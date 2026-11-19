
package com.aiassistant.blindpeople.distance

/**
 * Result containing bounding-box measurements
 * and an experimental physical-distance estimate.
 */
data class DistancePipelineResult(
    val metrics: BoundingBoxMetrics?,
    val distance: DistanceEstimate,
    val calibrationSampleCount: Int
)

/**
 * Connects the existing calculator, calibration,
 * and distance-estimation classes.
 *
 * Does not depend on Android framework classes.
 */
class DistancePipeline {

    private val calculator = BoundingBoxCalculator()
    private val calibration = DistanceCalibration()
    private val estimator = DistanceEstimator()

    fun analyze(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        imageWidth: Int,
        imageHeight: Int,
        realObjectHeightMeters: Float?,
        calibrationSamples: List<CalibrationSample>
    ): DistancePipelineResult {

        // Step 1: Calculate target bounding-box metrics.
        val metrics = calculator.calculate(
            left = left,
            top = top,
            right = right,
            bottom = bottom,
            imageWidth = imageWidth,
            imageHeight = imageHeight
        )

        if (metrics == null) {
            return unavailable(
                status = "Invalid target bounding box"
            )
        }

        // Step 2: Require calibration samples.
        if (calibrationSamples.isEmpty()) {
            return unavailable(
                status = "Calibration unavailable",
                metrics = metrics
            )
        }

        // Step 3: Calculate experimental focal length.
        val calibrationResult =
            calibration.calculateFromSamples(
                calibrationSamples
            )

        if (
            !calibrationResult.isValid ||
            calibrationResult.focalLengthPixels == null
        ) {
            return unavailable(
                status = "Calibration calculation failed",
                metrics = metrics
            )
        }

        // Reject partially invalid calibration batches.
        if (
            calibrationResult.sampleCount !=
            calibrationSamples.size
        ) {
            return unavailable(
                status = "Invalid calibration sample present",
                metrics = metrics
            )
        }

        // Step 4: Estimate target distance.
        val distanceResult = estimator.estimateDistance(
            objectHeightPixels = metrics.height,
            realObjectHeightMeters = realObjectHeightMeters,
            focalLengthPixels =
                calibrationResult.focalLengthPixels
        )

        return DistancePipelineResult(
            metrics = metrics,
            distance = distanceResult,
            calibrationSampleCount =
                calibrationResult.sampleCount
        )
    }

    private fun unavailable(
        status: String,
        metrics: BoundingBoxMetrics? = null
    ): DistancePipelineResult {

        return DistancePipelineResult(
            metrics = metrics,
            distance = DistanceEstimate(
                estimatedMeters = null,
                method = "UNAVAILABLE",
                isCalibrated = false,
                status = status
            ),
            calibrationSampleCount = 0
        )
    }
}
