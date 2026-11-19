
package com.aiassistant.blindpeople.distance

import org.junit.Assert.*
import org.junit.Test

class DetectionInputMapperTest {

    private val mapper = DetectionInputMapper()

    private fun detection(
        index: Int = 0,
        label: String = "reference object"
    ) = RawDetectionInput(
        index = index,
        label = label,
        confidence = 0.90f,
        left = 100f,
        top = 50f,
        right = 300f,
        bottom = 350f
    )

    @Test
    fun testSingleObjectConversion() {

        val result = mapper.convert(
            detections = listOf(detection())
        )

        assertEquals(1, result.size)
        assertEquals(0, result[0].detectionIndex)
        assertEquals(
            "reference object",
            result[0].objectName
        )

        assertEquals(
            0.90f,
            result[0].confidence,
            0.0001f
        )
    }

    @Test
    fun testBoundingBoxCoordinates() {

        val result = mapper.convert(
            detections = listOf(detection())
        )

        val item = result[0]

        assertEquals(100f, item.left, 0.0001f)
        assertEquals(50f, item.top, 0.0001f)
        assertEquals(300f, item.right, 0.0001f)
        assertEquals(350f, item.bottom, 0.0001f)
    }

    @Test
    fun testKnownObjectHeight() {

        val result = mapper.convert(
            detections = listOf(detection()),
            knownHeightsByIndex = mapOf(
                0 to 0.50f
            )
        )

        assertEquals(
            0.50f,
            result[0].realObjectHeightMeters!!,
            0.0001f
        )
    }

    @Test
    fun testUnknownObjectHeight() {

        val result = mapper.convert(
            detections = listOf(detection())
        )

        assertNull(
            result[0].realObjectHeightMeters
        )
    }

    @Test
    fun testMultipleObjects() {

        val detections = listOf(
            detection(0, "chair"),
            detection(1, "reference box"),
            detection(2, "door")
        )

        val result = mapper.convert(
            detections = detections,
            knownHeightsByIndex = mapOf(
                1 to 0.50f
            )
        )

        assertEquals(3, result.size)

        assertNull(
            result[0].realObjectHeightMeters
        )

        assertEquals(
            0.50f,
            result[1].realObjectHeightMeters!!,
            0.0001f
        )

        assertNull(
            result[2].realObjectHeightMeters
        )

        assertEquals("door", result[2].objectName)
        assertEquals(2, result[2].detectionIndex)
    }

    @Test
    fun testInvalidPhysicalHeight() {

        val invalidHeights = listOf(
            -1f,
            0f,
            Float.NaN,
            Float.POSITIVE_INFINITY
        )

        for (height in invalidHeights) {

            val result = mapper.convert(
                detections = listOf(detection()),
                knownHeightsByIndex = mapOf(
                    0 to height
                )
            )

            assertNull(
                result[0].realObjectHeightMeters
            )
        }
    }

    @Test
    fun testEmptyDetectionList() {

        val result = mapper.convert(
            detections = emptyList(),
            knownHeightsByIndex = mapOf(
                0 to 0.50f
            )
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun testUnmatchedReferenceIndex() {

        val result = mapper.convert(
            detections = listOf(detection(index = 2)),
            knownHeightsByIndex = mapOf(
                0 to 0.50f
            )
        )

        assertEquals(1, result.size)

        assertNull(
            result[0].realObjectHeightMeters
        )

        assertEquals(
            2,
            result[0].detectionIndex
        )
    }
}
