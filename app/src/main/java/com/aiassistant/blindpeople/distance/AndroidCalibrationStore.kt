
package com.aiassistant.blindpeople.distance

import android.content.Context

/**
 * Saves and restores one calibration profile
 * using Android SharedPreferences.
 *
 * This class is Android-specific and will
 * require device testing later.
 */
class AndroidCalibrationStore(
    context: Context
) : CalibrationProfileStorage {

    companion object {
        private const val PREFS_NAME =
            "distance_calibration_preferences"

        private const val PROFILE_KEY =
            "active_calibration_profile"
    }

    private val preferences =
        context.applicationContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

    private val codec = CalibrationProfileCodec()

    /**
     * Save the supplied profile.
     *
     * Returns true if the write succeeds.
     * Existing saved data is not changed if
     * the profile is invalid.
     */
    override fun saveProfile(
        profile: CameraCalibrationProfile
    ): Boolean {

        return try {
            val encoded = codec.encode(profile)

            preferences.edit()
                .putString(PROFILE_KEY, encoded)
                .commit()

        } catch (e: Exception) {
            false
        }
    }

    /**
     * Restore the previously saved profile.
     *
     * Returns null if no valid profile exists.
     */
    override fun loadProfile(): CameraCalibrationProfile? {

        return try {
            val encoded = preferences.getString(
                PROFILE_KEY,
                null
            )

            codec.decode(encoded)

        } catch (e: Exception) {
            null
        }
    }

    /**
     * Clear the stored calibration profile.
     */
    override fun clearProfile(): Boolean {

        return preferences.edit()
            .remove(PROFILE_KEY)
            .commit()
    }
}
