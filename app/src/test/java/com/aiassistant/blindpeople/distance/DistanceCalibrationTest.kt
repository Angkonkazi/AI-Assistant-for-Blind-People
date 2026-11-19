
package com.aiassistant.blindpeople.distance

import org.junit.Assert.*
import org.junit.Test

class DistanceCalibrationTest {

    private val calibration = DistanceCalibration()

    @Test
    fun testCorrectFocalLength() {
        val sample = CalibrationSample(
            actualDistanceMeters = 2.0f,
            realObjectHeightMeters = 0.5f,
            objectHeightPixels = 200f
        )

        val result = calibration.calculateFocalLength(sample)

        assertTrue(result.isValid)
        assertNotNull(result.focalLengthPixels)
        assertEquals(
            800f,
            result.focalLengthPixels!!,
            0.0001f
        )
    }

    @Test
    fun testZeroDistance() {
        val sample = CalibrationSample(
            actualDistanceMeters = 0f,
            realObjectHeightMeters = 0.5f,
            objectHeightPixels = 200f
        )

        val result = calibration.calculateFocalLength(sample)

        assertFalse(result.isValid)
        assertNull(result.focalLengthPixels)
    }

    @Test
    fun testNegativeHeight() {
        val sample = CalibrationSample(
            actualDistanceMeters = 2f,
            realObjectHeightMeters = -0.5f,
            objectHeightPixels = 200f
        )

        val result = calibration.calculateFocalLength(sample)

        assertFalse(result.isValid)
    }

    @Test
    fun testNaNInput() {
        val sample = CalibrationSample(
            actualDistanceMeters = Float.NaN,
            realObjectHeightMeters = 0.5f,
            objectHeightPixels = 200f
        )

        val result = calibration.calculateFocalLength(sample)

        assertNull(result.focalLengthPixels)
    }

    @Test
    fun testEmptySamples() {
        val result = calibration.calculateFromSamples(
            emptyList()
        )

        assertFalse(result.isValid)
        assertEquals(0, result.sampleCount)
        assertNull(result.focalLengthPixels)
    }

    @Test
    fun testMultipleSamplesMedian() {
        val samples = listOf(
            CalibrationSample(2f, 0.5f, 200f),
            CalibrationSample(2f, 0.5f, 210f),
            CalibrationSample(2f, 0.5f, 190f)
        )

        val result = calibration.calculateFromSamples(samples)

        assertTrue(result.isValid)
        assertEquals(3, result.sampleCount)
        assertEquals(
            800f,
            result.focalLengthPixels!!,
            0.0001f
        )
    }

    @Test
    fun testEvenNumberOfSamples() {
        val samples = listOf(
            CalibrationSample(2f, 0.5f, 200f),
            CalibrationSample(2f, 0.5f, 220f)
        )

        val result = calibration.calculateFromSamples(samples)

        assertEquals(
            840f,
            result.focalLengthPixels!!,
            0.0001f
        )
    }

    @Test
    fun testInvalidSampleIsExcluded() {
        val samples = listOf(
            CalibrationSample(2f, 0.5f, 200f),
            CalibrationSample(-1f, 0.5f, 200f),
            CalibrationSample(2f, 0.5f, 210f)
        )

        val result = calibration.calculateFromSamples(samples)

        assertTrue(result.isValid)
        assertEquals(2, result.sampleCount)
        assertEquals(
            820f,
            result.focalLengthPixels!!,
            0.0001f
        )
    }
}
