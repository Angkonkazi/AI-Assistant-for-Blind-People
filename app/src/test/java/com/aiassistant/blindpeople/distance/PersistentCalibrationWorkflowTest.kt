
package com.aiassistant.blindpeople.distance

import org.junit.Assert.*
import org.junit.Test

class PersistentCalibrationWorkflowTest {

    /**
     * Fake storage for laptop-based unit tests.
     */
    private class FakeStorage : CalibrationProfileStorage {

        var storedProfile: CameraCalibrationProfile? = null
        var allowSave = true
        var allowClear = true

        override fun saveProfile(
            profile: CameraCalibrationProfile
        ): Boolean {
            if (!allowSave) return false

            storedProfile = profile
            return true
        }

        override fun loadProfile(): CameraCalibrationProfile? {
            return storedProfile
        }

        override fun clearProfile(): Boolean {
            if (!allowClear) return false

            storedProfile = null
            return true
        }
    }

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
        heightMeters: Float? = 1f
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
    fun testSaveCalibrationProfile() {
        val storage = FakeStorage()
        val workflow = PersistentCalibrationWorkflow(storage)

        assertTrue(workflow.saveAndActivate(profile()))
        assertEquals(profile(), storage.storedProfile)
    }

    @Test
    fun testRestoreAfterRestart() {
        val storage = FakeStorage()

        val firstWorkflow = PersistentCalibrationWorkflow(storage)
        assertTrue(firstWorkflow.saveAndActivate(profile()))

        // Simulate a new app session.
        val newWorkflow = PersistentCalibrationWorkflow(storage)

        assertTrue(newWorkflow.restoreCalibration())

        val results = newWorkflow.analyze(
            listOf(detection()),
            configuration()
        )

        assertEquals(1, results.size)
        assertEquals(
            2.6666667f,
            results[0].distance.estimatedMeters!!,
            0.0001f
        )
    }

    @Test
    fun testRestoreWithoutSavedProfile() {
        val workflow = PersistentCalibrationWorkflow(
            FakeStorage()
        )

        assertFalse(workflow.restoreCalibration())

        val results = workflow.analyze(
            listOf(detection()),
            configuration()
        )

        assertNull(results[0].distance.estimatedMeters)
    }

    @Test
    fun testCameraMismatch() {
        val storage = FakeStorage()
        val workflow = PersistentCalibrationWorkflow(storage)

        assertTrue(workflow.saveAndActivate(profile()))

        val results = workflow.analyze(
            listOf(detection()),
            configuration().copy(cameraId = "camera_1")
        )

        assertEquals(
            CalibrationMatchStatus.CONFIGURATION_MISMATCH,
            results[0].calibrationStatus
        )

        assertNull(results[0].distance.estimatedMeters)
    }

    @Test
    fun testInvalidProfileRejected() {
        val storage = FakeStorage()
        val workflow = PersistentCalibrationWorkflow(storage)

        val invalidProfile = profile().copy(
            focalLengthPixels = -100f
        )

        assertFalse(
            workflow.saveAndActivate(invalidProfile)
        )

        assertNull(storage.storedProfile)
    }

    @Test
    fun testStorageFailure() {
        val storage = FakeStorage()
        storage.allowSave = false

        val workflow = PersistentCalibrationWorkflow(storage)

        assertFalse(workflow.saveAndActivate(profile()))

        val results = workflow.analyze(
            listOf(detection()),
            configuration()
        )

        assertNull(results[0].distance.estimatedMeters)
    }

    @Test
    fun testClearCalibration() {
        val storage = FakeStorage()
        val workflow = PersistentCalibrationWorkflow(storage)

        assertTrue(workflow.saveAndActivate(profile()))
        assertTrue(workflow.clearCalibration())

        assertNull(storage.storedProfile)

        val results = workflow.analyze(
            listOf(detection()),
            configuration()
        )

        assertNull(results[0].distance.estimatedMeters)
    }

    @Test
    fun testUnknownObjectHeight() {
        val storage = FakeStorage()
        val workflow = PersistentCalibrationWorkflow(storage)

        assertTrue(workflow.saveAndActivate(profile()))

        val results = workflow.analyze(
            listOf(detection(heightMeters = null)),
            configuration()
        )

        assertNull(results[0].distance.estimatedMeters)
    }

    @Test
    fun testEmptyDetections() {
        val storage = FakeStorage()
        val workflow = PersistentCalibrationWorkflow(storage)

        assertTrue(workflow.saveAndActivate(profile()))

        val results = workflow.analyze(
            emptyList(),
            configuration()
        )

        assertTrue(results.isEmpty())
    }
}
