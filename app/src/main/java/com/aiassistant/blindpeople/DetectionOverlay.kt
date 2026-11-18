
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
import kotlin.math.max
import kotlin.math.min

@Composable
fun DetectionOverlay(
    detections: List<YOLODetection>,
    selectedDetection: YOLODetection?,
    imageWidth: Int,
    imageHeight: Int,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier.fillMaxSize()
    ) {

        // Check if camera image dimensions are valid.
        if (imageWidth <= 0 || imageHeight <= 0) {
            return@Canvas
        }

        // Keep the existing bounding box transformation.
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

        // ============================================
        // Task A4: 3x3 Spatial Grid
        // ============================================

        val gridColor = Color.Cyan.copy(alpha = 0.45f)
        val gridStroke = 1f * density

        // First vertical line
        drawLine(
            color = gridColor,
            start = Offset(size.width / 3f, 0f),
            end = Offset(size.width / 3f, size.height),
            strokeWidth = gridStroke
        )

        // Second vertical line
        drawLine(
            color = gridColor,
            start = Offset(size.width * 2f / 3f, 0f),
            end = Offset(size.width * 2f / 3f, size.height),
            strokeWidth = gridStroke
        )

        // First horizontal line
        drawLine(
            color = gridColor,
            start = Offset(0f, size.height / 3f),
            end = Offset(size.width, size.height / 3f),
            strokeWidth = gridStroke
        )

        // Second horizontal line
        drawLine(
            color = gridColor,
            start = Offset(0f, size.height * 2f / 3f),
            end = Offset(size.width, size.height * 2f / 3f),
            strokeWidth = gridStroke
        )

        // ============================================
        // Task A4: Center Focus Marker
        // ============================================

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
            style = Stroke(width = markerStroke)
        )

        // Horizontal crosshair line
        drawLine(
            color = focusColor,
            start = Offset(
                focusCenter.x - markerSize * 1.5f,
                focusCenter.y
            ),
            end = Offset(
                focusCenter.x + markerSize * 1.5f,
                focusCenter.y
            ),
            strokeWidth = markerStroke
        )

        // Vertical crosshair line
        drawLine(
            color = focusColor,
            start = Offset(
                focusCenter.x,
                focusCenter.y - markerSize * 1.5f
            ),
            end = Offset(
                focusCenter.x,
                focusCenter.y + markerSize * 1.5f
            ),
            strokeWidth = markerStroke
        )

        // ============================================
        // Existing YOLO Bounding Box Drawing
        // ============================================

        // Developer A - Selected Object Highlight Colors
        val normalBoxColor = Color.Green
        val selectedBoxColor = Color.Yellow
        val strokeWidth = 3f * density
        val textSize = 14f * density

        val textPaint = Paint().apply {
            color = android.graphics.Color.WHITE
            this.textSize = textSize
            isAntiAlias = true
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        val backgroundPaint = Paint().apply {
            color = android.graphics.Color.BLACK
            alpha = 190
            isAntiAlias = true
        }

        detections.forEach { detection ->
            // Developer A - Check selected focus object
            val isSelected = selectedDetection == detection

            val currentBoxColor =
                if (isSelected) selectedBoxColor else normalBoxColor

            val currentStrokeWidth =
                if (isSelected) 5f * density else 3f * density
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
                return@forEach
            }

            drawRect(
                color = currentBoxColor,
                topLeft = Offset(
                    clampedLeft,
                    clampedTop
                ),
                size = Size(
                    clampedRight - clampedLeft,
                    clampedBottom - clampedTop
                ),
                style = Stroke(
                    width = currentStrokeWidth
                )
            )

            val confidencePercent =
                (detection.confidence * 100).toInt()

            // Developer A - Selected Focus Object Label
            val labelText =
                if (isSelected) {
                    "${detection.label} $confidencePercent% [FOCUS]"
                } else {
                    "${detection.label} $confidencePercent%"
                }

            val textWidth =
                textPaint.measureText(labelText)

            val padding = 6f * density

            val labelHeight =
                textSize + padding * 2f

            val labelWidth =
                textWidth + padding * 2f

            val labelLeft = min(
                clampedLeft,
                max(0f, size.width - labelWidth)
            )

            val labelTop = if (
                clampedTop >= labelHeight
            ) {
                clampedTop - labelHeight
            } else {
                clampedTop
            }

            drawIntoCanvas { canvas ->

                val nativeCanvas = canvas.nativeCanvas

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
