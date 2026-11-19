
package com.aiassistant.blindpeople.distance

import org.junit.Assert.*
import org.junit.Test

class DistancePipelineTest {

    private val pipeline = DistancePipeline()

    private val samples = listOf(
        CalibrationSample(
            actualDistanceMeters = 2.0f,
            realObjectHeightMeters = 0.5f,
            objectHeightPixels = 200f
        ),
        CalibrationSample(
            actualDistanceMeters = 2.0f,
            realObjectHeightMeters = 0.5f,
            objectHeightPixels = 200f
        )
    )

    private fun runPipeline(
        calibrationSamples: List<CalibrationSample> = samples,
        targetHeightMeters: Float? = 1.0f,
        left: Float = 100f,
        top: Float = 50f,
        right: Float = 300f,
        bottom: Float = 350f
    ): DistancePipelineResult {

        return pipeline.analyze(
            left = left,
            top = top,
            right = right,
            bottom = bottom,
            imageWidth = 640,
            imageHeight = 480,
            realObjectHeightMeters = targetHeightMeters,
            calibrationSamples = calibrationSamples
        )
    }

    @Test
    fun testCompleteDistancePipeline() {

        val result = runPipeline()

        assertNotNull(result.metrics)
        assertNotNull(result.distance.estimatedMeters)

        // Focal length = 2 * 200 / 0.5 = 800 px
        // Distance = 800 * 1.0 / 300
        assertEquals(
            2.6666667f,
            result.distance.estimatedMeters!!,
            0.0001f
        )

        assertEquals(2, result.calibrationSampleCount)

        assertEquals(
            0.1953125f,
            result.metrics!!.areaRatio,
            0.0001f
        )
    }

    @Test
    fun testMissingCalibration() {

        val result = runPipeline(
            calibrationSamples = emptyList()
        )

        assertNull(result.distance.estimatedMeters)
        assertFalse(result.distance.isCalibrated)
        assertNotNull(result.metrics)
    }

    @Test
    fun testMissingTargetHeight() {

        val result = runPipeline(
            targetHeightMeters = null
        )

        assertNull(result.distance.estimatedMeters)
        assertFalse(result.distance.isCalibrated)
    }

    @Test
    fun testInvalidBoundingBox() {

        val result = runPipeline(
            left = 300f,
            right = 100f
        )

        assertNull(result.metrics)
        assertNull(result.distance.estimatedMeters)
    }

    @Test
    fun testInvalidCalibrationSample() {

        val invalidSamples = listOf(
            CalibrationSample(2f, 0.5f, 200f),
            CalibrationSample(-1f, 0.5f, 200f)
        )

        val result = runPipeline(
            calibrationSamples = invalidSamples
        )

        assertNull(result.distance.estimatedMeters)
        assertFalse(result.distance.isCalibrated)
    }

    @Test
    fun testZeroTargetHeight() {

        val result = runPipeline(
            targetHeightMeters = 0f
        )

        assertNull(result.distance.estimatedMeters)
    }

    @Test
    fun testNaNBoundingBox() {

        val result = runPipeline(
            left = Float.NaN
        )

        assertNull(result.metrics)
        assertNull(result.distance.estimatedMeters)
    }
}
