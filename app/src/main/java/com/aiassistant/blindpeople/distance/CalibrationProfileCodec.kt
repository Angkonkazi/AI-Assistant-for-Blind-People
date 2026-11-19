
package com.aiassistant.blindpeople.distance

import java.io.StringReader
import java.io.StringWriter
import java.util.Properties

/**
 * Converts calibration profiles into text
 * that can be saved and restored.
 *
 * Uses Java Properties for safe text encoding.
 * Does not depend on Android framework classes.
 */
class CalibrationProfileCodec {

    companion object {
        private const val FORMAT_VERSION = "1"
    }

    /**
     * Convert a valid calibration profile
     * into a text representation.
     */
    fun encode(
        profile: CameraCalibrationProfile
    ): String {

        require(isValidProfile(profile)) {
            "Invalid calibration profile"
        }

        val config = profile.configuration

        val properties = Properties().apply {
            setProperty("version", FORMAT_VERSION)
            setProperty("cameraId", config.cameraId)
            setProperty("lensId", config.lensId)

            setProperty(
                "imageWidth",
                config.imageWidth.toString()
            )

            setProperty(
                "imageHeight",
                config.imageHeight.toString()
            )

            setProperty(
                "rotationDegrees",
                config.rotationDegrees.toString()
            )

            setProperty(
                "zoomRatio",
                config.zoomRatio.toString()
            )

            setProperty(
                "focalLengthPixels",
                profile.focalLengthPixels.toString()
            )

            setProperty(
                "sampleCount",
                profile.calibrationSampleCount.toString()
            )
        }

        val writer = StringWriter()
        properties.store(writer, null)

        return writer.toString()
    }

    /**
     * Convert previously saved text into
     * a validated calibration profile.
     *
     * Returns null for missing, corrupted,
     * unsupported or invalid data.
     */
    fun decode(
        encoded: String?
    ): CameraCalibrationProfile? {

        if (encoded.isNullOrBlank()) {
            return null
        }

        return try {
            val properties = Properties()
            properties.load(StringReader(encoded))

            if (
                properties.getProperty("version") !=
                FORMAT_VERSION
            ) {
                return null
            }

            val cameraId =
                properties.getProperty("cameraId")
                    ?: return null

            val lensId =
                properties.getProperty("lensId")
                    ?: return null

            val imageWidth =
                properties.getProperty("imageWidth")
                    ?.toIntOrNull() ?: return null

            val imageHeight =
                properties.getProperty("imageHeight")
                    ?.toIntOrNull() ?: return null

            val rotation =
                properties.getProperty("rotationDegrees")
                    ?.toIntOrNull() ?: return null

            val zoom =
                properties.getProperty("zoomRatio")
                    ?.toFloatOrNull() ?: return null

            val focal =
                properties.getProperty("focalLengthPixels")
                    ?.toFloatOrNull() ?: return null

            val sampleCount =
                properties.getProperty("sampleCount")
                    ?.toIntOrNull() ?: return null

            val profile = CameraCalibrationProfile(
                configuration = CameraConfiguration(
                    cameraId = cameraId,
                    lensId = lensId,
                    imageWidth = imageWidth,
                    imageHeight = imageHeight,
                    rotationDegrees = rotation,
                    zoomRatio = zoom
                ),
                focalLengthPixels = focal,
                calibrationSampleCount = sampleCount
            )

            if (isValidProfile(profile)) {
                profile
            } else {
                null
            }

        } catch (e: Exception) {
            null
        }
    }

    /**
     * Reuse the validation already implemented
     * by CameraCalibrationManager.
     */
    private fun isValidProfile(
        profile: CameraCalibrationProfile
    ): Boolean {

        val validator = CameraCalibrationManager()
        return validator.registerProfile(profile)
    }
}
