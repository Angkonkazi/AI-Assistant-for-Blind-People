
package com.aiassistant.blindpeople.distance

import com.aiassistant.blindpeople.YOLODetection

/**
 * Converts existing YOLO detections into inputs
 * understood by the distance processor.
 *
 * Known object dimensions are provided explicitly
 * by detection index, never guessed from labels.
 */
class YOLODistanceAdapter {

    private val processor = DetectionDistanceProcessor()

    fun analyze(
        detections: List<YOLODetection>,
        imageWidth: Int,
        imageHeight: Int,
        calibrationSamples: List<CalibrationSample>,
        knownHeightsByIndex: Map<Int, Float> = emptyMap()
    ): List<ObjectDistanceResult> {

        val inputs = detections.mapIndexed { index, detection ->

            val box = detection.boundingBox

            DistanceDetectionInput(
                detectionIndex = index,
                objectName = detection.label,
                confidence = detection.confidence,
                left = box.left,
                top = box.top,
                right = box.right,
                bottom = box.bottom,
                realObjectHeightMeters =
                    knownHeightsByIndex[index]
            )
        }

        return processor.process(
            detections = inputs,
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            calibrationSamples = calibrationSamples
        )
    }
}
