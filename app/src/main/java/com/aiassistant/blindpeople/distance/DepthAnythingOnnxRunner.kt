
package com.aiassistant.blindpeople.distance

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import java.io.Closeable
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Automatic metric depth estimation using:
 * Depth Anything V2 Metric Indoor Small.
 *
 * Runtime: ONNX Runtime for Android.
 *
 * No user-entered measurements or calibration.
 *
 * IMPORTANT:
 * This implementation uses the fixed portrait input
 * resolution of our verified ONNX export.
 *
 * The output represents experimental camera-axis
 * metric depth estimates, not validated safe distances.
 */
class DepthAnythingOnnxRunner(
    context: Context
) : Closeable {

    companion object {

        private const val TAG = "DEPTH_ONNX"

        private const val MODEL_FILE =
            "depth_anything_v2_metric_indoor_small.onnx"

        private const val INPUT_WIDTH = 518
        private const val INPUT_HEIGHT = 686

        private const val CHANNELS = 3

        private val INPUT_SHAPE = longArrayOf(
            1L,
            CHANNELS.toLong(),
            INPUT_HEIGHT.toLong(),
            INPUT_WIDTH.toLong()
        )

        private val OUTPUT_SHAPE = longArrayOf(
            1L,
            INPUT_HEIGHT.toLong(),
            INPUT_WIDTH.toLong()
        )

        // ImageNet normalization used by the
        // original Hugging Face image processor.
        private val MEAN = floatArrayOf(
            0.485f,
            0.456f,
            0.406f
        )

        private val STD = floatArrayOf(
            0.229f,
            0.224f,
            0.225f
        )
    }

    private val environment: OrtEnvironment =
        OrtEnvironment.getEnvironment()

    private val sessionOptions =
        OrtSession.SessionOptions()

    private val modelFile =
        File(context.filesDir, MODEL_FILE)

    private val session: OrtSession

    init {

        if (
            !modelFile.exists() ||
            modelFile.length() == 0L
        ) {

            context.assets.open(MODEL_FILE).use { input ->

                modelFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }

        session = environment.createSession(
            modelFile.absolutePath,
            sessionOptions
        )

        verifyModel()

        Log.i(
            TAG,
            "Depth Anything V2 ONNX model loaded"
        )
    }

    /**
     * Verify fixed ONNX input and output shapes.
     */
    private fun verifyModel() {

        val inputInfo =
            session.inputInfo["pixel_values"]
                ?: error("Missing pixel_values input")

        val inputTensorInfo =
            inputInfo.info as? TensorInfo
                ?: error("Invalid input tensor")

        require(
            inputTensorInfo.shape.contentEquals(INPUT_SHAPE)
        ) {
            "Unexpected input shape: " +
                    inputTensorInfo.shape.contentToString()
        }

        val outputInfo =
            session.outputInfo["predicted_depth"]
                ?: error("Missing predicted_depth output")

        val outputTensorInfo =
            outputInfo.info as? TensorInfo
                ?: error("Invalid output tensor")

        require(
            outputTensorInfo.shape.contentEquals(OUTPUT_SHAPE)
        ) {
            "Unexpected output shape: " +
                    outputTensorInfo.shape.contentToString()
        }
    }

    /**
     * Convert a Bitmap to a normalized Float32 tensor.
     *
     * Input format:
     * [1, 3, 686, 518]
     *
     * Channel order:
     * Red plane, Green plane, Blue plane.
     *
     * This experimental preprocessing resizes directly
     * to the fixed ONNX input resolution.
     *
     * For live camera integration, we must preserve
     * spatial coordinate alignment with YOLO.
     */
    private fun preprocess(
        bitmap: Bitmap
    ): ByteBuffer {

        require(
            bitmap.width > 0 && bitmap.height > 0
        ) {
            "Input Bitmap must have positive dimensions."
        }

        val resizedBitmap = Bitmap.createScaledBitmap(
            bitmap,
            INPUT_WIDTH,
            INPUT_HEIGHT,
            true
        )

        try {

            val pixels = IntArray(
                INPUT_WIDTH * INPUT_HEIGHT
            )

            resizedBitmap.getPixels(
                pixels,
                0,
                INPUT_WIDTH,
                0,
                0,
                INPUT_WIDTH,
                INPUT_HEIGHT
            )

            val pixelCount =
                INPUT_WIDTH * INPUT_HEIGHT

            val inputBuffer =
                ByteBuffer.allocateDirect(
                    CHANNELS * pixelCount * 4
                ).order(ByteOrder.nativeOrder())

            val floatBuffer =
                inputBuffer.asFloatBuffer()

            for (i in pixels.indices) {

                val pixel = pixels[i]

                val red =
                    ((pixel ushr 16) and 0xFF) / 255.0f

                val green =
                    ((pixel ushr 8) and 0xFF) / 255.0f

                val blue =
                    (pixel and 0xFF) / 255.0f

                floatBuffer.put(
                    i,
                    (red - MEAN[0]) / STD[0]
                )

                floatBuffer.put(
                    pixelCount + i,
                    (green - MEAN[1]) / STD[1]
                )

                floatBuffer.put(
                    2 * pixelCount + i,
                    (blue - MEAN[2]) / STD[2]
                )
            }

            return inputBuffer

        } finally {

            if (resizedBitmap !== bitmap) {
                resizedBitmap.recycle()
            }
        }
    }

    /**
     * Perform automatic depth inference.
     *
     * The Bitmap must already be upright.
     *
     * Returns a metric-depth map in meters.
     *
     * The map dimensions are 518 x 686.
     */
    fun estimateDepth(
        bitmap: Bitmap
    ): MetricDepthMap {

        val startTime = System.nanoTime()

        val inputBuffer = preprocess(bitmap)

        val inputTensor = OnnxTensor.createTensor(
            environment,
            inputBuffer.asFloatBuffer(),
            INPUT_SHAPE
        )

        inputTensor.use { tensor ->

            session.run(
                mapOf("pixel_values" to tensor)
            ).use { results ->

                val outputTensor =
                    results[0] as? OnnxTensor
                        ?: error(
                            "Unexpected ONNX depth output"
                        )

                val depthValues = FloatArray(
                    INPUT_WIDTH * INPUT_HEIGHT
                )

                val outputBuffer =
                    outputTensor.floatBuffer

                outputBuffer.rewind()
                outputBuffer.get(depthValues)

                val inferenceTimeMs =
                    (System.nanoTime() - startTime) /
                            1_000_000.0

                val validValues = depthValues.filter {
                    it.isFinite() && it > 0f
                }

                if (validValues.isEmpty()) {
                    throw IllegalStateException(
                        "Depth model returned no valid depths."
                    )
                }

                Log.d(
                    TAG,
                    "Depth inference completed: " +
                            "%.2f ms".format(inferenceTimeMs)
                )

                Log.d(
                    TAG,
                    "Depth range: " +
                            "${validValues.minOrNull()} to " +
                            "${validValues.maxOrNull()} meters"
                )

                return MetricDepthMap(
                    width = INPUT_WIDTH,
                    height = INPUT_HEIGHT,
                    valuesMeters = depthValues
                )
            }
        }
    }

    fun getModelInfo(): String {

        return buildString {

            appendLine(
                "Depth Anything V2 Metric Indoor Small"
            )

            appendLine(
                "Runtime: ONNX Runtime Android"
            )

            appendLine(
                "Input: [1, 3, 686, 518]"
            )

            appendLine(
                "Output: [1, 686, 518]"
            )

            appendLine(
                "Model size: ${modelFile.length()} bytes"
            )

            appendLine(
                "Status: Session created"
            )
        }
    }

    override fun close() {

        session.close()
        sessionOptions.close()

        Log.i(
            TAG,
            "Depth ONNX session closed"
        )
    }
}
