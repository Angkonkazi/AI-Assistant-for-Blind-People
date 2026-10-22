
package com.aiassistant.blindpeople

import android.graphics.RectF
import android.os.SystemClock
import kotlin.math.max
import kotlin.math.min

/**
 * Smooths YOLO detections across consecutive inference results.
 *
 * This class is intended for visual overlay stabilization.
 * Do not use retained detections as proof of a current obstacle.
 *
 * Call update() from the camera analysis thread.
 */
class DetectionStabilizer(
    private val retentionMs: Long = 400L,
    private val smoothingFactor: Float = 0.65f,
    private val minimumIoU: Float = 0.20f
) {

    private data class TrackedObject(
        val detection: YOLODetection,
        val lastSeenMs: Long
    )

    private val trackedObjects = mutableListOf<TrackedObject>()

    /**
     * Receives fresh model detections and returns stabilized detections.
     */
    fun update(
        newDetections: List<YOLODetection>
    ): List<YOLODetection> {

        val currentTime = SystemClock.elapsedRealtime()

        // Remove objects that have not been observed recently.
        trackedObjects.removeAll { tracked ->
            currentTime - tracked.lastSeenMs > retentionMs
        }

        val matchedIndices = mutableSetOf<Int>()
        val updatedObjects = mutableListOf<TrackedObject>()

        for (newDetection in newDetections) {

            var bestMatchIndex = -1
            var bestIoU = minimumIoU

            for (index in trackedObjects.indices) {

                if (index in matchedIndices) {
                    continue
                }

                val previous = trackedObjects[index].detection

                if (previous.classId != newDetection.classId) {
                    continue
                }

                val overlap = calculateIoU(
                    previous.boundingBox,
                    newDetection.boundingBox
                )

                if (overlap > bestIoU) {
                    bestIoU = overlap
                    bestMatchIndex = index
                }
            }

            if (bestMatchIndex >= 0) {

                matchedIndices.add(bestMatchIndex)

                val previous =
                    trackedObjects[bestMatchIndex].detection

                val smoothedBox = smoothBox(
                    previous.boundingBox,
                    newDetection.boundingBox
                )

                updatedObjects.add(
                    TrackedObject(
                        detection = newDetection.copy(
                            boundingBox = smoothedBox
                        ),
                        lastSeenMs = currentTime
                    )
                )

            } else {

                // Newly detected object.
                updatedObjects.add(
                    TrackedObject(
                        detection = newDetection,
                        lastSeenMs = currentTime
                    )
                )
            }
        }

        // Temporarily retain unmatched objects.
        for (index in trackedObjects.indices) {

            if (index in matchedIndices) {
                continue
            }

            val previous = trackedObjects[index]

            if (
                currentTime - previous.lastSeenMs <= retentionMs
            ) {
                updatedObjects.add(previous)
            }
        }

        trackedObjects.clear()
        trackedObjects.addAll(updatedObjects)

        return trackedObjects.map { tracked ->
            tracked.detection
        }
    }

    /**
     * Clears all previously tracked objects.
     */
    fun reset() {
        trackedObjects.clear()
    }

    /**
     * Smooths coordinates using an exponential moving average.
     */
    private fun smoothBox(
        previous: RectF,
        current: RectF
    ): RectF {

        val alpha = smoothingFactor.coerceIn(0f, 1f)

        return RectF(
            previous.left * (1f - alpha) +
                    current.left * alpha,

            previous.top * (1f - alpha) +
                    current.top * alpha,

            previous.right * (1f - alpha) +
                    current.right * alpha,

            previous.bottom * (1f - alpha) +
                    current.bottom * alpha
        )
    }

    /**
     * Calculates intersection over union for two boxes.
     */
    private fun calculateIoU(
        boxA: RectF,
        boxB: RectF
    ): Float {

        val intersectionLeft = max(
            boxA.left,
            boxB.left
        )

        val intersectionTop = max(
            boxA.top,
            boxB.top
        )

        val intersectionRight = min(
            boxA.right,
            boxB.right
        )

        val intersectionBottom = min(
            boxA.bottom,
            boxB.bottom
        )

        val intersectionWidth = max(
            0f,
            intersectionRight - intersectionLeft
        )

        val intersectionHeight = max(
            0f,
            intersectionBottom - intersectionTop
        )

        val intersectionArea =
            intersectionWidth * intersectionHeight

        val areaA =
            boxA.width() * boxA.height()

        val areaB =
            boxB.width() * boxB.height()

        val unionArea =
            areaA + areaB - intersectionArea

        return if (unionArea > 0f) {
            intersectionArea / unionArea
        } else {
            0f
        }
    }
}
