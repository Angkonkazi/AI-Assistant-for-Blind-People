
package com.aiassistant.blindpeople

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

data class YOLODetection(
    val classId: Int,
    val label: String,
    val confidence: Float,
    val boundingBox: RectF
)

data class YOLODebugInfo(
    val modelStatus: String = "Not initialized",
    val inferenceStatus: String = "Not started",
    val detectionCount: Int = 0,
    val maximumConfidence: Float = 0f,
    val inferenceTimeMs: Long = 0L,
    val errorMessage: String = "",
    val candidatesAboveThreshold: Int = 0,
    val invalidBoxes: Int = 0,
    val validBoxesBeforeNMS: Int = 0,
    val bestClass: String = "None",
    val rawBox: String = "None"
)

class YOLODetector(
    private val context: Context
) : Closeable {

    companion object {
        private const val TAG = "YOLODetector"
        private const val MODEL_NAME = "yolo11n.tflite"

        private const val INPUT_SIZE = 640
        private const val NUM_CLASSES = 80
        private const val NUM_PREDICTIONS = 8400
        private const val OUTPUT_CHANNELS = 84

        private const val CONFIDENCE_THRESHOLD = 0.45f
        private const val IOU_THRESHOLD = 0.50f
        private const val MAX_DETECTIONS = 30

        // Our exported model appears to output
        // normalized coordinates in the range 0..1.
        private const val BOX_COORDINATE_SCALE = 640f

        private val COCO_LABELS = listOf(
            "person", "bicycle", "car", "motorcycle",
            "airplane", "bus", "train", "truck", "boat",
            "traffic light", "fire hydrant", "stop sign",
            "parking meter", "bench", "bird", "cat",
            "dog", "horse", "sheep", "cow", "elephant",
            "bear", "zebra", "giraffe", "backpack",
            "umbrella", "handbag", "tie", "suitcase",
            "frisbee", "skis", "snowboard", "sports ball",
            "kite", "baseball bat", "baseball glove",
            "skateboard", "surfboard", "tennis racket",
            "bottle", "wine glass", "cup", "fork", "knife",
            "spoon", "bowl", "banana", "apple", "sandwich",
            "orange", "broccoli", "carrot", "hot dog",
            "pizza", "donut", "cake", "chair", "couch",
            "potted plant", "bed", "dining table",
            "toilet", "tv", "laptop", "mouse", "remote",
            "keyboard", "cell phone", "microwave",
            "oven", "toaster", "sink", "refrigerator",
            "book", "clock", "vase", "scissors",
            "teddy bear", "hair drier", "toothbrush"
        )
    }

    private var interpreter: Interpreter? = null

    @Volatile
    private var debugInfo = YOLODebugInfo()

    fun getDebugInfo(): YOLODebugInfo = debugInfo

    init {
        loadModel()
    }

    private fun loadModel() {
        try {
            debugInfo = debugInfo.copy(
                modelStatus = "Loading",
                errorMessage = ""
            )

            val modelBuffer =
                context.assets.openFd(MODEL_NAME).use { descriptor ->
                    descriptor.createInputStream().use { inputStream ->
                        inputStream.channel.map(
                            FileChannel.MapMode.READ_ONLY,
                            descriptor.startOffset,
                            descriptor.declaredLength
                        )
                    }
                }

            val options = Interpreter.Options().apply {
                setNumThreads(2)
            }

            interpreter = Interpreter(modelBuffer, options)

            val inputShape =
                interpreter!!.getInputTensor(0).shape()

            val outputShape =
                interpreter!!.getOutputTensor(0).shape()

            require(
                inputShape.contentEquals(
                    intArrayOf(1, 3, INPUT_SIZE, INPUT_SIZE)
                )
            ) {
                "Unexpected input shape: ${inputShape.contentToString()}"
            }

            require(
                outputShape.contentEquals(
                    intArrayOf(1, OUTPUT_CHANNELS, NUM_PREDICTIONS)
                )
            ) {
                "Unexpected output shape: ${outputShape.contentToString()}"
            }

            debugInfo = debugInfo.copy(
                modelStatus = "Loaded",
                inferenceStatus = "Waiting for camera frames",
                errorMessage = ""
            )

            Log.d(TAG, "YOLO11n model loaded successfully")

        } catch (e: Exception) {
            interpreter?.close()
            interpreter = null

            debugInfo = debugInfo.copy(
                modelStatus = "Failed",
                inferenceStatus = "Unavailable",
                errorMessage =
                    "${e.javaClass.simpleName}: ${e.message ?: "Unknown error"}"
            )

            Log.e(TAG, "Failed to load YOLO11n model", e)
        }
    }

    fun isModelLoaded(): Boolean = interpreter != null

    fun detect(bitmap: Bitmap): List<YOLODetection> {

        val model = interpreter ?: return emptyList()

        val startTime = System.nanoTime()

        return try {
            val inputBuffer = preprocess(bitmap)

            val output = Array(1) {
                Array(OUTPUT_CHANNELS) {
                    FloatArray(NUM_PREDICTIONS)
                }
            }

            model.run(inputBuffer, output)

            val predictions = output[0]

            var maximumConfidence = 0f
            var bestClassId = -1
            var bestPredictionIndex = -1

            var candidatesAboveThreshold = 0
            var invalidBoxes = 0
            var validBoxesBeforeNMS = 0

            val detections = mutableListOf<YOLODetection>()

            val scale = min(
                INPUT_SIZE.toFloat() / bitmap.width,
                INPUT_SIZE.toFloat() / bitmap.height
            )

            val padX =
                (INPUT_SIZE - bitmap.width * scale) / 2f

            val padY =
                (INPUT_SIZE - bitmap.height * scale) / 2f

            for (i in 0 until NUM_PREDICTIONS) {

                var classId = -1
                var confidence = 0f

                for (c in 0 until NUM_CLASSES) {
                    val score = predictions[c + 4][i]

                    if (score > confidence) {
                        confidence = score
                        classId = c
                    }
                }

                if (confidence > maximumConfidence) {
                    maximumConfidence = confidence
                    bestClassId = classId
                    bestPredictionIndex = i
                }

                if (
                    confidence < CONFIDENCE_THRESHOLD ||
                    classId < 0
                ) {
                    continue
                }

                candidatesAboveThreshold++

                // Convert normalized coordinates
                // into 640x640 model input pixels.
                val centerX =
                    predictions[0][i] * BOX_COORDINATE_SCALE

                val centerY =
                    predictions[1][i] * BOX_COORDINATE_SCALE

                val width =
                    predictions[2][i] * BOX_COORDINATE_SCALE

                val height =
                    predictions[3][i] * BOX_COORDINATE_SCALE

                if (
                    !centerX.isFinite() ||
                    !centerY.isFinite() ||
                    !width.isFinite() ||
                    !height.isFinite() ||
                    width <= 0f ||
                    height <= 0f
                ) {
                    invalidBoxes++
                    continue
                }

                // Reverse the letterbox transformation.
                val x1 =
                    (centerX - width / 2f - padX) / scale

                val y1 =
                    (centerY - height / 2f - padY) / scale

                val x2 =
                    (centerX + width / 2f - padX) / scale

                val y2 =
                    (centerY + height / 2f - padY) / scale

                val left = x1.coerceIn(
                    0f,
                    bitmap.width.toFloat()
                )

                val top = y1.coerceIn(
                    0f,
                    bitmap.height.toFloat()
                )

                val right = x2.coerceIn(
                    0f,
                    bitmap.width.toFloat()
                )

                val bottom = y2.coerceIn(
                    0f,
                    bitmap.height.toFloat()
                )

                if (
                    !left.isFinite() ||
                    !top.isFinite() ||
                    !right.isFinite() ||
                    !bottom.isFinite() ||
                    right <= left ||
                    bottom <= top
                ) {
                    invalidBoxes++
                    continue
                }

                validBoxesBeforeNMS++

                detections.add(
                    YOLODetection(
                        classId = classId,
                        label = COCO_LABELS[classId],
                        confidence = confidence,
                        boundingBox = RectF(
                            left,
                            top,
                            right,
                            bottom
                        )
                    )
                )
            }

            val results = applyNMS(detections)

            val bestClass =
                if (bestClassId >= 0) {
                    COCO_LABELS[bestClassId]
                } else {
                    "None"
                }

            val rawBox =
                if (bestPredictionIndex >= 0) {
                    val i = bestPredictionIndex

                    "cx=${formatNumber(predictions[0][i])}, " +
                            "cy=${formatNumber(predictions[1][i])}, " +
                            "w=${formatNumber(predictions[2][i])}, " +
                            "h=${formatNumber(predictions[3][i])}"
                } else {
                    "None"
                }

            val elapsedMs =
                (System.nanoTime() - startTime) / 1_000_000L

            debugInfo = debugInfo.copy(
                inferenceStatus = "Success",
                detectionCount = results.size,
                maximumConfidence = maximumConfidence,
                inferenceTimeMs = elapsedMs,
                errorMessage = "",
                candidatesAboveThreshold = candidatesAboveThreshold,
                invalidBoxes = invalidBoxes,
                validBoxesBeforeNMS = validBoxesBeforeNMS,
                bestClass = bestClass,
                rawBox = rawBox
            )

            results

        } catch (e: Exception) {
            val elapsedMs =
                (System.nanoTime() - startTime) / 1_000_000L

            debugInfo = debugInfo.copy(
                inferenceStatus = "Failed",
                detectionCount = 0,
                inferenceTimeMs = elapsedMs,
                errorMessage =
                    "${e.javaClass.simpleName}: ${e.message ?: "Unknown error"}"
            )

            Log.e(TAG, "Object detection failed", e)

            emptyList()
        }
    }

    private fun formatNumber(value: Float): String {
        return String.format(
            Locale.US,
            "%.1f",
            value
        )
    }

    private fun preprocess(bitmap: Bitmap): ByteBuffer {

        val resizedBitmap = Bitmap.createBitmap(
            INPUT_SIZE,
            INPUT_SIZE,
            Bitmap.Config.ARGB_8888
        )

        try {
            val canvas = Canvas(resizedBitmap)

            val paint = Paint(
                Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG
            )

            canvas.drawColor(Color.BLACK)

            val scale = min(
                INPUT_SIZE.toFloat() / bitmap.width,
                INPUT_SIZE.toFloat() / bitmap.height
            )

            val scaledWidth = bitmap.width * scale
            val scaledHeight = bitmap.height * scale

            val left = (INPUT_SIZE - scaledWidth) / 2f
            val top = (INPUT_SIZE - scaledHeight) / 2f

            canvas.drawBitmap(
                bitmap,
                null,
                RectF(
                    left,
                    top,
                    left + scaledWidth,
                    top + scaledHeight
                ),
                paint
            )

            val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)

            resizedBitmap.getPixels(
                pixels,
                0,
                INPUT_SIZE,
                0,
                0,
                INPUT_SIZE,
                INPUT_SIZE
            )

            val inputBuffer = ByteBuffer.allocateDirect(
                3 * INPUT_SIZE * INPUT_SIZE * 4
            )

            inputBuffer.order(ByteOrder.nativeOrder())

            // NCHW: Red channel
            for (pixel in pixels) {
                inputBuffer.putFloat(
                    Color.red(pixel) / 255.0f
                )
            }

            // NCHW: Green channel
            for (pixel in pixels) {
                inputBuffer.putFloat(
                    Color.green(pixel) / 255.0f
                )
            }

            // NCHW: Blue channel
            for (pixel in pixels) {
                inputBuffer.putFloat(
                    Color.blue(pixel) / 255.0f
                )
            }

            inputBuffer.rewind()

            return inputBuffer

        } finally {
            resizedBitmap.recycle()
        }
    }

    private fun applyNMS(
        detections: List<YOLODetection>
    ): List<YOLODetection> {

        val sorted = detections.sortedByDescending {
            it.confidence
        }

        val selected = mutableListOf<YOLODetection>()

        for (candidate in sorted) {

            if (selected.size >= MAX_DETECTIONS) {
                break
            }

            val overlaps = selected.any { existing ->
                existing.classId == candidate.classId &&
                        calculateIoU(
                            existing.boundingBox,
                            candidate.boundingBox
                        ) > IOU_THRESHOLD
            }

            if (!overlaps) {
                selected.add(candidate)
            }
        }

        return selected
    }

    private fun calculateIoU(
        boxA: RectF,
        boxB: RectF
    ): Float {

        val intersectionLeft =
            max(boxA.left, boxB.left)

        val intersectionTop =
            max(boxA.top, boxB.top)

        val intersectionRight =
            min(boxA.right, boxB.right)

        val intersectionBottom =
            min(boxA.bottom, boxB.bottom)

        val intersectionWidth = max(
            0f,
            intersectionRight - intersectionLeft
        )

        val intersectionHeight = max(
            0f,
            intersectionBottom - intersectionTop
        )

        val intersectionArea =
            intersectionWidth * intersectionHeight

        val areaA =
            boxA.width() * boxA.height()

        val areaB =
            boxB.width() * boxB.height()

        val unionArea =
            areaA + areaB - intersectionArea

        return if (unionArea > 0f) {
            intersectionArea / unionArea
        } else {
            0f
        }
    }

    override fun close() {
        interpreter?.close()
        interpreter = null

        debugInfo = debugInfo.copy(
            modelStatus = "Closed",
            inferenceStatus = "Stopped"
        )

        Log.d(TAG, "YOLODetector resources released")
    }
}
