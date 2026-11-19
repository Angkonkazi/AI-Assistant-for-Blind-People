
package com.aiassistant.blindpeople.distance

import com.aiassistant.blindpeople.YOLODetection

/**
 * Connects the existing YOLO detection model
 * with the persistent calibration workflow.
 *
 * Does not modify CameraScreen or YOLODetector.
 */
class YOLOCalibratedDistanceAdapter(
    private val workflow: PersistentCalibrationWorkflow
) {

    private val mapper = DetectionInputMapper()

    /**
     * Save and activate an experimental
     * camera calibration profile.
     */
    fun saveCalibration(
        profile: CameraCalibrationProfile
    ): Boolean {

        return workflow.saveAndActivate(profile)
    }

    /**
     * Restore calibration from persistent storage.
     */
    fun restoreCalibration(): Boolean {

        return workflow.restoreCalibration()
    }

    /**
     * Clear active and saved calibration.
     */
    fun clearCalibration(): Boolean {

        return workflow.clearCalibration()
    }

    /**
     * Analyze one batch of YOLO detections.
     *
     * Preconditions:
     * 1. Bounding boxes must be in image pixels.
     * 2. Coordinates must match the upright
     *    analysis image.
     * 3. Current configuration must represent
     *    that exact analysis image.
     * 4. Known heights must refer to physically
     *    measured objects in this detection batch.
     */
    fun analyze(
        detections: List<YOLODetection>,
        currentConfiguration: CameraConfiguration,
        knownHeightsByIndex: Map<Int, Float> = emptyMap()
    ): List<ManagedDistanceResult> {

        val rawDetections = detections.mapIndexed {
                index, detection ->

            val box = detection.boundingBox

            RawDetectionInput(
                index = index,
                label = detection.label,
                confidence = detection.confidence,
                left = box.left,
                top = box.top,
                right = box.right,
                bottom = box.bottom
            )
        }

        val distanceInputs = mapper.convert(
            detections = rawDetections,
            knownHeightsByIndex = knownHeightsByIndex
        )

        return workflow.analyze(
            detections = distanceInputs,
            currentConfiguration = currentConfiguration
        )
    }
}
