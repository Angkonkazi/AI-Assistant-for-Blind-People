
package com.aiassistant.blindpeople.distance

import com.aiassistant.blindpeople.YOLODetection

/**
 * Experimental automatic distance estimation result.
 *
 * A null distance means that a suitable estimate
 * could not be produced.
 */
data class AutomaticDistanceResult(
    val detectionIndex: Int,
    val objectName: String,
    val confidence: Float,
    val estimatedMeters: Float?,
    val referenceSizeMeters: Float?,
    val referenceDimension: ReferenceDimension?,
    val boundingBoxPixels: Float?,
    val focalLengthPixels: Float?,
    val status: String
)

/**
 * Lightweight experimental metric-distance estimator.
 *
 * Uses camera metadata, YOLO bounding boxes,
 * and assumed physical object dimensions.
 *
 * No additional neural network.
 * No manual user calibration.
 *
 * Not validated for navigation or obstacle avoidance.
 */
class AutomaticDistanceEstimator {

    companion object {

        private const val MIN_BOX_PIXELS = 20f

        private const val EDGE_MARGIN_PIXELS = 2f
    }

    private val distanceEstimator = DistanceEstimator()

    fun estimate(
        detections: List<YOLODetection>,
        focalLength: AutomaticFocalLength?,
        imageWidth: Int,
        imageHeight: Int
    ): List<AutomaticDistanceResult> {

        return detections.mapIndexed { index, detection ->

            estimateObject(
                index = index,
                detection = detection,
                focalLength = focalLength,
                imageWidth = imageWidth,
                imageHeight = imageHeight
            )
        }
    }

    private fun estimateObject(
        index: Int,
        detection: YOLODetection,
        focalLength: AutomaticFocalLength?,
        imageWidth: Int,
        imageHeight: Int
    ): AutomaticDistanceResult {

        val reference =
            ObjectSizeDatabase.findReference(
                detection.label
            )

        fun unavailable(
            status: String
        ): AutomaticDistanceResult {

            return AutomaticDistanceResult(
                detectionIndex = index,
                objectName = detection.label,
                confidence = detection.confidence,
                estimatedMeters = null,
                referenceSizeMeters =
                    reference?.sizeMeters,
                referenceDimension =
                    reference?.dimension,
                boundingBoxPixels = null,
                focalLengthPixels = null,
                status = status
            )
        }

        // Camera geometry must be available.
        if (focalLength == null) {
            return unavailable(
                "Camera focal length unavailable"
            )
        }

        // Verify that the camera parameters
        // match the current analysis bitmap.
        if (
            imageWidth <= 0 ||
            imageHeight <= 0 ||
            focalLength.imageWidth != imageWidth ||
            focalLength.imageHeight != imageHeight
        ) {
            return unavailable(
                "Image dimensions mismatch"
            )
        }

        // Do not invent physical object sizes.
        if (reference == null) {
            return unavailable(
                "Unsupported object class"
            )
        }

        val box = detection.boundingBox

        // Reject invalid bounding-box coordinates.
        if (
            !box.left.isFinite() ||
            !box.top.isFinite() ||
            !box.right.isFinite() ||
            !box.bottom.isFinite() ||
            box.width() <= 0f ||
            box.height() <= 0f
        ) {
            return unavailable(
                "Invalid bounding box"
            )
        }

        // Reject potentially truncated objects.
        if (
            box.left <= EDGE_MARGIN_PIXELS ||
            box.top <= EDGE_MARGIN_PIXELS ||
            box.right >=
            imageWidth - EDGE_MARGIN_PIXELS ||
            box.bottom >=
            imageHeight - EDGE_MARGIN_PIXELS
        ) {
            return unavailable(
                "Possibly truncated object"
            )
        }

        // Select the appropriate measurement axis.
        val boundingBoxPixels: Float

        val effectiveFocalLength: Float

        when (reference.dimension) {

            ReferenceDimension.HEIGHT -> {

                boundingBoxPixels = box.height()

                effectiveFocalLength =
                    focalLength.focalY
            }

            ReferenceDimension.WIDTH -> {

                boundingBoxPixels = box.width()

                effectiveFocalLength =
                    focalLength.focalX
            }
        }

        // Reject very small bounding boxes.
        if (boundingBoxPixels < MIN_BOX_PIXELS) {
            return unavailable(
                "Bounding box too small"
            )
        }

        // Reuse the existing pinhole formula.
        // The formula works for either dimension.
        val estimate =
            distanceEstimator.estimateDistance(
                objectHeightPixels =
                    boundingBoxPixels,
                realObjectHeightMeters =
                    reference.sizeMeters,
                focalLengthPixels =
                    effectiveFocalLength
            )

        return AutomaticDistanceResult(
            detectionIndex = index,
            objectName = detection.label,
            confidence = detection.confidence,
            estimatedMeters =
                estimate.estimatedMeters,
            referenceSizeMeters =
                reference.sizeMeters,
            referenceDimension =
                reference.dimension,
            boundingBoxPixels =
                boundingBoxPixels,
            focalLengthPixels =
                effectiveFocalLength,
            status =
                if (estimate.estimatedMeters != null) {
                    "Experimental size-based estimate"
                } else {
                    estimate.status
                }
        )
    }
}
