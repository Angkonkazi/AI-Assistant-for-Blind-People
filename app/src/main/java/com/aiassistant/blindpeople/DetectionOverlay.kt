
package com.aiassistant.blindpeople

import android.graphics.Paint
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
            detections.isEmpty()
        ) {
            return@Canvas
        }

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
        val textSize = 14f * density

        val textPaint = Paint().apply {
            color = android.graphics.Color.WHITE
            this.textSize = textSize
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

            val left = box.left * scale + offsetX
            val top = box.top * scale + offsetY
            val right = box.right * scale + offsetX
            val bottom = box.bottom * scale + offsetY

            val clampedLeft = left.coerceIn(
                0f,
                size.width
            )

            val clampedTop = top.coerceIn(
                0f,
                size.height
            )

            val clampedRight = right.coerceIn(
                0f,
                size.width
            )

            val clampedBottom = bottom.coerceIn(
                0f,
                size.height
            )

            if (
                clampedRight <= clampedLeft ||
                clampedBottom <= clampedTop
            ) {
                return@forEachIndexed
            }

            // Draw the green bounding box.
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
                (detection.confidence * 100).toInt()

            // Read the experimental distance.
            val experimentalDistance =
                distanceMetersByIndex[index]
                    ?.takeIf {
                        it.isFinite() && it > 0f
                    }

            // Never invent a distance when
            // calibration is unavailable.
            val distanceText =
                if (experimentalDistance == null) {
                    "Distance: Unknown"
                } else {
                    String.format(
                        Locale.US,
                        "TEST ~%.2f m",
                        experimentalDistance
                    )
                }

            // Build the complete object label.
            val labelText =
                "${detection.label} " +
                        "$confidencePercent% | $distanceText"

            val textWidth =
                textPaint.measureText(labelText)

            val padding = 6f * density

            val labelHeight =
                textSize + padding * 2f

            val labelWidth =
                textWidth + padding * 2f

            val labelLeft = min(
                clampedLeft,
                max(
                    0f,
                    size.width - labelWidth
                )
            )

            val labelTop =
                if (clampedTop >= labelHeight) {
                    clampedTop - labelHeight
                } else {
                    clampedTop
                }

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
                    labelText,
                    labelLeft + padding,
                    labelTop + padding + textSize,
                    textPaint
                )
            }
        }
    }
}
