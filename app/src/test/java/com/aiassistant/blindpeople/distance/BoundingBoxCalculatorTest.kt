
package com.aiassistant.blindpeople.distance

import org.junit.Assert.*
import org.junit.Test

class BoundingBoxCalculatorTest {

    private val calculator = BoundingBoxCalculator()

    @Test
    fun testCorrectAreaCalculation() {

        val result = calculator.calculate(
            left = 100f,
            top = 50f,
            right = 300f,
            bottom = 350f,
            imageWidth = 640,
            imageHeight = 480
        )

        assertNotNull(result)

        val metrics = result!!

        assertEquals(200f, metrics.width, 0.0001f)
        assertEquals(300f, metrics.height, 0.0001f)
        assertEquals(0.3125f, metrics.widthRatio, 0.0001f)
        assertEquals(0.625f, metrics.heightRatio, 0.0001f)
        assertEquals(0.1953125f, metrics.areaRatio, 0.0001f)
    }

    @Test
    fun testInvalidImageDimensions() {

        val result = calculator.calculate(
            left = 10f,
            top = 10f,
            right = 100f,
            bottom = 100f,
            imageWidth = 0,
            imageHeight = 480
        )

        assertNull(result)
    }

    @Test
    fun testInvalidBoundingBox() {

        val result = calculator.calculate(
            left = 300f,
            top = 100f,
            right = 100f,
            bottom = 200f,
            imageWidth = 640,
            imageHeight = 480
        )

        assertNull(result)
    }

    @Test
    fun testOutOfBoundsBox() {

        val result = calculator.calculate(
            left = -10f,
            top = 20f,
            right = 200f,
            bottom = 300f,
            imageWidth = 640,
            imageHeight = 480
        )

        assertNull(result)
    }

    @Test
    fun testNonFiniteCoordinates() {

        val result = calculator.calculate(
            left = Float.NaN,
            top = 20f,
            right = 200f,
            bottom = 300f,
            imageWidth = 640,
            imageHeight = 480
        )

        assertNull(result)
    }
}
