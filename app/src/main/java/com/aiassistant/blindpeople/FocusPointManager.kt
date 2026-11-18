
package com.aiassistant.blindpeople

// Describes an object's relationship to the focus point.
enum class FocusRelation {
    CONTAINS_FOCUS,
    NEAR_FOCUS,
    OTHER
}

// Stores the focus analysis for one detected object.
data class FocusResult(
    val detection: YOLODetection,
    val relation: FocusRelation,
    val normalizedDistance: Float,
    val containsFocusPoint: Boolean
)

class FocusPointManager {

    private val focusX = 0.5f
    private val focusY = 0.5f
    private val nearThreshold = 0.10f

    // Task A3.3: Analyze one object
    fun analyzeFocus(
        detection: YOLODetection,
        imageWidth: Int,
        imageHeight: Int
    ): FocusResult {

        require(imageWidth > 0 && imageHeight > 0) {
            "Image dimensions must be positive"
        }

        val box = detection.boundingBox

        // Normalize bounding box coordinates.
        val left = box.left / imageWidth.toFloat()
        val right = box.right / imageWidth.toFloat()
        val top = box.top / imageHeight.toFloat()
        val bottom = box.bottom / imageHeight.toFloat()

        // Check whether focus is inside the box.
        val containsFocus =
            focusX >= left &&
                    focusX <= right &&
                    focusY >= top &&
                    focusY <= bottom

        // Find the nearest point on the box.
        val nearestX = focusX.coerceIn(left, right)
        val nearestY = focusY.coerceIn(top, bottom)

        val dx = focusX - nearestX
        val dy = focusY - nearestY

        val distance = kotlin.math.sqrt(
            dx * dx + dy * dy
        )

        val relation = when {
            containsFocus ->
                FocusRelation.CONTAINS_FOCUS

            distance <= nearThreshold ->
                FocusRelation.NEAR_FOCUS

            else ->
                FocusRelation.OTHER
        }

        return FocusResult(
            detection = detection,
            relation = relation,
            normalizedDistance = distance,
            containsFocusPoint = containsFocus
        )
    }

    // Task A3.6: Select one focus object from multiple detections
    fun selectFocusObject(
        detections: List<YOLODetection>,
        imageWidth: Int,
        imageHeight: Int
    ): FocusResult? {

        require(imageWidth > 0 && imageHeight > 0) {
            "Image dimensions must be positive"
        }

        if (detections.isEmpty()) {
            return null
        }

        val results = detections.map { detection ->
            analyzeFocus(
                detection = detection,
                imageWidth = imageWidth,
                imageHeight = imageHeight
            )
        }

        // First priority: objects containing focus point.
        val overlapping = results.filter {
            it.containsFocusPoint
        }

        if (overlapping.isNotEmpty()) {
            return overlapping.sortedWith(
                compareByDescending<FocusResult> {
                    it.detection.confidence
                }.thenBy {
                    val box = it.detection.boundingBox
                    box.width() * box.height()
                }.thenBy {
                    it.detection.classId
                }
            ).first()
        }

        // Second priority: closest bounding box to focus point.
        return results.sortedWith(
            compareBy<FocusResult> {
                it.normalizedDistance
            }.thenByDescending {
                it.detection.confidence
            }.thenBy {
                it.detection.classId
            }
        ).first()
    }
}
