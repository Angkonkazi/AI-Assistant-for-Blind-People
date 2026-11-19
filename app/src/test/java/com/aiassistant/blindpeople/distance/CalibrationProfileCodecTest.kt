
package com.aiassistant.blindpeople.distance

import org.junit.Assert.*
import org.junit.Test

class CalibrationProfileCodecTest {

    private val codec = CalibrationProfileCodec()

    private fun validProfile() =
        CameraCalibrationProfile(
            configuration = CameraConfiguration(
                cameraId = "camera_0",
                lensId = "rear_main",
                imageWidth = 640,
                imageHeight = 480,
                rotationDegrees = 0,
                zoomRatio = 1.0f
            ),
            focalLengthPixels = 800f,
            calibrationSampleCount = 3
        )

    @Test
    fun testEncodeAndDecode() {

        val original = validProfile()

        val encoded = codec.encode(original)
        val restored = codec.decode(encoded)

        assertNotNull(restored)
        assertEquals(original, restored)
    }

    @Test
    fun testMissingProfile() {

        assertNull(codec.decode(null))
        assertNull(codec.decode(""))
    }

    @Test
    fun testInvalidText() {

        val restored = codec.decode(
            "invalid calibration data"
        )

        assertNull(restored)
    }

    @Test
    fun testUnsupportedVersion() {

        val encoded = codec.encode(validProfile())

        val modified = encoded.replace(
            "version=1",
            "version=999"
        )

        assertNull(codec.decode(modified))
    }

    @Test
    fun testInvalidFocalLength() {

        val invalid = validProfile().copy(
            focalLengthPixels = -10f
        )

        assertThrows(
            IllegalArgumentException::class.java
        ) {
            codec.encode(invalid)
        }
    }

    @Test
    fun testSpecialCharactersInCameraId() {

        val profile = validProfile().copy(
            configuration =
                validProfile().configuration.copy(
                    cameraId = "rear camera: main=0"
                )
        )

        val restored = codec.decode(
            codec.encode(profile)
        )

        assertEquals(profile, restored)
    }

    @Test
    fun testInvalidStoredZoom() {

        val encoded = codec.encode(validProfile())

        val modified = encoded.replace(
            "zoomRatio=1.0",
            "zoomRatio=-2.0"
        )

        assertNull(codec.decode(modified))
    }

    @Test
    fun testMissingRequiredField() {

        val encoded = codec.encode(validProfile())

        val modified = encoded.replace(
            "sampleCount=3",
            "removedSampleCount=3"
        )

        assertNull(codec.decode(modified))
    }
}
