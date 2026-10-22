
package com.aiassistant.blindpeople

import android.graphics.Bitmap
import android.graphics.Matrix
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
import androidx.compose.foundation.layout.padding
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
import java.util.concurrent.Executors

@Composable
fun CameraScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Detections displayed by the overlay.
    var detections by remember {
        mutableStateOf<List<YOLODetection>>(emptyList())
    }

    var imageWidth by remember {
        mutableIntStateOf(0)
    }

    var imageHeight by remember {
        mutableIntStateOf(0)
    }

    val detector = remember {
        YOLODetector(context.applicationContext)
    }

    // Stabilizes detections between inference results.
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

                                // Step 1: Raw YOLO11n predictions.
                                val rawDetections =
                                    detector.detect(frame)

                                // Step 2: Stabilize bounding boxes.
                                val stabilizedDetections =
                                    stabilizer.update(rawDetections)

                                // Step 3: Update visual overlay.
                                imageWidth = frame.width
                                imageHeight = frame.height
                                detections = stabilizedDetections

                                // Keep original model diagnostics.
                                debugInfo = detector.getDebugInfo()

                                stabilizedCount =
                                    stabilizedDetections.size

                                if (rawDetections.isNotEmpty()) {
                                    Log.d(
                                        "YOLODetection",
                                        rawDetections.joinToString {
                                            "${it.label}: " +
                                                    "${(it.confidence * 100).toInt()}%"
                                        }
                                    )
                                }

                            } else {
                                debugInfo = detector.getDebugInfo()
                            }

                        } catch (e: Exception) {

                            val message =
                                "${e.javaClass.simpleName}: " +
                                        (e.message ?: "Unknown error")

                            cameraStatus =
                                "Frame processing failed"

                            debugInfo = detector.getDebugInfo().copy(
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

                    cameraProvider.unbindAll()

                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                    )

                    cameraStatus = "Camera started"

                    Log.d(
                        "CameraScreen",
                        "Camera, YOLO11n and stabilizer started"
                    )

                } catch (e: Exception) {

                    cameraStatus =
                        "Camera initialization failed"

                    debugInfo = detector.getDebugInfo().copy(
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

            // Clean up on the same thread used for inference.
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
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            modifier = Modifier.fillMaxSize()
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
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {

            Text(
                text = "YOLO11n STABILIZATION DEBUG",
                color = Color.Yellow,
                fontSize = 15.sp
            )

            DebugLine("Camera", cameraStatus)
            DebugLine("Model", debugInfo.modelStatus)
            DebugLine("Inference", debugInfo.inferenceStatus)

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
