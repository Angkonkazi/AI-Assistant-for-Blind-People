
package com.aiassistant.blindpeople.distance

/**
 * Distance result for one object, with the
 * camera-calibration lookup status attached.
 */
data class ManagedDistanceResult(
    val detectionIndex: Int,
    val objectName: String,
    val confidence: Float,
    val metrics: BoundingBoxMetrics?,
    val distance: DistanceEstimate,
    val calibrationStatus: CalibrationMatchStatus
)

/**
 * Combines camera-configuration validation,
 * bounding-box calculations, and experimental
 * physical-distance estimation.
 *
 * This class does not use Android framework APIs.
 */
class CalibratedDistanceProcessor(
    private val calibrationManager:
    CameraCalibrationManager = CameraCalibrationManager()
) {

    private val calculator = BoundingBoxCalculator()
    private val distanceEstimator = DistanceEstimator()

    /**
     * Register an existing experimental
     * calibration profile.
     */
    fun registerCalibration(
        profile: CameraCalibrationProfile
    ): Boolean {
        return calibrationManager.registerProfile(profile)
    }

    /**
     * Remove the currently registered profile.
     */
    fun clearCalibration() {
        calibrationManager.clearProfile()
    }

    /**
     * Analyze a batch of detections from one frame.
     *
     * All detection coordinates must refer to the
     * upright image described by currentConfiguration.
     *
     * realObjectHeightMeters is required for a
     * numerical distance estimate.
     */
    fun process(
        detections: List<DistanceDetectionInput>,
        currentConfiguration: CameraConfiguration
    ): List<ManagedDistanceResult> {

        // Check the camera configuration once per batch.
        val lookup = calibrationManager.findCalibration(
            current = currentConfiguration
        )

        val imageWidth = currentConfiguration.imageWidth
        val imageHeight = currentConfiguration.imageHeight

        return detections.map { detection ->

            val metrics = calculator.calculate(
                left = detection.left,
                top = detection.top,
                right = detection.right,
                bottom = detection.bottom,
                imageWidth = imageWidth,
                imageHeight = imageHeight
            )

            val distanceResult = when {

                // Invalid target geometry.
                metrics == null -> {
                    unavailable("Invalid bounding box")
                }

                // No matching calibration.
                lookup.status != CalibrationMatchStatus.MATCHED ||
                        lookup.focalLengthPixels == null -> {
                    unavailable(
                        "Calibration unavailable: ${lookup.status}"
                    )
                }

                // All required inputs are now available.
                else -> {
                    distanceEstimator.estimateDistance(
                        objectHeightPixels = metrics.height,
                        realObjectHeightMeters =
                            detection.realObjectHeightMeters,
                        focalLengthPixels =
                            lookup.focalLengthPixels
                    )
                }
            }

            ManagedDistanceResult(
                detectionIndex = detection.detectionIndex,
                objectName = detection.objectName,
                confidence = detection.confidence,
                metrics = metrics,
                distance = distanceResult,
                calibrationStatus = lookup.status
            )
        }
    }

    private fun unavailable(
        reason: String
    ): DistanceEstimate {

        return DistanceEstimate(
            estimatedMeters = null,
            method = "UNAVAILABLE",
            isCalibrated = false,
            status = reason
        )
    }
}
