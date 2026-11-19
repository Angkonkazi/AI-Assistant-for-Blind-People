
package com.aiassistant.blindpeople.distance

import org.junit.Assert.*
import org.junit.Test

class DetectionDistanceProcessorTest {

    private val processor = DetectionDistanceProcessor()

    private val samples = listOf(
        CalibrationSample(
            actualDistanceMeters = 2f,
            realObjectHeightMeters = 0.5f,
            objectHeightPixels = 200f
        )
    )

    @Test
    fun testKnownObjectDistance() {

        val detection = DistanceDetectionInput(
            detectionIndex = 0,
            objectName = "reference object",
            confidence = 0.9f,
            left = 100f,
            top = 50f,
            right = 300f,
            bottom = 350f,
            realObjectHeightMeters = 1f
        )

        val results = processor.process(
            detections = listOf(detection),
            imageWidth = 640,
            imageHeight = 480,
            calibrationSamples = samples
        )

        assertEquals(1, results.size)
        assertEquals(0, results[0].detectionIndex)

        val distance =
            results[0].pipelineResult.distance.estimatedMeters

        assertNotNull(distance)

        assertEquals(
            2.6666667f,
            distance!!,
            0.0001f
        )
    }

    @Test
    fun testUnknownObjectHeight() {

        val detection = DistanceDetectionInput(
            detectionIndex = 1,
            objectName = "chair",
            confidence = 0.85f,
            left = 100f,
            top = 50f,
            right = 300f,
            bottom = 350f
        )

        val results = processor.process(
            detections = listOf(detection),
            imageWidth = 640,
            imageHeight = 480,
            calibrationSamples = samples
        )

        assertEquals(1, results.size)

        assertNull(
            results[0].pipelineResult.distance.estimatedMeters
        )
    }

    @Test
    fun testMultipleObjects() {

        val detections = listOf(
            DistanceDetectionInput(
                0, "reference object", 0.9f,
                100f, 50f, 300f, 350f, 1f
            ),
            DistanceDetectionInput(
                1, "chair", 0.8f,
                350f, 100f, 500f, 400f
            )
        )

        val results = processor.process(
            detections = detections,
            imageWidth = 640,
            imageHeight = 480,
            calibrationSamples = samples
        )

        assertEquals(2, results.size)

        assertNotNull(
            results[0].pipelineResult.distance.estimatedMeters
        )

        assertNull(
            results[1].pipelineResult.distance.estimatedMeters
        )

        assertEquals("chair", results[1].objectName)
        assertEquals(1, results[1].detectionIndex)
    }

    @Test
    fun testEmptyDetections() {

        val results = processor.process(
            detections = emptyList(),
            imageWidth = 640,
            imageHeight = 480,
            calibrationSamples = samples
        )

        assertTrue(results.isEmpty())
    }

    @Test
    fun testInvalidBoundingBox() {

        val detection = DistanceDetectionInput(
            detectionIndex = 0,
            objectName = "reference object",
            confidence = 0.9f,
            left = 300f,
            top = 50f,
            right = 100f,
            bottom = 350f,
            realObjectHeightMeters = 1f
        )

        val results = processor.process(
            detections = listOf(detection),
            imageWidth = 640,
            imageHeight = 480,
            calibrationSamples = samples
        )

        assertEquals(1, results.size)

        assertNull(
            results[0].pipelineResult.distance.estimatedMeters
        )

        assertNull(
            results[0].pipelineResult.metrics
        )
    }
}
