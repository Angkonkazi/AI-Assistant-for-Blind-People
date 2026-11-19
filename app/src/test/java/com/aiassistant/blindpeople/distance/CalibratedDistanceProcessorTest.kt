
package com.aiassistant.blindpeople.distance

import org.junit.Assert.*
import org.junit.Test

class CalibratedDistanceProcessorTest {

    private val processor = CalibratedDistanceProcessor()

    private fun configuration() = CameraConfiguration(
        cameraId = "camera_0",
        lensId = "rear_main",
        imageWidth = 640,
        imageHeight = 480,
        rotationDegrees = 0,
        zoomRatio = 1.0f
    )

    private fun profile() = CameraCalibrationProfile(
        configuration = configuration(),
        focalLengthPixels = 800f,
        calibrationSampleCount = 3
    )

    private fun detection(
        heightMeters: Float? = 1.0f
    ) = DistanceDetectionInput(
        detectionIndex = 0,
        objectName = "reference object",
        confidence = 0.90f,
        left = 100f,
        top = 50f,
        right = 300f,
        bottom = 350f,
        realObjectHeightMeters = heightMeters
    )

    @Test
    fun testMatchingCalibration() {

        assertTrue(processor.registerCalibration(profile()))

        val results = processor.process(
            detections = listOf(detection()),
            currentConfiguration = configuration()
        )

        assertEquals(1, results.size)

        val result = results[0]

        assertEquals(
            CalibrationMatchStatus.MATCHED,
            result.calibrationStatus
        )

        assertNotNull(result.distance.estimatedMeters)

        assertEquals(
            2.6666667f,
            result.distance.estimatedMeters!!,
            0.0001f
        )
    }

    @Test
    fun testNoCalibration() {

        val results = processor.process(
            detections = listOf(detection()),
            currentConfiguration = configuration()
        )

        assertEquals(1, results.size)

        assertEquals(
            CalibrationMatchStatus.NO_PROFILE,
            results[0].calibrationStatus
        )

        assertNull(results[0].distance.estimatedMeters)
    }

    @Test
    fun testDifferentCamera() {

        assertTrue(processor.registerCalibration(profile()))

        val results = processor.process(
            detections = listOf(detection()),
            currentConfiguration = configuration().copy(
                cameraId = "camera_1"
            )
        )

        assertEquals(
            CalibrationMatchStatus.CONFIGURATION_MISMATCH,
            results[0].calibrationStatus
        )

        assertNull(results[0].distance.estimatedMeters)
    }

    @Test
    fun testDifferentZoom() {

        assertTrue(processor.registerCalibration(profile()))

        val results = processor.process(
            detections = listOf(detection()),
            currentConfiguration = configuration().copy(
                zoomRatio = 2.0f
            )
        )

        assertNull(results[0].distance.estimatedMeters)

        assertEquals(
            CalibrationMatchStatus.CONFIGURATION_MISMATCH,
            results[0].calibrationStatus
        )
    }

    @Test
    fun testUnknownObjectHeight() {

        assertTrue(processor.registerCalibration(profile()))

        val results = processor.process(
            detections = listOf(
                detection(heightMeters = null)
            ),
            currentConfiguration = configuration()
        )

        assertEquals(
            CalibrationMatchStatus.MATCHED,
            results[0].calibrationStatus
        )

        assertNull(results[0].distance.estimatedMeters)
        assertFalse(results[0].distance.isCalibrated)
    }

    @Test
    fun testInvalidBoundingBox() {

        assertTrue(processor.registerCalibration(profile()))

        val invalid = detection().copy(
            left = 300f,
            right = 100f
        )

        val results = processor.process(
            detections = listOf(invalid),
            currentConfiguration = configuration()
        )

        assertNull(results[0].metrics)
        assertNull(results[0].distance.estimatedMeters)

        assertEquals(
            "Invalid bounding box",
            results[0].distance.status
        )
    }

    @Test
    fun testClearCalibration() {

        assertTrue(processor.registerCalibration(profile()))

        processor.clearCalibration()

        val results = processor.process(
            detections = listOf(detection()),
            currentConfiguration = configuration()
        )

        assertEquals(
            CalibrationMatchStatus.NO_PROFILE,
            results[0].calibrationStatus
        )

        assertNull(results[0].distance.estimatedMeters)
    }

    @Test
    fun testMultipleDetections() {

        assertTrue(processor.registerCalibration(profile()))

        val first = detection()

        val second = detection(
            heightMeters = null
        ).copy(
            detectionIndex = 1,
            objectName = "chair",
            left = 350f,
            right = 500f
        )

        val results = processor.process(
            detections = listOf(first, second),
            currentConfiguration = configuration()
        )

        assertEquals(2, results.size)

        assertNotNull(
            results[0].distance.estimatedMeters
        )

        assertNull(
            results[1].distance.estimatedMeters
        )

        assertEquals("chair", results[1].objectName)
        assertEquals(1, results[1].detectionIndex)
    }

    @Test
    fun testEmptyDetections() {

        assertTrue(processor.registerCalibration(profile()))

        val results = processor.process(
            detections = emptyList(),
            currentConfiguration = configuration()
        )

        assertTrue(results.isEmpty())
    }

    @Test
    fun testInvalidCameraConfiguration() {

        assertTrue(processor.registerCalibration(profile()))

        val results = processor.process(
            detections = listOf(detection()),
            currentConfiguration = configuration().copy(
                imageWidth = 0
            )
        )

        assertEquals(
            CalibrationMatchStatus.INVALID_CONFIGURATION,
            results[0].calibrationStatus
        )

        assertNull(results[0].distance.estimatedMeters)
    }
}
