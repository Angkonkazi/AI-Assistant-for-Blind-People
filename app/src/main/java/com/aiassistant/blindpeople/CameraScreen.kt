package com.aiassistant.blindpeople

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.Canvas as BitmapCanvas
import android.graphics.Paint
import android.graphics.Color as BitmapColor
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.util.Size
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.TextButton
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.aiassistant.blindpeople.distance.AutomaticObjectDistanceEstimator
import com.aiassistant.blindpeople.distance.DepthAnythingOnnxRunner
import java.util.Locale
import kotlinx.coroutines.delay
import com.aiassistant.blindpeople.distance.AutomaticObjectDistance
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Experimental automatic object distance estimation.
 *
 * NO height input, distance input or manual calibration.
 *
 * Depth inference is deliberately throttled and uses a separate thread.
 * Its results describe the CAPTURED frame, NOT necessarily the current view.
 * This is diagnostic output only: do not use it for navigation decisions.
 */
@Composable
fun CameraScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var debugExpanded by rememberSaveable { mutableStateOf(false) }
    var showSnapshot by rememberSaveable { mutableStateOf(false) }
    var completedSnapshot by remember { mutableStateOf<CompletedDepthSnapshot?>(null) }
    var snapshotAgeSeconds by remember { mutableStateOf<Long?>(null) }

    // Age is measured from analyzer entry, not a claim of live distance.
    LaunchedEffect(completedSnapshot) {
        val snapshot = completedSnapshot
        snapshotAgeSeconds = null
        if (snapshot != null) {
            while (true) {
                snapshotAgeSeconds = (SystemClock.elapsedRealtime() - snapshot.capturedAtMs) / 1000L
                delay(1000L)
            }
        }
    }

    // Preserve the existing YOLO frame sampling policy.
    val frameSamplingInterval = 4
    // The observed Android CPU inference was ~10 seconds on the first test.
    // Avoid building an inference queue or blocking CameraX.
    val depthIntervalMs = 20_000L

    var detections by remember { mutableStateOf<List<YOLODetection>>(emptyList()) }
    var selectedDetection by remember { mutableStateOf<YOLODetection?>(null) }
    var imageWidth by remember { mutableIntStateOf(0) }
    var imageHeight by remember { mutableIntStateOf(0) }
    var cameraStatus by remember { mutableStateOf("Initializing camera") }
    var analyzedFrameCount by remember { mutableIntStateOf(0) }
    var processedFrameCount by remember { mutableIntStateOf(0) }
    var stabilizedCount by remember { mutableIntStateOf(0) }

    // Diagnostic-only results. Do not place old distances on live detections:
    // a prediction may return many seconds after the frame was captured.
    var depthStatus by remember { mutableStateOf("Depth: waiting for a camera frame") }
    var depthObjectSummary by remember { mutableStateOf("No automatic object estimates yet") }
    var lastDepthTimeMs by remember { mutableStateOf<Long?>(null) }
    var lastDepthFrameAgeSeconds by remember { mutableStateOf<Long?>(null) }

    // Always empty until we have same-frame synchronized results for overlay.
    // This prevents stale distances from being attached to new YOLO objects.
    val overlayDistanceMetersByIndex = emptyMap<Int, Float?>()

    val detector = remember { YOLODetector(context.applicationContext) }
    val stabilizer = remember {
        DetectionStabilizer(
            retentionMs = 400L,
            smoothingFactor = 0.65f,
            minimumIoU = 0.20f
        )
    }
    val distanceEstimator = remember { AutomaticObjectDistanceEstimator() }
    var debugInfo by remember { mutableStateOf(detector.getDebugInfo()) }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val depthExecutor = remember { Executors.newSingleThreadExecutor() }

    val previewView = remember(context) {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    DisposableEffect(
        lifecycleOwner,
        previewView,
        detector,
        stabilizer,
        distanceEstimator,
        cameraExecutor,
        depthExecutor
    ) {
        val disposed = AtomicBoolean(false)
        val depthBusy = AtomicBoolean(false)
        var frameCounter = 0
        var processedCounter = 0
        var lastDepthScheduledAt = SystemClock.elapsedRealtime() - depthIntervalMs

        // Owned and closed on depthExecutor, never on the camera thread.
        var depthRunner: DepthAnythingOnnxRunner? = null
        val providerFuture = ProcessCameraProvider.getInstance(context)

        fun publishDepthStatus(status: String) {
            mainHandler.post {
                if (!disposed.get()) depthStatus = status
            }
        }

        providerFuture.addListener({
            if (!disposed.get()) {
                try {
                    val cameraProvider = providerFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setTargetResolution(Size(640, 480))
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        var croppedBitmap: Bitmap? = null
                        var uprightBitmap: Bitmap? = null
                        try {
                            val frameReceivedAt = SystemClock.elapsedRealtime()
                            frameCounter++
                            if (frameCounter % frameSamplingInterval != 0) {
                                return@setAnalyzer
                            }
                            processedCounter++
                            analyzedFrameCount = frameCounter
                            processedFrameCount = processedCounter
                            cameraStatus = "Receiving camera frames"

                            if (!detector.isModelLoaded()) {
                                debugInfo = detector.getDebugInfo()
                                return@setAnalyzer
                            }

                            // Keep the existing shared crop and orientation handling.
                            Log.d(
                                "CAMERA_CROP_CHECK",
                                "Image=${imageProxy.width}x${imageProxy.height}, " +
                                        "Crop=${imageProxy.cropRect}, " +
                                        "Rotation=${imageProxy.imageInfo.rotationDegrees}"
                            )
                            val fullBitmap = imageProxy.toBitmap()
                            val crop = imageProxy.cropRect
                            try {
                                croppedBitmap = Bitmap.createBitmap(
                                    fullBitmap,
                                    crop.left,
                                    crop.top,
                                    crop.width(),
                                    crop.height()
                                )
                            } finally {
                                if (croppedBitmap !== fullBitmap) fullBitmap.recycle()
                            }

                            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                            uprightBitmap = if (rotationDegrees != 0) {
                                val matrix = Matrix().apply {
                                    postRotate(rotationDegrees.toFloat())
                                }
                                Bitmap.createBitmap(
                                    croppedBitmap, 0, 0,
                                    croppedBitmap.width, croppedBitmap.height,
                                    matrix, true
                                )
                            } else {
                                croppedBitmap
                            }
                            val frame = uprightBitmap

                            // Unchanged YOLO and spatial/focus algorithm.
                            val rawDetections = detector.detect(frame)
                            val spatialAnalyzer = SpatialAnalyzer()
                            val focusPointManager = FocusPointManager()
                            for (detection in rawDetections) {
                                val horizontal = spatialAnalyzer.getHorizontalPosition(
                                    detection = detection, imageWidth = frame.width
                                )
                                val vertical = spatialAnalyzer.getVerticalPosition(
                                    detection = detection, imageHeight = frame.height
                                )
                                val focus = focusPointManager.analyzeFocus(
                                    detection = detection,
                                    imageWidth = frame.width,
                                    imageHeight = frame.height
                                )
                                Log.d(
                                    "SPATIAL_DIRECTION",
                                    "Object=${detection.label}, " +
                                            "Horizontal=${horizontal.zone}, Vertical=${vertical.zone}"
                                )
                                Log.d(
                                    "FOCUS_POINT_CHECK",
                                    "Object=${detection.label}, " +
                                            "Relation=${focus.relation}, " +
                                            "ContainsFocus=${focus.containsFocusPoint}, " +
                                            "Distance=${focus.normalizedDistance}"
                                )
                            }
                            val stableDetections = stabilizer.update(rawDetections)
                            val focused = focusPointManager.selectFocusObject(
                                detections = stableDetections,
                                imageWidth = frame.width,
                                imageHeight = frame.height
                            )
                            selectedDetection = focused?.detection
                            if (focused != null) {
                                Log.d(
                                    "FOCUS_SELECTION",
                                    "Selected=${focused.detection.label}, " +
                                            "Relation=${focused.relation}, " +
                                            "Confidence=${focused.detection.confidence}, " +
                                            "Distance=${focused.normalizedDistance}"
                                )
                            }
                            imageWidth = frame.width
                            imageHeight = frame.height
                            detections = stableDetections
                            stabilizedCount = stableDetections.size
                            debugInfo = detector.getDebugInfo()

                            for (detection in rawDetections) {
                                val box = detection.boundingBox
                                Log.d(
                                    "YOLODetection",
                                    String.format(
                                        Locale.US,
                                        "Detected: %s, confidence=%.2f, " +
                                                "left=%d, top=%d, right=%d, bottom=%d",
                                        detection.label, detection.confidence,
                                        box.left.toInt(), box.top.toInt(),
                                        box.right.toInt(), box.bottom.toInt()
                                    )
                                )
                            }
                            Log.d(
                                "YOLOFrameSampling",
                                "Analyzer frames=$frameCounter, processed=$processedCounter, " +
                                        "interval=$frameSamplingInterval"
                            )

                            // Capture a snapshot for depth INDEPENDENT of subsequent frames.
                            val now = SystemClock.elapsedRealtime()
                            if (rawDetections.isEmpty() && !depthBusy.get() &&
                                now - lastDepthScheduledAt >= depthIntervalMs) {
                                publishDepthStatus("Depth: waiting for a detected object")
                            }
                            if (
                                rawDetections.isNotEmpty() &&
                                !depthBusy.get() &&
                                now - lastDepthScheduledAt >= depthIntervalMs &&
                                depthBusy.compareAndSet(false, true)
                            ) {
                                lastDepthScheduledAt = now
                                val snapshot = try {
                                    requireNotNull(frame.copy(Bitmap.Config.ARGB_8888, false))
                                } catch (e: Exception) {
                                    depthBusy.set(false)
                                    throw e
                                }
                                // Raw detections belong to this exact image. Stabilized
                                // boxes can contain geometry retained from older frames.
                                val snapshotDetections = rawDetections.map {
                                    it.copy(boundingBox = RectF(it.boundingBox))
                                }
                                val snapshotWidth = frame.width
                                val snapshotHeight = frame.height
                                val captureAt = frameReceivedAt
                                publishDepthStatus("Depth: processing captured frame...")

                                try {
                                    depthExecutor.execute {
                                        try {
                                            if (disposed.get()) return@execute
                                            val runner = depthRunner
                                                ?: DepthAnythingOnnxRunner(
                                                    context.applicationContext
                                                ).also { depthRunner = it }
                                            val start = SystemClock.elapsedRealtime()
                                            val map = runner.estimateDepth(snapshot)
                                            val elapsed = SystemClock.elapsedRealtime() - start
                                            val results = distanceEstimator.estimate(
                                                detections = snapshotDetections,
                                                depthMap = map,
                                                cameraWidth = snapshotWidth,
                                                cameraHeight = snapshotHeight
                                            )
                                            val summary = if (results.isEmpty()) {
                                                "No YOLO objects in captured frame"
                                            } else {
                                                results.take(5).joinToString("\n") { item ->
                                                    val distance = formatMetricDistance(item.estimatedMeters)
                                                    if (distance == null) {
                                                        "${item.label}: unavailable"
                                                    } else {
                                                        "${item.label}: ~$distance (${item.reliability.name.lowercase(Locale.US)})"
                                                    }
                                                }
                                            }
                                            // Annotate a separate bitmap for developer review.
                                            // Never attach these values to the live preview.
                                            val annotated = annotateDepthSnapshot(snapshot, snapshotDetections, results)
                                            val completed = CompletedDepthSnapshot(annotated, captureAt)
                                            Log.i(
                                                "DEPTH_CAMERA_TEST",
                                                "Snapshot ${snapshotWidth}x${snapshotHeight}; " +
                                                        "inference=${elapsed}ms; objects=${results.size}; " +
                                                        "estimates=$summary"
                                            )
                                            mainHandler.post {
                                                if (!disposed.get()) {
                                                    depthStatus = "Depth: inference succeeded"
                                                    completedSnapshot = completed
                                                    depthObjectSummary = summary
                                                    lastDepthTimeMs = elapsed
                                                    lastDepthFrameAgeSeconds =
                                                        (SystemClock.elapsedRealtime() - captureAt) / 1000L
                                                }
                                            }
                                        } catch (e: Exception) {
                                            Log.e("DEPTH_CAMERA_TEST", "Depth inference failed", e)
                                            publishDepthStatus(
                                                "Depth: failed (${e.javaClass.simpleName})"
                                            )
                                        } finally {
                                            snapshot.recycle()
                                            depthBusy.set(false)
                                        }
                                    }
                                } catch (e: Exception) {
                                    snapshot.recycle()
                                    depthBusy.set(false)
                                    throw e
                                }
                            }
                        } catch (e: Exception) {
                            cameraStatus = "Frame processing failed"
                            val message = "${e.javaClass.simpleName}: ${e.message ?: "Unknown error"}"
                            debugInfo = detector.getDebugInfo().copy(
                                inferenceStatus = "Camera frame error",
                                errorMessage = message
                            )
                            Log.e("CameraScreen", "Frame analysis failed", e)
                        } finally {
                            if (uprightBitmap != null && uprightBitmap !== croppedBitmap) {
                                uprightBitmap.recycle()
                            }
                            croppedBitmap?.recycle()
                            imageProxy.close()
                        }
                    }

                    // Preserve the original PreviewView viewport alignment.
                    val sharedViewPort = previewView.viewPort
                        ?: throw IllegalStateException(
                            "Camera PreviewView viewport is not ready"
                        )
                    val useCaseGroup = UseCaseGroup.Builder()
                        .setViewPort(sharedViewPort)
                        .addUseCase(preview)
                        .addUseCase(imageAnalysis)
                        .build()
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        useCaseGroup
                    )
                    cameraStatus = "Camera started"
                    Log.d(
                        "CameraScreen",
                        "Camera, YOLO11n, spatial logic and automatic depth started"
                    )
                } catch (e: Exception) {
                    cameraStatus = "Camera initialization failed"
                    debugInfo = detector.getDebugInfo().copy(
                        errorMessage = "${e.javaClass.simpleName}: ${e.message ?: "Unknown error"}"
                    )
                    Log.e("CameraScreen", "Camera initialization failed", e)
                }
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            disposed.set(true)
            if (providerFuture.isDone) {
                try {
                    providerFuture.get().unbindAll()
                } catch (e: Exception) {
                    Log.e("CameraScreen", "Camera cleanup failed", e)
                }
            }
            cameraExecutor.execute {
                stabilizer.reset()
                detector.close()
            }
            cameraExecutor.shutdown()
            // Close ONNX runner on its owner thread, after any pending inference.
            depthExecutor.execute {
                try {
                    depthRunner?.close()
                    depthRunner = null
                } catch (e: Exception) {
                    Log.e("DEPTH_CAMERA_TEST", "Runner cleanup failed", e)
                }
            }
            depthExecutor.shutdown()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )
        DetectionOverlay(
            detections = detections,
            selectedDetection = selectedDetection,
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            modifier = Modifier.fillMaxSize(),
            distanceMetersByIndex = overlayDistanceMetersByIndex
        )
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(8.dp)
                .background(
                    color = Color.Black.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(10.dp)
                )
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "EXPERIMENTAL DEPTH",
                    color = Color.Yellow,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { debugExpanded = !debugExpanded }) {
                    Text(
                        text = if (debugExpanded) "Hide details" else "Show details",
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }
            }
            DebugLine("Depth", depthStatus.removePrefix("Depth: "))
            DebugLine(
                "Last inference / YOLO objects",
                "${lastDepthTimeMs?.let { "$it ms" } ?: "Pending"} / $stabilizedCount"
            )
            Text(
                text = "Delayed snapshot estimates — not for navigation.",
                color = Color.Yellow,
                fontSize = 11.sp,
                lineHeight = 14.sp
            )
            // Show numerical results without requiring the debug panel to be expanded.
            Text(
                text = if (completedSnapshot == null) "Waiting for first object-depth result"
                else "Last captured frame (${snapshotAgeSeconds?.let { "~$it s old" } ?: "age pending"})",
                color = Color.Yellow,
                fontSize = 11.sp
            )
            Text(
                text = depthObjectSummary,
                color = Color.White,
                fontSize = 12.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            if (completedSnapshot != null) {
                TextButton(onClick = { showSnapshot = !showSnapshot }) {
                    Text(if (showSnapshot) "Hide captured frame" else "Show captured frame",
                        color = Color.White, fontSize = 12.sp)
                }
            }
            if (showSnapshot) {
                completedSnapshot?.let { snapshot ->
                    Image(
                        bitmap = remember(snapshot) { snapshot.bitmap.asImageBitmap() },
                        contentDescription = "Past captured frame with experimental depth estimates, not live guidance",
                        modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            }
            if (debugExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "No manual calibration. Estimates refer to the captured frame, not the current view.",
                        color = Color.White,
                        fontSize = 11.sp
                    )
                    DebugLine("Depth status", depthStatus)
                    DebugLine("Snapshot inference", lastDepthTimeMs?.let { "$it ms" } ?: "Pending")
                    DebugLine(
                        "Snapshot age when ready",
                        lastDepthFrameAgeSeconds?.let { "$it s" } ?: "Pending"
                    )
                    DebugLine("Captured-frame object estimates", depthObjectSummary)
                    DebugLine("Camera", cameraStatus)
                    DebugLine("YOLO model", debugInfo.modelStatus)
                    DebugLine("YOLO inference", debugInfo.inferenceStatus)
                    DebugLine("Sampling interval", "Every $frameSamplingInterval frames")
                    DebugLine("Analyzer frames", analyzedFrameCount.toString())
                    DebugLine("Processed frames", processedFrameCount.toString())
                    DebugLine("Raw YOLO objects", debugInfo.detectionCount.toString())
                    DebugLine("Stabilized objects", stabilizedCount.toString())
                    DebugLine("Max confidence", "${(debugInfo.maximumConfidence * 100).toInt()}%")
                    DebugLine("YOLO processing", "${debugInfo.inferenceTimeMs} ms")
                    DebugLine("Candidates >= 45%", debugInfo.candidatesAboveThreshold.toString())
                    DebugLine("Invalid boxes", debugInfo.invalidBoxes.toString())
                    DebugLine("Valid before NMS", debugInfo.validBoxesBeforeNMS.toString())
                    DebugLine("Best class", debugInfo.bestClass)
                    DebugLine("Raw box", debugInfo.rawBox)
                    if (debugInfo.errorMessage.isNotBlank()) {
                        Text(
                            text = "ERROR: ${debugInfo.errorMessage}",
                            color = Color.Red,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DebugLine(label: String, value: String) {
    Text(
        text = "$label: $value",
        color = Color.White,
        fontSize = 11.sp,
        lineHeight = 14.sp
    )
}



/** Immutable-in-use diagnostic image; released by GC when no longer displayed. */
private data class CompletedDepthSnapshot(
    val bitmap: Bitmap,
    val capturedAtMs: Long
)

/** Draw only estimates paired with detections from the very same snapshot. */
private fun annotateDepthSnapshot(
    source: Bitmap,
    detections: List<YOLODetection>,
    results: List<AutomaticObjectDistance>
): Bitmap {
    val image = requireNotNull(source.copy(Bitmap.Config.ARGB_8888, true))
    val canvas = BitmapCanvas(image)
    val textSize = (image.width * 0.04f).coerceIn(12f, 28f)
    val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = BitmapColor.YELLOW
        style = Paint.Style.STROKE
        strokeWidth = (image.width / 180f).coerceAtLeast(2f)
    }
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = BitmapColor.WHITE
        this.textSize = textSize
    }
    val background = Paint().apply { color = BitmapColor.BLACK; alpha = 210 }
    detections.zip(results).forEach { (detection, result) ->
        val box = detection.boundingBox
        canvas.drawRect(box, outline)
        val label = formatMetricDistance(result.estimatedMeters)?.let {
            "${detection.label} ~$it"
        } ?: "${detection.label}: unavailable"
        val padding = 4f
        val labelWidth = (text.measureText(label) + padding * 2).coerceAtMost(image.width.toFloat())
        val left = box.left.coerceIn(0f, image.width - labelWidth)
        val height = textSize + padding * 2
        val top = (box.top - height).coerceIn(0f, (image.height - height).coerceAtLeast(0f))
        canvas.drawRect(left, top, left + labelWidth, top + height, background)
        canvas.drawText(label, left + padding, top + padding + textSize * 0.82f, text)
    }
    return image
}


/** Display only; the model and fusion continue to use meters internally. */
private fun formatMetricDistance(meters: Float?): String? {
    if (meters == null || !meters.isFinite() || meters <= 0f) return null
    return if (meters < 1f) {
        String.format(Locale.US, "%.0f cm", meters * 100f)
    } else {
        String.format(Locale.US, "%.2f m", meters)
    }
}
