
package com.aiassistant.blindpeople.distance

import kotlin.math.abs

/**
 * Describes the effective camera setup used
 * for image analysis.
 *
 * rotationDegrees: rotation applied to produce
 * the upright analysis image.
 *
 * zoomRatio: actual camera zoom ratio.
 */
data class CameraConfiguration(
    val cameraId: String,
    val lensId: String,
    val imageWidth: Int,
    val imageHeight: Int,
    val rotationDegrees: Int,
    val zoomRatio: Float
)

/**
 * Calibration parameters tied to one camera setup.
 *
 * focalLengthPixels is an experimental value
 * obtained from reference measurements.
 */
data class CameraCalibrationProfile(
    val configuration: CameraConfiguration,
    val focalLengthPixels: Float,
    val calibrationSampleCount: Int
)

enum class CalibrationMatchStatus {
    MATCHED,
    NO_PROFILE,
    INVALID_CONFIGURATION,
    CONFIGURATION_MISMATCH,
    INVALID_PROFILE
}

data class CalibrationLookupResult(
    val focalLengthPixels: Float?,
    val status: CalibrationMatchStatus
)

/**
 * Pure Kotlin manager for in-memory calibration.
 *
 * It does not read actual Android camera metadata
 * or save calibration permanently.
 */
class CameraCalibrationManager {

    private var activeProfile: CameraCalibrationProfile? = null

    fun registerProfile(
        profile: CameraCalibrationProfile
    ): Boolean {

        if (!isValidConfiguration(profile.configuration)) {
            return false
        }

        if (
            !profile.focalLengthPixels.isFinite() ||
            profile.focalLengthPixels <= 0f ||
            profile.calibrationSampleCount <= 0
        ) {
            return false
        }

        activeProfile = profile
        return true
    }

    fun findCalibration(
        current: CameraConfiguration
    ): CalibrationLookupResult {

        if (!isValidConfiguration(current)) {
            return CalibrationLookupResult(
                focalLengthPixels = null,
                status = CalibrationMatchStatus.INVALID_CONFIGURATION
            )
        }

        val profile = activeProfile
            ?: return CalibrationLookupResult(
                focalLengthPixels = null,
                status = CalibrationMatchStatus.NO_PROFILE
            )

        if (
            !isValidConfiguration(profile.configuration) ||
            !profile.focalLengthPixels.isFinite() ||
            profile.focalLengthPixels <= 0f ||
            profile.calibrationSampleCount <= 0
        ) {
            return CalibrationLookupResult(
                focalLengthPixels = null,
                status = CalibrationMatchStatus.INVALID_PROFILE
            )
        }

        val saved = profile.configuration

        // Compare every field explicitly.
        val matches =
            saved.cameraId == current.cameraId &&
                    saved.lensId == current.lensId &&
                    saved.imageWidth == current.imageWidth &&
                    saved.imageHeight == current.imageHeight &&
                    saved.rotationDegrees == current.rotationDegrees &&
                    abs(saved.zoomRatio - current.zoomRatio) < 0.0001f

        return if (matches) {
            CalibrationLookupResult(
                focalLengthPixels = profile.focalLengthPixels,
                status = CalibrationMatchStatus.MATCHED
            )
        } else {
            CalibrationLookupResult(
                focalLengthPixels = null,
                status = CalibrationMatchStatus.CONFIGURATION_MISMATCH
            )
        }
    }

    fun clearProfile() {
        activeProfile = null
    }

    private fun isValidConfiguration(
        configuration: CameraConfiguration
    ): Boolean {

        return configuration.cameraId.isNotBlank() &&
                configuration.lensId.isNotBlank() &&
                configuration.imageWidth > 0 &&
                configuration.imageHeight > 0 &&
                configuration.rotationDegrees in listOf(
            0, 90, 180, 270
        ) &&
                configuration.zoomRatio.isFinite() &&
                configuration.zoomRatio > 0f
    }
}
