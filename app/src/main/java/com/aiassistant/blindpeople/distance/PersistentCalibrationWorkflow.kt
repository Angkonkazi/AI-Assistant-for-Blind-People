
package com.aiassistant.blindpeople.distance

/**
 * Coordinates calibration storage and the
 * existing calibrated distance processor.
 *
 * The workflow uses a storage interface,
 * allowing pure Kotlin unit testing.
 */
class PersistentCalibrationWorkflow(
    private val storage: CalibrationProfileStorage,
    private val processor: CalibratedDistanceProcessor =
        CalibratedDistanceProcessor()
) {

    /**
     * Validate, save and activate a calibration
     * profile.
     *
     * Returns false if validation or saving fails.
     */
    fun saveAndActivate(
        profile: CameraCalibrationProfile
    ): Boolean {

        // Verify the supplied profile first.
        val validator = CameraCalibrationManager()

        if (!validator.registerProfile(profile)) {
            return false
        }

        // Persist before activating the profile.
        val saved = try {
            storage.saveProfile(profile)
        } catch (e: Exception) {
            false
        }

        if (!saved) {
            return false
        }

        return processor.registerCalibration(profile)
    }

    /**
     * Restore a previously saved profile.
     *
     * A failed restore clears the active profile
     * instead of continuing with stale settings.
     */
    fun restoreCalibration(): Boolean {

        processor.clearCalibration()

        val profile = try {
            storage.loadProfile()
        } catch (e: Exception) {
            null
        } ?: return false

        return processor.registerCalibration(profile)
    }

    /**
     * Remove both the active and saved profile.
     *
     * The in-memory profile is always cleared.
     * The return value reports whether persistent
     * storage was successfully cleared.
     */
    fun clearCalibration(): Boolean {

        processor.clearCalibration()

        return try {
            storage.clearProfile()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Process detections using the currently
     * active calibration profile.
     *
     * If the configuration does not match,
     * numerical distances remain unavailable.
     */
    fun analyze(
        detections: List<DistanceDetectionInput>,
        currentConfiguration: CameraConfiguration
    ): List<ManagedDistanceResult> {

        return processor.process(
            detections = detections,
            currentConfiguration = currentConfiguration
        )
    }
}
