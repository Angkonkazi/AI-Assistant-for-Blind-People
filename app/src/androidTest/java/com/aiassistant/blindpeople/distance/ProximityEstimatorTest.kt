
package com.aiassistant.blindpeople.distance

import android.graphics.RectF
import com.aiassistant.blindpeople.YOLODetection
import org.junit.Assert.*
import org.junit.Test
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProximityEstimatorTest {

    private val estimator = ProximityEstimator()

    @Test
    fun testRelativeAreaCalculation() {

        val detection = YOLODetection(
            classId = 56,
            label = "chair",
            confidence = 0.90f,
            boundingBox = RectF(
                100f, 50f, 300f, 350f
            )
        )

        val results = estimator.estimate(
            detections = listOf(detection),
            imageWidth = 640,
            imageHeight = 480
        )

        assertEquals(1, results.size)

        val result = results[0]

        assertEquals(
            0.3125f,
            result.widthRatio,
            0.0001f
        )

        assertEquals(
            0.625f,
            result.heightRatio,
            0.0001f
        )

        assertEquals(
            0.1953125f,
            result.areaRatio,
            0.0001f
        )

        assertEquals(
            ProximityLevel.UNKNOWN,
            result.proximityLevel
        )
    }

    @Test
    fun testInvalidImageDimensions() {

        val results = estimator.estimate(
            detections = emptyList(),
            imageWidth = 0,
            imageHeight = 480
        )

        assertTrue(results.isEmpty())
    }

    @Test
    fun testEmptyDetections() {

        val results = estimator.estimate(
            detections = emptyList(),
            imageWidth = 640,
            imageHeight = 480
        )

        assertTrue(results.isEmpty())
    }
}
