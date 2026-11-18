
package com.aiassistant.blindpeople

// Task A1: Horizontal Direction
enum class HorizontalZone {
    LEFT,
    CENTER,
    RIGHT
}

data class HorizontalPosition(
    val normalizedCenterX: Float,
    val zone: HorizontalZone
)

// Task A2: Vertical Direction
enum class VerticalZone {
    TOP,
    MIDDLE,
    BOTTOM
}

data class VerticalPosition(
    val normalizedCenterY: Float,
    val zone: VerticalZone
)

class SpatialAnalyzer {

    // Task A1: Horizontal Position Calculation
    fun getHorizontalPosition(
        detection: YOLODetection,
        imageWidth: Int
    ): HorizontalPosition {

        require(imageWidth > 0) {
            "Image width must be greater than zero"
        }

        val centerX = detection.boundingBox.centerX()

        val normalizedX =
            (centerX / imageWidth.toFloat())
                .coerceIn(0f, 1f)

        val zone = when {
            normalizedX < 1f / 3f ->
                HorizontalZone.LEFT

            normalizedX < 2f / 3f ->
                HorizontalZone.CENTER

            else ->
                HorizontalZone.RIGHT
        }

        return HorizontalPosition(
            normalizedCenterX = normalizedX,
            zone = zone
        )
    }

    // Task A2: Vertical Position Calculation
    fun getVerticalPosition(
        detection: YOLODetection,
        imageHeight: Int
    ): VerticalPosition {

        require(imageHeight > 0) {
            "Image height must be greater than zero"
        }

        val centerY = detection.boundingBox.centerY()

        val normalizedY =
            (centerY / imageHeight.toFloat())
                .coerceIn(0f, 1f)

        val zone = when {
            normalizedY < 1f / 3f ->
                VerticalZone.TOP

            normalizedY < 2f / 3f ->
                VerticalZone.MIDDLE

            else ->
                VerticalZone.BOTTOM
        }

        return VerticalPosition(
            normalizedCenterY = normalizedY,
            zone = zone
        )
    }
}
