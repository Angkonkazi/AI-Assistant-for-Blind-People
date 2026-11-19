
package com.aiassistant.blindpeople.distance

data class BoundingBoxMetrics(
    val width: Float,
    val height: Float,
    val widthRatio: Float,
    val heightRatio: Float,
    val areaRatio: Float
)

class BoundingBoxCalculator {

    fun calculate(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        imageWidth: Int,
        imageHeight: Int
    ): BoundingBoxMetrics? {

        // Validate image dimensions.
        if (imageWidth <= 0 || imageHeight <= 0) {
            return null
        }

        // Validate all bounding-box coordinates.
        val coordinates = listOf(
            left, top, right, bottom
        )

        if (coordinates.any { !it.isFinite() }) {
            return null
        }

        // Calculate bounding-box dimensions.
        val boxWidth = right - left
        val boxHeight = bottom - top

        if (boxWidth <= 0f || boxHeight <= 0f) {
            return null
        }

        // Reject coordinates outside the image.
        if (
            left < 0f ||
            top < 0f ||
            right > imageWidth.toFloat() ||
            bottom > imageHeight.toFloat()
        ) {
            return null
        }

        // Calculate normalized measurements.
        val widthRatio =
            boxWidth / imageWidth.toFloat()

        val heightRatio =
            boxHeight / imageHeight.toFloat()

        val areaRatio =
            widthRatio * heightRatio

        return BoundingBoxMetrics(
            width = boxWidth,
            height = boxHeight,
            widthRatio = widthRatio,
            heightRatio = heightRatio,
            areaRatio = areaRatio
        )
    }
}
