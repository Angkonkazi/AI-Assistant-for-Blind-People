
package com.aiassistant.blindpeople.distance

/**
 * Represents a detected object before it is
 * converted into the distance module's format.
 *
 * Bounding-box coordinates must use pixels
 * in the same upright image coordinate system.
 */
data class RawDetectionInput(
    val index: Int,
    val label: String,
    val confidence: Float,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

/**
 * Converts raw detections into distance inputs.
 *
 * Physical heights are supplied only when
 * a specific detected reference object has
 * been measured.
 */
class DetectionInputMapper {

    fun convert(
        detections: List<RawDetectionInput>,
        knownHeightsByIndex: Map<Int, Float> = emptyMap()
    ): List<DistanceDetectionInput> {

        return detections.map { detection ->

            val measuredHeight =
                knownHeightsByIndex[detection.index]
                    ?.takeIf {
                        it.isFinite() && it > 0f
                    }

            DistanceDetectionInput(
                detectionIndex = detection.index,
                objectName = detection.label,
                confidence = detection.confidence,
                left = detection.left,
                top = detection.top,
                right = detection.right,
                bottom = detection.bottom,
                realObjectHeightMeters = measuredHeight
            )
        }
    }
}
