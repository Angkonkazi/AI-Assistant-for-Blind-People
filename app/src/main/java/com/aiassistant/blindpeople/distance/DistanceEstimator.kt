
package com.aiassistant.blindpeople.distance

/**
 * Result of experimental physical-distance estimation.
 *
 * A null distance means estimation is unavailable.
 */
data class DistanceEstimate(
    val estimatedMeters: Float?,
    val method: String,
    val isCalibrated: Boolean,
    val status: String
)

/**
 * Experimental pinhole-camera distance estimator.
 *
 * Requirements:
 * 1. Known real-world object height.
 * 2. Calibrated focal length in image pixels.
 * 3. Corresponding object height in image pixels.
 *
 * Assumes the measured dimension is suitable for
 * the pinhole model and camera calibration.
 */
class DistanceEstimator {

    fun estimateDistance(
        objectHeightPixels: Float,
        realObjectHeightMeters: Float?,
        focalLengthPixels: Float?
    ): DistanceEstimate {

        val method = "CALIBRATED_PINHOLE"

        // Check whether calibration data is available.
        if (
            realObjectHeightMeters == null ||
            focalLengthPixels == null
        ) {
            return DistanceEstimate(
                estimatedMeters = null,
                method = method,
                isCalibrated = false,
                status = "Calibration data unavailable"
            )
        }

        // Validate all supplied measurements.
        if (
            !objectHeightPixels.isFinite() ||
            !realObjectHeightMeters.isFinite() ||
            !focalLengthPixels.isFinite() ||
            objectHeightPixels <= 0f ||
            realObjectHeightMeters <= 0f ||
            focalLengthPixels <= 0f
        ) {
            return DistanceEstimate(
                estimatedMeters = null,
                method = method,
                isCalibrated = false,
                status = "Invalid measurement"
            )
        }

        // Pinhole camera formula:
        // Distance = focalLength * realHeight / pixelHeight
        val distance =
            (focalLengthPixels.toDouble() *
                    realObjectHeightMeters.toDouble()) /
                    objectHeightPixels.toDouble()

        if (
            !distance.isFinite() ||
            distance <= 0.0 ||
            distance > Float.MAX_VALUE
        ) {
            return DistanceEstimate(
                estimatedMeters = null,
                method = method,
                isCalibrated = false,
                status = "Distance calculation failed"
            )
        }

        return DistanceEstimate(
            estimatedMeters = distance.toFloat(),
            method = method,
            isCalibrated = true,
            status = "Experimental estimate"
        )
    }
}
