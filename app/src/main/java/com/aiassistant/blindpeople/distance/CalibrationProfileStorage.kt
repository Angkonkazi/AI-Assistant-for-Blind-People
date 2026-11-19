
package com.aiassistant.blindpeople.distance

/**
 * Storage contract for camera calibration profiles.
 *
 * AndroidCalibrationStore will implement this.
 * Tests can use an in-memory implementation.
 */
interface CalibrationProfileStorage {

    fun saveProfile(
        profile: CameraCalibrationProfile
    ): Boolean

    fun loadProfile(): CameraCalibrationProfile?

    fun clearProfile(): Boolean
}
