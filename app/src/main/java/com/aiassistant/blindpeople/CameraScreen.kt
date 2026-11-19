package com.aiassistant.blindpeople

import android.graphics.Bitmap
import android.graphics.Matrix
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.aiassistant.blindpeople.distance.CalibratedDistanceProcessor
import com.aiassistant.blindpeople.distance.CalibrationSample
import com.aiassistant.blindpeople.distance.CameraCalibrationProfile
import com.aiassistant.blindpeople.distance.CameraConfiguration
import com.aiassistant.blindpeople.distance.DistanceCalibration
import com.aiassistant.blindpeople.distance.DistanceDetectionInput
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Session-only reference object for controlled
 * distance experiments.
 */
private data class DistanceDemoReference(
    val classId: Int,
    val label: String,
    val measuredHeightMeters: Float
)

@Composable
fun CameraScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Process one out of every four analyzer frames.
    val frameSamplingInterval = 4

    var detections by remember {
        mutableStateOf<List<YOLODetection>>(emptyList())
    }

    // Developer A: focus object selected from stabilized detections.
    var selectedDetection by remember {
        mutableStateOf<YOLODetection?>(null)
    }

    var imageWidth by remember {
        mutableIntStateOf(0)
    }

    var imageHeight by remember {
        mutableIntStateOf(0)
    }

    // Distance is initially unavailable.
    // No example calibration values are used.
    var distanceMetersByIndex by remember {
        mutableStateOf<Map<Int, Float?>>(emptyMap())
    }

    var currentRotationDegrees by remember {
        mutableIntStateOf(0)
    }

    var measuredHeightText by remember {
        mutableStateOf("")
    }

    var measuredDistanceText by remember {
        mutableStateOf("")
    }

    var calibrationMessage by remember {
        mutableStateOf(
            "Distance: Unknown (calibrate a measured reference object)"
        )
    }

    var demoReference by remember {
        mutableStateOf<DistanceDemoReference?>(null)
    }

    val distanceProcessor = remember {
        CalibratedDistanceProcessor()
    }

    val distanceLock = remember { Any() }

    // These camera identifiers are placeholders
    // for this experimental session only.
    // Do not save this profile permanently.
    fun activeCameraConfiguration(): CameraConfiguration {
        return CameraConfiguration(
            cameraId = "cameraX-default-back-session-only",
            lensId = "back-default-session-only",
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            rotationDegrees = currentRotationDegrees,
            zoomRatio = 1.0f
        )
    }

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

                                // Developer A: use the shared crop rectangle so
                                // the analysis bitmap matches the preview viewport.
                                Log.d(
                                    "CAMERA_CROP_CHECK",
                                    "Image size: ${imageProxy.width}x${imageProxy.height}, " +
                                            "CropRect: ${imageProxy.cropRect}, " +
                                            "Rotation: ${imageProxy.imageInfo.rotationDegrees}"
                                )

                                val fullBitmap = imageProxy.toBitmap()
                                val crop = imageProxy.cropRect
                                try {
                                    bitmap = Bitmap.createBitmap(
                                        fullBitmap,
                                        crop.left,
                                        crop.top,
                                        crop.width(),
                                        crop.height()
                                    )
                                } finally {
                                    if (bitmap !== fullBitmap) {
                                        fullBitmap.recycle()
                                    }
                                }

                                Log.d(
                                    "BITMAP_SIZE_CHECK",
                                    "Bitmap: ${bitmap.width}x${bitmap.height}, " +
                                            "CropRect: ${imageProxy.cropRect}"
                                )

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

                                // Step 1: YOLO11n inference.
                                val rawDetections =
                                    detector.detect(frame)

                                // Developer A: horizontal/vertical position
                                // and focus-point analysis for each detection.
                                val spatialAnalyzer = SpatialAnalyzer()
                                val focusPointManager = FocusPointManager()

                                for (detection in rawDetections) {
                                    val horizontal =
                                        spatialAnalyzer.getHorizontalPosition(
                                            detection = detection,
                                            imageWidth = frame.width
                                        )
                                    val vertical =
                                        spatialAnalyzer.getVerticalPosition(
                                            detection = detection,
                                            imageHeight = frame.height
                                        )
                                    val focusResult =
                                        focusPointManager.analyzeFocus(
                                            detection = detection,
                                            imageWidth = frame.width,
                                            imageHeight = frame.height
                                        )
                                    Log.d(
                                        "FOCUS_POINT_CHECK",
                                        "Object=${detection.label}, " +
                                                "Relation=${focusResult.relation}, " +
                                                "ContainsFocus=${focusResult.containsFocusPoint}, " +
                                                "Distance=${focusResult.normalizedDistance}"
                                    )
                                    Log.d(
                                        "SPATIAL_DIRECTION",
                                        "Object=${detection.label}, " +
                                                "X=${horizontal.normalizedCenterX}, " +
                                                "Y=${vertical.normalizedCenterY}, " +
                                                "Horizontal=${horizontal.zone}, " +
                                                "Vertical=${vertical.zone}"
                                    )
                                }

                                // Step 2: Stabilize bounding boxes.
                                val stabilizedDetections =
                                    stabilizer.update(rawDetections)

                                // Developer A: choose and display exactly one focus object.
                                val selectedFocus =
                                    focusPointManager.selectFocusObject(
                                        detections = stabilizedDetections,
                                        imageWidth = frame.width,
                                        imageHeight = frame.height
                                    )
                                selectedDetection = selectedFocus?.detection
                                if (selectedFocus != null) {
                                    Log.d(
                                        "FOCUS_SELECTION",
                                        "Selected=${selectedFocus.detection.label}, " +
                                                "Relation=${selectedFocus.relation}, " +
                                                "Confidence=${selectedFocus.detection.confidence}, " +
                                                "Distance=${selectedFocus.normalizedDistance}"
                                    )
                                } else {
                                    Log.d("FOCUS_SELECTION", "No object selected")
                                }

                                // Step 3: Update image information.
                                imageWidth = frame.width
                                imageHeight = frame.height
                                currentRotationDegrees =
                                    rotationDegrees

                                detections = stabilizedDetections

                                // Step 4: Experimental distance.
                                // Only one measured reference object
                                // of its class may receive a known
                                // physical height.
                                val reference = demoReference

                                val matchingCount =
                                    if (reference == null) {
                                        0
                                    } else {
                                        stabilizedDetections.count {
                                            it.classId ==
                                                    reference.classId
                                        }
                                    }

                                val inputs =
                                    stabilizedDetections.mapIndexed {
                                            index, detection ->

                                        val knownHeight =
                                            if (
                                                reference != null &&
                                                matchingCount == 1 &&
                                                detection.classId ==
                                                reference.classId
                                            ) {
                                                reference.measuredHeightMeters
                                            } else {
                                                null
                                            }

                                        DistanceDetectionInput(
                                            detectionIndex = index,
                                            objectName = detection.label,
                                            confidence =
                                                detection.confidence,
                                            left =
                                                detection.boundingBox.left,
                                            top =
                                                detection.boundingBox.top,
                                            right =
                                                detection.boundingBox.right,
                                            bottom =
                                                detection.boundingBox.bottom,
                                            realObjectHeightMeters =
                                                knownHeight
                                        )
                                    }

                                val configuration =
                                    activeCameraConfiguration()

                                val distanceResults =
                                    synchronized(distanceLock) {
                                        distanceProcessor.process(
                                            detections = inputs,
                                            currentConfiguration =
                                                configuration
                                        )
                                    }

                                distanceMetersByIndex =
                                    distanceResults.associate {
                                        it.detectionIndex to
                                                it.distance.estimatedMeters
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
                                debugInfo =
                                    detector.getDebugInfo()
                            }

                        } catch (e: Exception) {

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

                    // Developer A: preview and analyzer share one viewport.
                    // This is important for matching box geometry to preview.
                    val sharedViewPort =
                        previewView.viewPort
                            ?: throw IllegalStateException(
                                "Camera PreviewView viewport is not ready"
                            )

                    val useCaseGroup =
                        UseCaseGroup.Builder()
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
            detections = detections,
            selectedDetection = selectedDetection,
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            modifier = Modifier.fillMaxSize(),
            distanceMetersByIndex = distanceMetersByIndex
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
                text = "DISTANCE EXPERIMENT (measured object only)",
                color = Color.Yellow,
                fontSize = 11.sp
            )

            Text(
                text = "Aim at one object; measure its FULL height and " +
                        "distance from the camera. Keep the same object in view.",
                color = Color.White,
                fontSize = 10.sp,
                lineHeight = 12.sp
            )

            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(6.dp)
            ) {

                OutlinedTextField(
                    value = measuredHeightText,
                    onValueChange = {
                        measuredHeightText = it
                    },
                    label = {
                        Text(
                            "Height (m)",
                            fontSize = 10.sp
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal
                    ),
                    modifier = Modifier.weight(1f)
                )

                OutlinedTextField(
                    value = measuredDistanceText,
                    onValueChange = {
                        measuredDistanceText = it
                    },
                    label = {
                        Text(
                            "Distance (m)",
                            fontSize = 10.sp
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal
                    ),
                    modifier = Modifier.weight(1f)
                )
            }

            Button(
                onClick = {

                    val height =
                        measuredHeightText.toFloatOrNull()

                    val actualDistance =
                        measuredDistanceText.toFloatOrNull()

                    val topDetection =
                        detections.maxByOrNull {
                            it.confidence
                        }

                    val sameClassCount =
                        if (topDetection == null) {
                            0
                        } else {
                            detections.count {
                                it.classId ==
                                        topDetection.classId
                            }
                        }

                    if (
                        height == null ||
                        !height.isFinite() ||
                        height <= 0f ||
                        actualDistance == null ||
                        !actualDistance.isFinite() ||
                        actualDistance <= 0f
                    ) {
                        calibrationMessage =
                            "Enter positive measured values in meters"

                    } else if (
                        imageWidth <= 0 ||
                        imageHeight <= 0 ||
                        topDetection == null ||
                        sameClassCount != 1
                    ) {
                        calibrationMessage =
                            "Aim at ONE visible object of its class"

                    } else {

                        val sample = CalibrationSample(
                            actualDistanceMeters =
                                actualDistance,
                            realObjectHeightMeters =
                                height,
                            objectHeightPixels =
                                topDetection.boundingBox.height()
                        )

                        val calibration =
                            DistanceCalibration()
                                .calculateFocalLength(sample)

                        val focal =
                            calibration.focalLengthPixels

                        if (
                            !calibration.isValid ||
                            focal == null
                        ) {
                            calibrationMessage =
                                "Calibration failed: check full bounding box"

                        } else {

                            // Calibration is for this
                            // session only.
                            val profile =
                                CameraCalibrationProfile(
                                    configuration =
                                        activeCameraConfiguration(),
                                    focalLengthPixels = focal,
                                    calibrationSampleCount = 1
                                )

                            val registered =
                                synchronized(distanceLock) {
                                    distanceProcessor
                                        .registerCalibration(profile)
                                }

                            if (registered) {

                                demoReference =
                                    DistanceDemoReference(
                                        classId =
                                            topDetection.classId,
                                        label =
                                            topDetection.label,
                                        measuredHeightMeters =
                                            height
                                    )

                                calibrationMessage =
                                    "TEST ONLY: ${topDetection.label}; " +
                                            "keep the SAME measured object visible"

                            } else {
                                calibrationMessage =
                                    "Calibration registration failed"
                            }
                        }
                    }
                }
            ) {
                Text(
                    "Calibrate visible reference (TEST)"
                )
            }

            Text(
                text = calibrationMessage,
                color = Color.Yellow,
                fontSize = 10.sp,
                lineHeight = 12.sp
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
