
package com.aiassistant.blindpeople.distance

import org.junit.Assert.*
import org.junit.Test

class AutomaticDistanceEstimatorTest {

    private val estimator = DistanceEstimator()

    private val focalLength = 744.48535f

    @Test
    fun bottleAtOneMeter() {

        val result = estimator.estimateDistance(
            objectHeightPixels = 186.12134f,
            realObjectHeightMeters = 0.25f,
            focalLengthPixels = focalLength
        )

        assertNotNull(result.estimatedMeters)

        assertEquals(
            1.0f,
            result.estimatedMeters!!,
            0.001f
        )
    }

    @Test
    fun bottleAtTwoMeters() {

        val result = estimator.estimateDistance(
            objectHeightPixels = 93.06067f,
            realObjectHeightMeters = 0.25f,
            focalLengthPixels = focalLength
        )

        assertEquals(
            2.0f,
            result.estimatedMeters!!,
            0.001f
        )
    }

    @Test
    fun bottleAtHalfMeter() {

        val result = estimator.estimateDistance(
            objectHeightPixels = 372.24268f,
            realObjectHeightMeters = 0.25f,
            focalLengthPixels = focalLength
        )

        assertEquals(
            0.5f,
            result.estimatedMeters!!,
            0.001f
        )
    }

    @Test
    fun laptopWidthBasedCalculation() {

        val reference =
            ObjectSizeDatabase.findReference("laptop")!!

        assertEquals(
            ReferenceDimension.WIDTH,
            reference.dimension
        )

        val result = estimator.estimateDistance(
            objectHeightPixels = 260.5699f,
            realObjectHeightMeters = reference.sizeMeters,
            focalLengthPixels = focalLength
        )

        assertEquals(
            1.0f,
            result.estimatedMeters!!,
            0.001f
        )
    }

    @Test
    fun chairHeightReference() {

        val reference =
            ObjectSizeDatabase.findReference("chair")

        assertNotNull(reference)

        assertEquals(
            ReferenceDimension.HEIGHT,
            reference!!.dimension
        )

        assertEquals(
            0.85f,
            reference.sizeMeters,
            0.001f
        )
    }

    @Test
    fun largerBoxProducesSmallerDistance() {

        val smallBox = estimator.estimateDistance(
            100f,
            0.25f,
            focalLength
        )

        val largeBox = estimator.estimateDistance(
            200f,
            0.25f,
            focalLength
        )

        assertTrue(
            smallBox.estimatedMeters!! >
                    largeBox.estimatedMeters!!
        )
    }

    @Test
    fun doublingBoxSizeHalvesDistance() {

        val first = estimator.estimateDistance(
            100f,
            0.25f,
            focalLength
        )

        val second = estimator.estimateDistance(
            200f,
            0.25f,
            focalLength
        )

        assertEquals(
            first.estimatedMeters!! / 2f,
            second.estimatedMeters!!,
            0.001f
        )
    }

    @Test
    fun unsupportedObjectReturnsNoReference() {

        assertNull(
            ObjectSizeDatabase.findReference("unknown_object")
        )
    }

    @Test
    fun referenceLookupIgnoresCaseAndWhitespace() {

        assertNotNull(
            ObjectSizeDatabase.findReference(" BOTTLE ")
        )
    }

    @Test
    fun zeroBoundingBoxReturnsUnknown() {

        val result = estimator.estimateDistance(
            0f,
            0.25f,
            focalLength
        )

        assertNull(result.estimatedMeters)
    }

    @Test
    fun negativeBoundingBoxReturnsUnknown() {

        val result = estimator.estimateDistance(
            -100f,
            0.25f,
            focalLength
        )

        assertNull(result.estimatedMeters)
    }

    @Test
    fun missingFocalLengthReturnsUnknown() {

        val result = estimator.estimateDistance(
            100f,
            0.25f,
            null
        )

        assertNull(result.estimatedMeters)
    }

    @Test
    fun missingObjectSizeReturnsUnknown() {

        val result = estimator.estimateDistance(
            100f,
            null,
            focalLength
        )

        assertNull(result.estimatedMeters)
    }

    @Test
    fun nanMeasurementReturnsUnknown() {

        val result = estimator.estimateDistance(
            Float.NaN,
            0.25f,
            focalLength
        )

        assertNull(result.estimatedMeters)
    }

    @Test
    fun infiniteMeasurementReturnsUnknown() {

        val result = estimator.estimateDistance(
            Float.POSITIVE_INFINITY,
            0.25f,
            focalLength
        )

        assertNull(result.estimatedMeters)
    }
}
