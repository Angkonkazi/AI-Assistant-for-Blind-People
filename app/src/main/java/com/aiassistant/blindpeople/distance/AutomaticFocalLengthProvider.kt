
package com.aiassistant.blindpeople.distance

import android.graphics.Matrix
import android.hardware.camera2.CameraCharacteristics
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraInfo
import androidx.camera.core.ImageProxy
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Experimental focal-length information for
 * the upright analysis bitmap.
 */
data class AutomaticFocalLength(
    val cameraId: String,
    val focalX: Float,
    val focalY: Float,
    val imageWidth: Int,
    val imageHeight: Int,
    val source: String
)

/**
 * Estimates focal length in analysis-image pixels
 * using Android camera hardware metadata.
 *
 * Does not require manual user calibration.
 *
 * The resulting parameters are experimental,
 * not independently validated calibration.
 */
@OptIn(ExperimentalCamera2Interop::class)
class AutomaticFocalLengthProvider(
    cameraInfo: CameraInfo
) {

    private val camera2Info =
        Camera2CameraInfo.from(cameraInfo)

    private val cameraId =
        camera2Info.cameraId

    private val intrinsicCalibration =
        camera2Info.getCameraCharacteristic(
            CameraCharacteristics
                .LENS_INTRINSIC_CALIBRATION
        )

    private val focalLengths =
        camera2Info.getCameraCharacteristic(
            CameraCharacteristics
                .LENS_INFO_AVAILABLE_FOCAL_LENGTHS
        )

    private val sensorSize =
        camera2Info.getCameraCharacteristic(
            CameraCharacteristics
                .SENSOR_INFO_PHYSICAL_SIZE
        )

    private val pixelArray =
        camera2Info.getCameraCharacteristic(
            CameraCharacteristics
                .SENSOR_INFO_PIXEL_ARRAY_SIZE
        )

    private val activeArray =
        camera2Info.getCameraCharacteristic(
            CameraCharacteristics
                .SENSOR_INFO_ACTIVE_ARRAY_SIZE
        )

    private val preCorrectionArray =
        camera2Info.getCameraCharacteristic(
            CameraCharacteristics
                .SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE
        )

    private fun valid(value: Float): Boolean {

        return value.isFinite() && value > 0f
    }

    fun estimate(
        imageProxy: ImageProxy
    ): AutomaticFocalLength? {

        var sensorFx: Float
        var sensorFy: Float
        val source: String

        val intrinsics = intrinsicCalibration

        // Prefer usable hardware intrinsic parameters.
        if (
            intrinsics != null &&
            intrinsics.size >= 5 &&
            valid(intrinsics[0]) &&
            valid(intrinsics[1]) &&
            activeArray != null &&
            activeArray == preCorrectionArray
        ) {

            sensorFx = intrinsics[0]
            sensorFy = intrinsics[1]

            source = "CAMERA_INTRINSIC_APPROX"

        } else {

            // Use physical camera specifications
            // when usable intrinsics are unavailable.
            val focalMm =
                focalLengths?.singleOrNull()
                    ?: return null

            val physicalSize =
                sensorSize ?: return null

            val sensorPixels =
                pixelArray ?: return null

            if (
                !valid(focalMm) ||
                !valid(physicalSize.width) ||
                !valid(physicalSize.height) ||
                sensorPixels.width <= 0 ||
                sensorPixels.height <= 0
            ) {
                return null
            }

            sensorFx =
                focalMm * sensorPixels.width /
                        physicalSize.width

            sensorFy =
                focalMm * sensorPixels.height /
                        physicalSize.height

            source = "PHYSICAL_SENSOR_APPROX"
        }

        val matrix =
            imageProxy.imageInfo
                .sensorToBufferTransformMatrix

        val values = FloatArray(9)

        matrix.getValues(values)

        if (values.any { !it.isFinite() }) {
            return null
        }

        // Reject unexpected perspective transforms.
        if (
            abs(values[Matrix.MPERSP_0]) > 0.000001f ||
            abs(values[Matrix.MPERSP_1]) > 0.000001f ||
            abs(values[Matrix.MPERSP_2] - 1f) > 0.000001f
        ) {
            return null
        }

        val xScale = values[Matrix.MSCALE_X]
        val xSkew = values[Matrix.MSKEW_X]

        val ySkew = values[Matrix.MSKEW_Y]
        val yScale = values[Matrix.MSCALE_Y]

        // Convert sensor focal lengths into
        // analysis-buffer pixel coordinates.
        val bufferFx = sqrt(
            (xScale * sensorFx) *
                    (xScale * sensorFx) +
                    (xSkew * sensorFy) *
                    (xSkew * sensorFy)
        )

        val bufferFy = sqrt(
            (ySkew * sensorFx) *
                    (ySkew * sensorFx) +
                    (yScale * sensorFy) *
                    (yScale * sensorFy)
        )

        if (!valid(bufferFx) || !valid(bufferFy)) {
            return null
        }

        val rotation =
            imageProxy.imageInfo.rotationDegrees

        if (rotation !in listOf(0, 90, 180, 270)) {
            return null
        }

        val swapAxes =
            rotation == 90 || rotation == 270

        val finalFx =
            if (swapAxes) bufferFy else bufferFx

        val finalFy =
            if (swapAxes) bufferFx else bufferFy

        val finalWidth =
            if (swapAxes) {
                imageProxy.height
            } else {
                imageProxy.width
            }

        val finalHeight =
            if (swapAxes) {
                imageProxy.width
            } else {
                imageProxy.height
            }

        if (finalWidth <= 0 || finalHeight <= 0) {
            return null
        }

        return AutomaticFocalLength(
            cameraId = cameraId,
            focalX = finalFx,
            focalY = finalFy,
            imageWidth = finalWidth,
            imageHeight = finalHeight,
            source = source
        )
    }
}
