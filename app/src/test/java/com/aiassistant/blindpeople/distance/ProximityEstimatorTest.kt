
package com.aiassistant.blindpeople.distance

import org.junit.Assert.assertEquals
import org.junit.Test

class ProximityEstimatorTest {

    private val estimator = ProximityEstimator()

    @Test
    fun smallBoundingBoxReturnsFar() {
        assertEquals(
            ProximityLevel.FAR,
            estimator.classifyProximity(0.04f)
        )
    }

    @Test
    fun mediumBoundingBoxReturnsMedium() {
        assertEquals(
            ProximityLevel.MEDIUM,
            estimator.classifyProximity(0.20f)
        )
    }

    @Test
    fun largeBoundingBoxReturnsNear() {
        assertEquals(
            ProximityLevel.NEAR,
            estimator.classifyProximity(0.56f)
        )
    }

    @Test
    fun mediumLowerBoundary() {
        assertEquals(
            ProximityLevel.MEDIUM,
            estimator.classifyProximity(0.10f)
        )
    }

    @Test
    fun nearLowerBoundary() {
        assertEquals(
            ProximityLevel.NEAR,
            estimator.classifyProximity(0.30f)
        )
    }

    @Test
    fun justBelowMediumBoundary() {
        assertEquals(
            ProximityLevel.FAR,
            estimator.classifyProximity(0.0999f)
        )
    }

    @Test
    fun justBelowNearBoundary() {
        assertEquals(
            ProximityLevel.MEDIUM,
            estimator.classifyProximity(0.2999f)
        )
    }

    @Test
    fun fullFrameBoundingBoxReturnsNear() {
        assertEquals(
            ProximityLevel.NEAR,
            estimator.classifyProximity(1.0f)
        )
    }

    @Test
    fun zeroAreaReturnsUnknown() {
        assertEquals(
            ProximityLevel.UNKNOWN,
            estimator.classifyProximity(0f)
        )
    }

    @Test
    fun negativeAreaReturnsUnknown() {
        assertEquals(
            ProximityLevel.UNKNOWN,
            estimator.classifyProximity(-0.10f)
        )
    }

    @Test
    fun oversizedAreaReturnsUnknown() {
        assertEquals(
            ProximityLevel.UNKNOWN,
            estimator.classifyProximity(1.20f)
        )
    }

    @Test
    fun nanAreaReturnsUnknown() {
        assertEquals(
            ProximityLevel.UNKNOWN,
            estimator.classifyProximity(Float.NaN)
        )
    }

    @Test
    fun infiniteAreaReturnsUnknown() {
        assertEquals(
            ProximityLevel.UNKNOWN,
            estimator.classifyProximity(
                Float.POSITIVE_INFINITY
            )
        )
    }
}
