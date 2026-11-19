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

/**
 * Combined detection overlay.
 *
 * Developer A:
 * - 3x3 spatial grid
 * - Center focus marker
 * - Selected object highlighting
 *
 * Developer B:
 * - Experimental distance display
 * - Distance unavailable fallback
 *
 * Numerical distances are experimental and
 * must not be used for navigation guidance
 * without real-world validation.
 */
@Composable
fun DetectionOverlay(
    detections: List<YOLODetection>,
    selectedDetection: YOLODetection? = null,
    imageWidth: Int,
    imageHeight: Int,
    modifier: Modifier = Modifier,
    distanceMetersByIndex: Map<Int, Float?> = emptyMap()
) {

    Canvas(
        modifier = modifier.fillMaxSize()
    ) {

        if (imageWidth <= 0 || imageHeight <= 0) {
            return@Canvas
        }

        // Match the camera preview's FILL_CENTER
        // scaling and cropping behavior.
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

        // ======================================
        // DEVELOPER A: 3x3 SPATIAL GRID
        // ======================================

        val gridColor = Color.Cyan.copy(
            alpha = 0.45f
        )

        val gridStroke = 1f * density

        // First vertical grid line.
        drawLine(
            color = gridColor,
            start = Offset(
                x = size.width / 3f,
                y = 0f
            ),
            end = Offset(
                x = size.width / 3f,
                y = size.height
            ),
            strokeWidth = gridStroke
        )

        // Second vertical grid line.
        drawLine(
            color = gridColor,
            start = Offset(
                x = size.width * 2f / 3f,
                y = 0f
            ),
            end = Offset(
                x = size.width * 2f / 3f,
                y = size.height
            ),
            strokeWidth = gridStroke
        )

        // First horizontal grid line.
        drawLine(
            color = gridColor,
            start = Offset(
                x = 0f,
                y = size.height / 3f
            ),
            end = Offset(
                x = size.width,
                y = size.height / 3f
            ),
            strokeWidth = gridStroke
        )

        // Second horizontal grid line.
        drawLine(
            color = gridColor,
            start = Offset(
                x = 0f,
                y = size.height * 2f / 3f
            ),
            end = Offset(
                x = size.width,
                y = size.height * 2f / 3f
            ),
            strokeWidth = gridStroke
        )

        // ======================================
        // DEVELOPER A: CENTER FOCUS MARKER
        // ======================================

        val focusCenter = Offset(
            x = size.width / 2f,
            y = size.height / 2f
        )

        val focusColor = Color.Red
        val markerSize = 18f * density
        val markerStroke = 2f * density

        drawCircle(
            color = focusColor,
            radius = markerSize,
            center = focusCenter,
            style = Stroke(
                width = markerStroke
            )
        )

        // Horizontal crosshair.
        drawLine(
            color = focusColor,
            start = Offset(
                x = focusCenter.x -
                        markerSize * 1.5f,
                y = focusCenter.y
            ),
            end = Offset(
                x = focusCenter.x +
                        markerSize * 1.5f,
                y = focusCenter.y
            ),
            strokeWidth = markerStroke
        )

        // Vertical crosshair.
        drawLine(
            color = focusColor,
            start = Offset(
                x = focusCenter.x,
                y = focusCenter.y -
                        markerSize * 1.5f
            ),
            end = Offset(
                x = focusCenter.x,
                y = focusCenter.y +
                        markerSize * 1.5f
            ),
            strokeWidth = markerStroke
        )

        // ======================================
        // COMBINED OBJECT DETECTION OVERLAY
        // ======================================

        val normalBoxColor = Color.Green
        val selectedBoxColor = Color.Yellow

        val normalStrokeWidth = 3f * density
        val selectedStrokeWidth = 5f * density

        val textSize = 14f * density
        val padding = 6f * density

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

            // Developer A: Selected focus object.
            val isSelected =
                selectedDetection == detection

            val currentBoxColor =
                if (isSelected) {
                    selectedBoxColor
                } else {
                    normalBoxColor
                }

            val currentStrokeWidth =
                if (isSelected) {
                    selectedStrokeWidth
                } else {
                    normalStrokeWidth
                }

            // Get original image-space bounding box.
            val box = detection.boundingBox

            // Transform the box into preview space.
            val left =
                box.left * scale + offsetX

            val top =
                box.top * scale + offsetY

            val right =
                box.right * scale + offsetX

            val bottom =
                box.bottom * scale + offsetY

            // Keep drawings inside screen bounds.
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

            // Draw the bounding box.
            drawRect(
                color = currentBoxColor,
                topLeft = Offset(
                    x = clampedLeft,
                    y = clampedTop
                ),
                size = Size(
                    width =
                        clampedRight - clampedLeft,
                    height =
                        clampedBottom - clampedTop
                ),
                style = Stroke(
                    width = currentStrokeWidth
                )
            )

            // ==================================
            // OBJECT LABEL
            // ==================================

            val confidencePercent =
                (detection.confidence * 100)
                    .toInt()
                    .coerceIn(0, 100)

            // Developer B:
            // Get experimental distance, if valid.
            val experimentalDistance =
                distanceMetersByIndex[index]
                    ?.takeIf {
                        it.isFinite() && it > 0f
                    }

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

            // Developer A:
            // Mark the selected focus object.
            val focusText =
                if (isSelected) {
                    " [FOCUS]"
                } else {
                    ""
                }

            // Combined label.
            val labelText =
                "${detection.label} " +
                        "$confidencePercent%" +
                        focusText +
                        " | " +
                        distanceText

            val textWidth =
                textPaint.measureText(labelText)

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
