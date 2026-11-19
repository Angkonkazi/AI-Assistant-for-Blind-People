
package com.aiassistant.blindpeople.distance

import org.junit.Assert.*
import org.junit.Test

class DistanceEstimatorTest {

    private val estimator = DistanceEstimator()

    @Test
    fun testCorrectDistanceCalculation() {
        val result = estimator.estimateDistance(
            objectHeightPixels = 400f,
            realObjectHeightMeters = 1.0f,
            focalLengthPixels = 800f
        )

        assertNotNull(result.estimatedMeters)
        assertEquals(2.0f, result.estimatedMeters!!, 0.0001f)
        assertEquals("Experimental estimate", result.status)
    }

    @Test
    fun testMissingFocalLength() {
        val result = estimator.estimateDistance(
            objectHeightPixels = 400f,
            realObjectHeightMeters = 1.0f,
            focalLengthPixels = null
        )

        assertNull(result.estimatedMeters)
        assertFalse(result.isCalibrated)
    }

    @Test
    fun testMissingObjectHeight() {
        val result = estimator.estimateDistance(
            objectHeightPixels = 400f,
            realObjectHeightMeters = null,
            focalLengthPixels = 800f
        )

        assertNull(result.estimatedMeters)
    }

    @Test
    fun testZeroPixelHeight() {
        val result = estimator.estimateDistance(
            objectHeightPixels = 0f,
            realObjectHeightMeters = 1.0f,
            focalLengthPixels = 800f
        )

        assertNull(result.estimatedMeters)
        assertEquals("Invalid measurement", result.status)
    }

    @Test
    fun testNegativeObjectHeight() {
        val result = estimator.estimateDistance(
            objectHeightPixels = 400f,
            realObjectHeightMeters = -1.0f,
            focalLengthPixels = 800f
        )

        assertNull(result.estimatedMeters)
    }

    @Test
    fun testNaNInput() {
        val result = estimator.estimateDistance(
            objectHeightPixels = Float.NaN,
            realObjectHeightMeters = 1.0f,
            focalLengthPixels = 800f
        )

        assertNull(result.estimatedMeters)
    }

    @Test
    fun testInfiniteFocalLength() {
        val result = estimator.estimateDistance(
            objectHeightPixels = 400f,
            realObjectHeightMeters = 1.0f,
            focalLengthPixels = Float.POSITIVE_INFINITY
        )

        assertNull(result.estimatedMeters)
    }

    @Test
    fun testDifferentDistance() {
        val result = estimator.estimateDistance(
            objectHeightPixels = 200f,
            realObjectHeightMeters = 1.5f,
            focalLengthPixels = 800f
        )

        assertNotNull(result.estimatedMeters)
        assertEquals(6.0f, result.estimatedMeters!!, 0.0001f)
    }
}
