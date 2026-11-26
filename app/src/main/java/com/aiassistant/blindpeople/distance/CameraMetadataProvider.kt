
package com.aiassistant.blindpeople.distance

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log

/**
 * Reads available camera hardware metadata.
 *
 * This is a diagnostic module, not a distance estimator.
 * No manual user calibration is required.
 */
class CameraMetadataProvider(context: Context) {

    companion object {
        private const val TAG = "CAMERA_METADATA"
    }

    private val cameraManager =
        context.applicationContext.getSystemService(
            Context.CAMERA_SERVICE
        ) as CameraManager

    fun logCameraMetadata() {

        try {

            for (cameraId in cameraManager.cameraIdList) {

                val characteristics =
                    cameraManager.getCameraCharacteristics(
                        cameraId
                    )

                val lensFacing = characteristics.get(
                    CameraCharacteristics.LENS_FACING
                )

                val facingName = when (lensFacing) {

                    CameraCharacteristics.LENS_FACING_BACK ->
                        "BACK"

                    CameraCharacteristics.LENS_FACING_FRONT ->
                        "FRONT"

                    CameraCharacteristics.LENS_FACING_EXTERNAL ->
                        "EXTERNAL"

                    else -> "UNKNOWN"
                }

                val focalLengths = characteristics.get(
                    CameraCharacteristics
                        .LENS_INFO_AVAILABLE_FOCAL_LENGTHS
                )

                val sensorSize = characteristics.get(
                    CameraCharacteristics
                        .SENSOR_INFO_PHYSICAL_SIZE
                )

                val pixelArray = characteristics.get(
                    CameraCharacteristics
                        .SENSOR_INFO_PIXEL_ARRAY_SIZE
                )

                val activeArray = characteristics.get(
                    CameraCharacteristics
                        .SENSOR_INFO_ACTIVE_ARRAY_SIZE
                )

                val preCorrectionArray = characteristics.get(
                    CameraCharacteristics
                        .SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE
                )

                val intrinsics = characteristics.get(
                    CameraCharacteristics
                        .LENS_INTRINSIC_CALIBRATION
                )

                val sensorOrientation = characteristics.get(
                    CameraCharacteristics.SENSOR_ORIENTATION
                )

                Log.i(TAG, "================================")
                Log.i(TAG, "Camera ID: $cameraId")
                Log.i(TAG, "Lens facing: $facingName")

                Log.i(
                    TAG,
                    "Focal lengths (mm): " +
                            (focalLengths?.contentToString()
                                ?: "Unavailable")
                )

                Log.i(
                    TAG,
                    "Sensor physical size (mm): " +
                            (sensorSize?.let {
                                "${it.width} x ${it.height}"
                            } ?: "Unavailable")
                )

                Log.i(
                    TAG,
                    "Sensor pixel array: " +
                            (pixelArray?.let {
                                "${it.width} x ${it.height}"
                            } ?: "Unavailable")
                )

                Log.i(
                    TAG,
                    "Active array: " +
                            (activeArray?.toShortString()
                                ?: "Unavailable")
                )

                Log.i(
                    TAG,
                    "Pre-correction active array: " +
                            (preCorrectionArray?.toShortString()
                                ?: "Unavailable")
                )

                Log.i(
                    TAG,
                    "Intrinsic calibration: " +
                            (intrinsics?.contentToString()
                                ?: "Unavailable")
                )

                Log.i(
                    TAG,
                    "Sensor orientation: $sensorOrientation"
                )

                Log.i(TAG, "================================")
            }

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Unable to read camera metadata",
                e
            )
        }
    }
}
