
package com.aiassistant.blindpeople

import android.graphics.Paint
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Displays YOLO detections and experimental
 * size-based distance estimates.
 *
 * Distance estimates are not validated for
 * navigation or obstacle avoidance.
 */
@Composable
fun DetectionOverlay(
    detections: List<YOLODetection>,
    imageWidth: Int,
    imageHeight: Int,
    modifier: Modifier = Modifier,
    distanceMetersByIndex: Map<Int, Float?> = emptyMap()
) {

    Canvas(
        modifier = modifier.fillMaxSize()
    ) {

        if (
            imageWidth <= 0 ||
            imageHeight <= 0 ||
            detections.isEmpty() ||
            size.width <= 0f ||
            size.height <= 0f
        ) {
            return@Canvas
        }

        // Match CameraX FILL_CENTER scaling.
        val scale = max(
            size.width / imageWidth.toFloat(),
            size.height / imageHeight.toFloat()
        )

        val displayedWidth = imageWidth * scale
        val displayedHeight = imageHeight * scale

        val offsetX =
            (size.width - displayedWidth) / 2f

        val offsetY =
            (size.height - displayedHeight) / 2f

        val boxColor = Color.Green

        val strokeWidth = 3f * density
        val labelTextSize = 14f * density

        val textPaint = TextPaint().apply {
            color = android.graphics.Color.WHITE
            textSize = labelTextSize
            isAntiAlias = true
            typeface =
                android.graphics.Typeface.DEFAULT_BOLD
        }

        val backgroundPaint = Paint().apply {
            color = android.graphics.Color.BLACK
            alpha = 190
            isAntiAlias = true
        }

        detections.forEachIndexed { index, detection ->

            val box = detection.boundingBox

            // Reject invalid bounding boxes.
            if (
                !box.left.isFinite() ||
                !box.top.isFinite() ||
                !box.right.isFinite() ||
                !box.bottom.isFinite()
            ) {
                return@forEachIndexed
            }

            val left = box.left * scale + offsetX
            val top = box.top * scale + offsetY
            val right = box.right * scale + offsetX
            val bottom = box.bottom * scale + offsetY

            val clampedLeft =
                left.coerceIn(0f, size.width)

            val clampedTop =
                top.coerceIn(0f, size.height)

            val clampedRight =
                right.coerceIn(0f, size.width)

            val clampedBottom =
                bottom.coerceIn(0f, size.height)

            if (
                clampedRight <= clampedLeft ||
                clampedBottom <= clampedTop
            ) {
                return@forEachIndexed
            }

            // Draw the existing green bounding box.
            drawRect(
                color = boxColor,
                topLeft = Offset(
                    clampedLeft,
                    clampedTop
                ),
                size = Size(
                    clampedRight - clampedLeft,
                    clampedBottom - clampedTop
                ),
                style = Stroke(
                    width = strokeWidth
                )
            )

            // YOLO confidence percentage.
            val confidencePercent =
                (detection.confidence * 100)
                    .toInt()
                    .coerceIn(0, 100)

            // Retrieve the experimental distance
            // belonging to this exact detection.
            val experimentalDistance =
                distanceMetersByIndex[index]
                    ?.takeIf {
                        it.isFinite() && it > 0f
                    }

            val distanceText =
                formatExperimentalDistance(
                    experimentalDistance
                )

            // Combine class, confidence and distance.
            val labelText =
                "${detection.label} " +
                        "$confidencePercent% | " +
                        distanceText

            val padding = 6f * density

            val labelHeight =
                labelTextSize + padding * 2f

            val availableTextWidth =
                (size.width - padding * 2f)
                    .coerceAtLeast(0f)

            if (availableTextWidth <= 0f) {
                return@forEachIndexed
            }

            // Prevent long labels from extending
            // outside the camera preview.
            val fittedLabel =
                TextUtils.ellipsize(
                    labelText,
                    textPaint,
                    availableTextWidth,
                    TextUtils.TruncateAt.END
                ).toString()

            val textWidth =
                textPaint.measureText(fittedLabel)

            val labelWidth =
                min(
                    size.width,
                    textWidth + padding * 2f
                )

            val labelLeft =
                min(
                    clampedLeft,
                    max(
                        0f,
                        size.width - labelWidth
                    )
                )

            val preferredLabelTop =
                if (clampedTop >= labelHeight) {
                    clampedTop - labelHeight
                } else {
                    clampedTop
                }

            val labelTop =
                preferredLabelTop.coerceIn(
                    0f,
                    max(
                        0f,
                        size.height - labelHeight
                    )
                )

            // Draw label background and text.
            drawIntoCanvas { canvas ->

                val nativeCanvas =
                    canvas.nativeCanvas

                nativeCanvas.drawRect(
                    labelLeft,
                    labelTop,
                    labelLeft + labelWidth,
                    labelTop + labelHeight,
                    backgroundPaint
                )

                nativeCanvas.drawText(
                    fittedLabel,
                    labelLeft + padding,
                    labelTop + padding + labelTextSize,
                    textPaint
                )
            }
        }
    }
}

/**
 * Formats experimental distance estimates.
 *
 * Below 1 meter: whole centimeters.
 * At or above 1 meter: two decimal places.
 * Missing or invalid distance: N/A.
 */
private fun formatExperimentalDistance(
    distanceMeters: Float?
): String {

    if (
        distanceMeters == null ||
        !distanceMeters.isFinite() ||
        distanceMeters <= 0f
    ) {
        return "Distance: N/A"
    }

    return if (distanceMeters < 1f) {

        val centimeters =
            (distanceMeters * 100f).roundToInt()

        "TEST ~$centimeters cm"

    } else {

        String.format(
            Locale.US,
            "TEST ~%.2f m",
            distanceMeters
        )
    }
}
