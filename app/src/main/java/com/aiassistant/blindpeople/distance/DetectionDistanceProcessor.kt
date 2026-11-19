
package com.aiassistant.blindpeople.distance

/**
 * Platform-independent detection information.
 * Coordinates must be in upright image pixels.
 */
data class DistanceDetectionInput(
    val detectionIndex: Int,
    val objectName: String,
    val confidence: Float,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val realObjectHeightMeters: Float? = null
)

/**
 * Distance result associated with an input detection.
 */
data class ObjectDistanceResult(
    val detectionIndex: Int,
    val objectName: String,
    val confidence: Float,
    val pipelineResult: DistancePipelineResult
)

/**
 * Processes multiple detected objects through
 * the existing distance pipeline.
 *
 * No Android framework dependencies.
 */
class DetectionDistanceProcessor {

    private val pipeline = DistancePipeline()

    fun process(
        detections: List<DistanceDetectionInput>,
        imageWidth: Int,
        imageHeight: Int,
        calibrationSamples: List<CalibrationSample>
    ): List<ObjectDistanceResult> {

        return detections.map { detection ->

            val result = pipeline.analyze(
                left = detection.left,
                top = detection.top,
                right = detection.right,
                bottom = detection.bottom,
                imageWidth = imageWidth,
                imageHeight = imageHeight,
                realObjectHeightMeters =
                    detection.realObjectHeightMeters,
                calibrationSamples = calibrationSamples
            )

            ObjectDistanceResult(
                detectionIndex = detection.detectionIndex,
                objectName = detection.objectName,
                confidence = detection.confidence,
                pipelineResult = result
            )
        }
    }
}
