
package com.aiassistant.blindpeople.distance

import com.aiassistant.blindpeople.YOLODetection

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

class ProximityEstimator {

    private val calculator = BoundingBoxCalculator()

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
            ) ?: return@forEachIndexed

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
                    proximityLevel = ProximityLevel.UNKNOWN
                )
            )
        }

        return results
    }
}
