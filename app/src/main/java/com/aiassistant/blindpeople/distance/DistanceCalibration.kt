
package com.aiassistant.blindpeople.distance

/**
 * One controlled reference measurement.
 *
 * All distances/heights must be in meters.
 * Pixel height must correspond to the same
 * physical dimension as realObjectHeightMeters.
 */
data class CalibrationSample(
    val actualDistanceMeters: Float,
    val realObjectHeightMeters: Float,
    val objectHeightPixels: Float
)

/**
 * Result of experimental focal-length calibration.
 */
data class CalibrationResult(
    val focalLengthPixels: Float?,
    val sampleCount: Int,
    val isValid: Boolean,
    val status: String
)

/**
 * Pure Kotlin camera-calibration calculator.
 *
 * The mathematical model assumes:
 * - A pinhole-camera approximation.
 * - A reference object of known height.
 * - The reference object's measured height
 *   is suitable for perspective geometry.
 * - A fixed camera/lens/resolution setup.
 *
 * This does not independently verify
 * the calibration measurements.
 */
class DistanceCalibration {

    fun calculateFocalLength(
        sample: CalibrationSample
    ): CalibrationResult {

        val distance = sample.actualDistanceMeters
        val realHeight = sample.realObjectHeightMeters
        val pixelHeight = sample.objectHeightPixels

        if (
            !distance.isFinite() ||
            !realHeight.isFinite() ||
            !pixelHeight.isFinite() ||
            distance <= 0f ||
            realHeight <= 0f ||
            pixelHeight <= 0f
        ) {
            return CalibrationResult(
                focalLengthPixels = null,
                sampleCount = 0,
                isValid = false,
                status = "Invalid calibration measurement"
            )
        }

        // F = D * P / H
        val focalLength =
            (distance.toDouble() *
                    pixelHeight.toDouble()) /
                    realHeight.toDouble()

        if (
            !focalLength.isFinite() ||
            focalLength <= 0.0 ||
            focalLength > Float.MAX_VALUE
        ) {
            return CalibrationResult(
                focalLengthPixels = null,
                sampleCount = 0,
                isValid = false,
                status = "Focal length calculation failed"
            )
        }

        return CalibrationResult(
            focalLengthPixels = focalLength.toFloat(),
            sampleCount = 1,
            isValid = true,
            status = "Experimental calibration calculated"
        )
    }

    /**
     * Calculates a representative focal length
     * from multiple valid reference samples.
     *
     * All samples must be from the same camera
     * configuration and compatible geometry.
     */
    fun calculateFromSamples(
        samples: List<CalibrationSample>
    ): CalibrationResult {

        if (samples.isEmpty()) {
            return CalibrationResult(
                focalLengthPixels = null,
                sampleCount = 0,
                isValid = false,
                status = "No calibration samples"
            )
        }

        val focalLengths = samples.mapNotNull { sample ->
            val result = calculateFocalLength(sample)
            result.focalLengthPixels
        }

        if (focalLengths.isEmpty()) {
            return CalibrationResult(
                focalLengthPixels = null,
                sampleCount = 0,
                isValid = false,
                status = "No valid calibration samples"
            )
        }

        // Median reduces the influence of
        // unusually high or low estimates.
        val sorted = focalLengths
            .map { it.toDouble() }
            .sorted()

        val middle = sorted.size / 2

        val median = if (sorted.size % 2 == 1) {
            sorted[middle]
        } else {
            (sorted[middle - 1] + sorted[middle]) / 2.0
        }

        if (
            !median.isFinite() ||
            median <= 0.0 ||
            median > Float.MAX_VALUE
        ) {
            return CalibrationResult(
                focalLengthPixels = null,
                sampleCount = 0,
                isValid = false,
                status = "Combined calibration failed"
            )
        }

        return CalibrationResult(
            focalLengthPixels = median.toFloat(),
            sampleCount = sorted.size,
            isValid = true,
            status = "Experimental calibration calculated"
        )
    }
}
