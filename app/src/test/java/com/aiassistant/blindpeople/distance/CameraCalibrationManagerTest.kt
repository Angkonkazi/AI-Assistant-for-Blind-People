
package com.aiassistant.blindpeople.distance

import org.junit.Assert.*
import org.junit.Test

class CameraCalibrationManagerTest {

    private val manager = CameraCalibrationManager()

    private fun testConfiguration() = CameraConfiguration(
        cameraId = "camera_0",
        lensId = "rear_main",
        imageWidth = 640,
        imageHeight = 480,
        rotationDegrees = 0,
        zoomRatio = 1.0f
    )

    private fun testProfile() = CameraCalibrationProfile(
        configuration = testConfiguration(),
        focalLengthPixels = 800f,
        calibrationSampleCount = 3
    )

    @Test
    fun testMatchingConfiguration() {
        assertTrue(manager.registerProfile(testProfile()))

        val result = manager.findCalibration(
            testConfiguration()
        )

        assertEquals(
            CalibrationMatchStatus.MATCHED,
            result.status
        )

        assertEquals(
            800f,
            result.focalLengthPixels!!,
            0.0001f
        )
    }

    @Test
    fun testNoProfile() {
        val result = manager.findCalibration(
            testConfiguration()
        )

        assertEquals(
            CalibrationMatchStatus.NO_PROFILE,
            result.status
        )

        assertNull(result.focalLengthPixels)
    }

    @Test
    fun testDifferentCamera() {
        manager.registerProfile(testProfile())

        val result = manager.findCalibration(
            testConfiguration().copy(
                cameraId = "camera_1"
            )
        )

        assertEquals(
            CalibrationMatchStatus.CONFIGURATION_MISMATCH,
            result.status
        )

        assertNull(result.focalLengthPixels)
    }

    @Test
    fun testDifferentImageResolution() {
        manager.registerProfile(testProfile())

        val result = manager.findCalibration(
            testConfiguration().copy(
                imageWidth = 1280,
                imageHeight = 720
            )
        )

        assertNull(result.focalLengthPixels)
    }

    @Test
    fun testDifferentZoomRatio() {
        manager.registerProfile(testProfile())

        val result = manager.findCalibration(
            testConfiguration().copy(
                zoomRatio = 2.0f
            )
        )

        assertEquals(
            CalibrationMatchStatus.CONFIGURATION_MISMATCH,
            result.status
        )
    }

    @Test
    fun testInvalidImageDimensions() {
        val invalid = testConfiguration().copy(
            imageWidth = 0
        )

        val result = manager.findCalibration(invalid)

        assertEquals(
            CalibrationMatchStatus.INVALID_CONFIGURATION,
            result.status
        )
    }

    @Test
    fun testInvalidFocalLength() {
        val invalidProfile = testProfile().copy(
            focalLengthPixels = -800f
        )

        assertFalse(
            manager.registerProfile(invalidProfile)
        )

        val result = manager.findCalibration(
            testConfiguration()
        )

        assertEquals(
            CalibrationMatchStatus.NO_PROFILE,
            result.status
        )
    }

    @Test
    fun testClearProfile() {
        assertTrue(manager.registerProfile(testProfile()))

        manager.clearProfile()

        val result = manager.findCalibration(
            testConfiguration()
        )

        assertEquals(
            CalibrationMatchStatus.NO_PROFILE,
            result.status
        )
    }
}
