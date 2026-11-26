
package com.aiassistant.blindpeople.distance

import com.aiassistant.blindpeople.YOLODetection

/**
 * Experimental bounding-box-based proximity categories.
 *
 * These categories describe apparent proximity based
 * on image occupancy, not verified physical distance.
 */
enum class ProximityLevel {
    NEAR,
    MEDIUM,
    FAR,
    UNKNOWN
}

data class ProximityResult(
    val detectionIndex: Int,
    val objectName: String,
    val confidence: Float,
    val boundingBoxWidth: Float,
    val boundingBoxHeight: Float,
    val widthRatio: Float,
    val heightRatio: Float,
    val areaRatio: Float,
    val proximityLevel: ProximityLevel
)

/**
 * Lightweight bounding-box proximity estimation.
 *
 * Uses existing YOLO11n detections.
 *
 * No additional AI model.
 * No manual calibration.
 * No known object height required.
 *
 * Experimental baseline only.
 * Not validated for navigation guidance.
 */
class ProximityEstimator {

    companion object {

        // Initial experimental thresholds.
        // These must be evaluated using controlled tests.
        private const val NEAR_AREA_THRESHOLD = 0.30f
        private const val MEDIUM_AREA_THRESHOLD = 0.10f
    }

    private val calculator = BoundingBoxCalculator()

    /**
     * Classifies apparent proximity using the proportion
     * of the image occupied by a bounding box.
     */
    fun classifyProximity(
        areaRatio: Float
    ): ProximityLevel {

        if (
            !areaRatio.isFinite() ||
            areaRatio <= 0f ||
            areaRatio > 1f
        ) {
            return ProximityLevel.UNKNOWN
        }

        return when {

            areaRatio >= NEAR_AREA_THRESHOLD ->
                ProximityLevel.NEAR

            areaRatio >= MEDIUM_AREA_THRESHOLD ->
                ProximityLevel.MEDIUM

            else ->
                ProximityLevel.FAR
        }
    }

    /**
     * Calculates proximity for each detected object.
     */
    fun estimate(
        detections: List<YOLODetection>,
        imageWidth: Int,
        imageHeight: Int
    ): List<ProximityResult> {

        val results = mutableListOf<ProximityResult>()

        detections.forEachIndexed { index, detection ->

            val box = detection.boundingBox

            val metrics = calculator.calculate(
                left = box.left,
                top = box.top,
                right = box.right,
                bottom = box.bottom,
                imageWidth = imageWidth,
                imageHeight = imageHeight
            )

            // Preserve the detection index even when
            // bounding-box metrics are unavailable.
            if (metrics == null) {

                results.add(
                    ProximityResult(
                        detectionIndex = index,
                        objectName = detection.label,
                        confidence = detection.confidence,
                        boundingBoxWidth = 0f,
                        boundingBoxHeight = 0f,
                        widthRatio = 0f,
                        heightRatio = 0f,
                        areaRatio = 0f,
                        proximityLevel = ProximityLevel.UNKNOWN
                    )
                )

                return@forEachIndexed
            }

            val proximityLevel = classifyProximity(
                areaRatio = metrics.areaRatio
            )

            results.add(
                ProximityResult(
                    detectionIndex = index,
                    objectName = detection.label,
                    confidence = detection.confidence,
                    boundingBoxWidth = metrics.width,
                    boundingBoxHeight = metrics.height,
                    widthRatio = metrics.widthRatio,
                    heightRatio = metrics.heightRatio,
                    areaRatio = metrics.areaRatio,
                    proximityLevel = proximityLevel
                )
            )
        }

        return results
    }
}
