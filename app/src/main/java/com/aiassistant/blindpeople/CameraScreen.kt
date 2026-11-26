package com.aiassistant.blindpeople

import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import android.util.Size
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.aiassistant.blindpeople.distance.AutomaticDistanceEstimator
import com.aiassistant.blindpeople.distance.AutomaticFocalLengthProvider
import com.aiassistant.blindpeople.distance.ProximityEstimator
import java.util.Locale
import java.util.concurrent.Executors

private data class MetricFrameResult(
    val detections: List<YOLODetection> = emptyList(),
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val distanceMetersByIndex: Map<Int, Float?> = emptyMap()
)

@Composable
fun CameraScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Process one out of every four analyzer frames.
    val frameSamplingInterval = 4

    var metricFrame by remember {
        mutableStateOf(MetricFrameResult())
    }

    val automaticDistanceEstimator = remember {
        AutomaticDistanceEstimator()
    }

    val proximityEstimator = remember { ProximityEstimator() }

    val detector = remember {
        YOLODetector(context.applicationContext)
    }

    val stabilizer = remember {
        DetectionStabilizer(
            retentionMs = 400L,
            smoothingFactor = 0.65f,
            minimumIoU = 0.20f
        )
    }

    var debugInfo by remember {
        mutableStateOf(detector.getDebugInfo())
    }

    var cameraStatus by remember {
        mutableStateOf("Initializing camera")
    }

    var stabilizedCount by remember {
        mutableIntStateOf(0)
    }

    var analyzedFrameCount by remember {
        mutableIntStateOf(0)
    }

    var processedFrameCount by remember {
        mutableIntStateOf(0)
    }

    val cameraExecutor = remember {
        Executors.newSingleThreadExecutor()
    }

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
        cameraExecutor
    ) {
        var isDisposed = false

        var frameCounter = 0
        var inferenceCounter = 0

        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({

            if (!isDisposed) {
                try {
                    val cameraProvider =
                        cameraProviderFuture.get()

                    val preview = Preview.Builder()
                        .build()
                        .also {
                            it.surfaceProvider =
                                previewView.surfaceProvider
                        }

                    val imageAnalysis =
                        ImageAnalysis.Builder()
                            .setTargetResolution(
                                Size(640, 480)
                            )
                            .setOutputImageFormat(
                                ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888
                            )
                            .setBackpressureStrategy(
                                ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                            )
                            .build()

                    cameraProvider.unbindAll()

                    val camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                    )

                    val focalProvider = try {
                        AutomaticFocalLengthProvider(camera.cameraInfo)
                    } catch (e: Exception) {
                        Log.e("FOCAL_LENGTH_TEST", "Provider initialization failed", e)
                        null
                    }
                    var lastGeometry: String? = null

                    imageAnalysis.setAnalyzer(
                        cameraExecutor
                    ) { imageProxy ->

                        var bitmap: Bitmap? = null
                        var rotatedBitmap: Bitmap? = null

                        try {
                            frameCounter++

                            // Skip three out of every four frames.
                            // Every ImageProxy is still closed.
                            if (
                                frameCounter % frameSamplingInterval != 0
                            ) {
                                return@setAnalyzer
                            }

                            inferenceCounter++

                            analyzedFrameCount = frameCounter
                            processedFrameCount = inferenceCounter

                            cameraStatus =
                                "Receiving camera frames"

                            if (detector.isModelLoaded()) {

                                bitmap = imageProxy.toBitmap()

                                val rotationDegrees =
                                    imageProxy.imageInfo.rotationDegrees

                                rotatedBitmap =
                                    if (rotationDegrees != 0) {

                                        val matrix = Matrix().apply {
                                            postRotate(
                                                rotationDegrees.toFloat()
                                            )
                                        }

                                        Bitmap.createBitmap(
                                            bitmap,
                                            0,
                                            0,
                                            bitmap.width,
                                            bitmap.height,
                                            matrix,
                                            true
                                        )

                                    } else {
                                        bitmap
                                    }

                                val frame = rotatedBitmap

                                // Read geometry for this exact analysis frame.
                                val focalLength = try {
                                    val focal = focalProvider?.estimate(imageProxy)
                                    val matrixValues = FloatArray(9)
                                    imageProxy.imageInfo.sensorToBufferTransformMatrix
                                        .getValues(matrixValues)
                                    val geometry = "buffer=${imageProxy.width}x${imageProxy.height} " +
                                            "bitmap=${frame.width}x${frame.height} " +
                                            "rotation=$rotationDegrees crop=${imageProxy.cropRect.toShortString()} " +
                                            "matrix=${matrixValues.contentToString()} " +
                                            (focal?.let {
                                                "camera=${it.cameraId} fx=${it.focalX} fy=${it.focalY} " +
                                                        "expected_bitmap=${it.imageWidth}x${it.imageHeight} source=${it.source} " +
                                                        "dimensions_match=${it.imageWidth == frame.width && it.imageHeight == frame.height}"
                                            } ?: "focal=UNAVAILABLE")
                                    if (geometry != lastGeometry) {
                                        Log.i("FOCAL_LENGTH_TEST", geometry)
                                        lastGeometry = geometry
                                    }
                                    // Conservatively reject non-full buffer crops.
                                    val crop = imageProxy.cropRect
                                    focal?.takeIf {
                                        crop.left == 0 && crop.top == 0 &&
                                                crop.right == imageProxy.width &&
                                                crop.bottom == imageProxy.height
                                    }
                                } catch (e: Exception) {
                                    Log.e("FOCAL_LENGTH_TEST", "Geometry diagnostic failed", e)
                                    null
                                }

                                // Step 1: YOLO11n inference.
                                val rawDetections =
                                    detector.detect(frame)

                                // Step 2: Stabilize bounding boxes.
                                val stabilizedDetections =
                                    stabilizer.update(rawDetections)

                                // Step 3: Calculate experimental metric estimates
                                // using only detections from this exact frame.
                                val distanceStart = SystemClock.elapsedRealtimeNanos()
                                val distanceResults = automaticDistanceEstimator.estimate(
                                    detections = rawDetections,
                                    focalLength = focalLength,
                                    imageWidth = frame.width,
                                    imageHeight = frame.height
                                )
                                val distanceMap = distanceResults.associate {
                                    it.detectionIndex to it.estimatedMeters
                                }
                                val distanceElapsedNs =
                                    SystemClock.elapsedRealtimeNanos() - distanceStart

                                // Publish one immutable frame result. Do not mix
                                // retained/stabilized boxes with current raw indexes.
                                metricFrame = MetricFrameResult(
                                    detections = rawDetections.toList(),
                                    imageWidth = frame.width,
                                    imageHeight = frame.height,
                                    distanceMetersByIndex = distanceMap
                                )

                                Log.i(
                                    "DISTANCE_BENCHMARK",
                                    String.format(
                                        Locale.US,
                                        "frame=%d objects=%d estimates=%d elapsed_ns=%d elapsed_ms=%.4f",
                                        inferenceCounter, distanceResults.size,
                                        distanceResults.count { it.estimatedMeters != null },
                                        distanceElapsedNs, distanceElapsedNs / 1_000_000.0
                                    )
                                )

                                for (result in distanceResults) {
                                    Log.i(
                                        "DISTANCE_TEST",
                                        "frame=$inferenceCounter object=${result.objectName} " +
                                                "index=${result.detectionIndex} " +
                                                "estimated_m=${result.estimatedMeters ?: "UNKNOWN"} " +
                                                "reference_m=${result.referenceSizeMeters} " +
                                                "axis=${result.referenceDimension} " +
                                                "box_px=${result.boundingBoxPixels} " +
                                                "focal_px=${result.focalLengthPixels} " +
                                                "status=${result.status}"
                                    )
                                }

                                // Preserve the original raw proximity benchmark.
                                val proximityStart = SystemClock.elapsedRealtimeNanos()
                                val proximityResults = proximityEstimator.estimate(
                                    detections = rawDetections,
                                    imageWidth = frame.width,
                                    imageHeight = frame.height
                                )
                                val proximityElapsedNs =
                                    SystemClock.elapsedRealtimeNanos() - proximityStart

                                Log.i(
                                    "PROXIMITY_BENCHMARK",
                                    String.format(
                                        Locale.US,
                                        "frame=%d objects=%d elapsed_ns=%d elapsed_ms=%.4f",
                                        inferenceCounter, proximityResults.size,
                                        proximityElapsedNs, proximityElapsedNs / 1_000_000.0
                                    )
                                )
                                for (result in proximityResults) {
                                    Log.i(
                                        "PROXIMITY_TEST",
                                        String.format(
                                            Locale.US,
                                            "frame=%d object=%s index=%d confidence=%.2f area=%.2f%% experimental_proximity=%s",
                                            inferenceCounter, result.objectName,
                                            result.detectionIndex, result.confidence,
                                            result.areaRatio * 100f, result.proximityLevel.name
                                        )
                                    )
                                }

                                debugInfo =
                                    detector.getDebugInfo()

                                stabilizedCount =
                                    stabilizedDetections.size

                                // Log detected objects.
                                for (detection in rawDetections) {

                                    val box =
                                        detection.boundingBox

                                    val message = String.format(
                                        Locale.US,
                                        "Detected: %s, confidence=%.2f, " +
                                                "x=%d, y=%d, " +
                                                "left=%d, top=%d, " +
                                                "right=%d, bottom=%d",
                                        detection.label,
                                        detection.confidence,
                                        box.centerX().toInt(),
                                        box.centerY().toInt(),
                                        box.left.toInt(),
                                        box.top.toInt(),
                                        box.right.toInt(),
                                        box.bottom.toInt()
                                    )

                                    Log.d(
                                        "YOLODetection",
                                        message
                                    )
                                }

                                Log.d(
                                    "YOLOFrameSampling",
                                    "Analyzer frames=$frameCounter, " +
                                            "processed=$inferenceCounter, " +
                                            "interval=$frameSamplingInterval"
                                )

                            } else {
                                metricFrame = MetricFrameResult()
                                debugInfo =
                                    detector.getDebugInfo()
                            }

                        } catch (e: Exception) {
                            metricFrame = MetricFrameResult()

                            val message =
                                "${e.javaClass.simpleName}: " +
                                        (e.message ?: "Unknown error")

                            cameraStatus =
                                "Frame processing failed"

                            debugInfo =
                                detector.getDebugInfo().copy(
                                    inferenceStatus =
                                        "Camera frame error",
                                    errorMessage = message
                                )

                            Log.e(
                                "CameraScreen",
                                "Frame analysis failed",
                                e
                            )

                        } finally {

                            if (
                                rotatedBitmap != null &&
                                rotatedBitmap !== bitmap
                            ) {
                                rotatedBitmap.recycle()
                            }

                            bitmap?.recycle()
                            imageProxy.close()
                        }
                    }

                    cameraStatus = "Camera started"

                    Log.d(
                        "CameraScreen",
                        "Camera, YOLO11n, frame sampling " +
                                "and stabilizer started"
                    )

                } catch (e: Exception) {

                    cameraStatus =
                        "Camera initialization failed"

                    debugInfo =
                        detector.getDebugInfo().copy(
                            errorMessage =
                                "${e.javaClass.simpleName}: " +
                                        (e.message ?: "Unknown error")
                        )

                    Log.e(
                        "CameraScreen",
                        "Camera initialization failed",
                        e
                    )
                }
            }

        }, ContextCompat.getMainExecutor(context))

        onDispose {
            isDisposed = true

            if (cameraProviderFuture.isDone) {
                try {
                    cameraProviderFuture.get().unbindAll()
                } catch (e: Exception) {
                    Log.e(
                        "CameraScreen",
                        "Camera cleanup failed",
                        e
                    )
                }
            }

            cameraExecutor.execute {
                stabilizer.reset()
                detector.close()
            }

            cameraExecutor.shutdown()
        }
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {

        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        DetectionOverlay(
            detections = metricFrame.detections,
            imageWidth = metricFrame.imageWidth,
            imageHeight = metricFrame.imageHeight,
            modifier = Modifier.fillMaxSize(),
            distanceMetersByIndex = metricFrame.distanceMetersByIndex
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(8.dp)
                .background(
                    color = Color.Black.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(10.dp)
                )
                .heightIn(max = 480.dp)
                .verticalScroll(rememberScrollState())
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {

            Text(
                text = "YOLO11n FRAME SAMPLING DEBUG",
                color = Color.Yellow,
                fontSize = 15.sp
            )

            Text(
                text = "TEST ONLY: Unvalidated size-based estimates. Not for navigation. Raw same-frame boxes.",
                color = Color.White,
                fontSize = 11.sp
            )

            DebugLine(
                "Camera",
                cameraStatus
            )

            DebugLine(
                "Model",
                debugInfo.modelStatus
            )

            DebugLine(
                "Inference",
                debugInfo.inferenceStatus
            )

            DebugLine(
                "Sampling interval",
                "Every $frameSamplingInterval frames"
            )

            DebugLine(
                "Analyzer frames",
                analyzedFrameCount.toString()
            )

            DebugLine(
                "Processed frames",
                processedFrameCount.toString()
            )

            DebugLine(
                "Raw YOLO objects",
                debugInfo.detectionCount.toString()
            )

            DebugLine(
                "Stabilized objects",
                stabilizedCount.toString()
            )

            DebugLine(
                "Max confidence",
                "${(debugInfo.maximumConfidence * 100).toInt()}%"
            )

            DebugLine(
                "Processing",
                "${debugInfo.inferenceTimeMs} ms"
            )

            DebugLine(
                "Candidates >= 45%",
                debugInfo.candidatesAboveThreshold.toString()
            )

            DebugLine(
                "Invalid boxes",
                debugInfo.invalidBoxes.toString()
            )

            DebugLine(
                "Valid before NMS",
                debugInfo.validBoxesBeforeNMS.toString()
            )

            DebugLine(
                "Best class",
                debugInfo.bestClass
            )

            DebugLine(
                "Raw box",
                debugInfo.rawBox
            )

            if (debugInfo.errorMessage.isNotBlank()) {
                Text(
                    text = "ERROR: ${debugInfo.errorMessage}",
                    color = Color.Red,
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

@Composable
private fun DebugLine(
    label: String,
    value: String
) {
    Text(
        text = "$label: $value",
        color = Color.White,
        fontSize = 11.sp,
        lineHeight = 14.sp
    )
}
