
package com.aiassistant.blindpeople.distance

import java.util.Locale

/**
 * Provisional physical-size assumptions for
 * controlled distance-estimation experiments.
 *
 * These values are not universal dimensions
 * or validated statistical averages.
 */

enum class ReferenceDimension {
    HEIGHT,
    WIDTH
}

data class ObjectSizeReference(
    val label: String,
    val dimension: ReferenceDimension,
    val sizeMeters: Float
)

object ObjectSizeDatabase {

    private val references = listOf(

        ObjectSizeReference(
            label = "chair",
            dimension = ReferenceDimension.HEIGHT,
            sizeMeters = 0.85f
        ),

        ObjectSizeReference(
            label = "dining table",
            dimension = ReferenceDimension.HEIGHT,
            sizeMeters = 0.75f
        ),

        ObjectSizeReference(
            label = "refrigerator",
            dimension = ReferenceDimension.HEIGHT,
            sizeMeters = 1.70f
        ),

        ObjectSizeReference(
            label = "bottle",
            dimension = ReferenceDimension.HEIGHT,
            sizeMeters = 0.25f
        ),

        ObjectSizeReference(
            label = "cup",
            dimension = ReferenceDimension.HEIGHT,
            sizeMeters = 0.10f
        ),

        ObjectSizeReference(
            label = "laptop",
            dimension = ReferenceDimension.WIDTH,
            sizeMeters = 0.35f
        ),

        ObjectSizeReference(
            label = "keyboard",
            dimension = ReferenceDimension.WIDTH,
            sizeMeters = 0.44f
        ),

        ObjectSizeReference(
            label = "microwave",
            dimension = ReferenceDimension.WIDTH,
            sizeMeters = 0.48f
        )

    ).associateBy { it.label }

    fun findReference(
        label: String
    ): ObjectSizeReference? {

        val normalizedLabel =
            label.trim().lowercase(Locale.ROOT)

        return references[normalizedLabel]
    }

    fun isSupported(label: String): Boolean {

        return findReference(label) != null
    }

    fun getSupportedLabels(): Set<String> {

        return references.keys.toSet()
    }
}
