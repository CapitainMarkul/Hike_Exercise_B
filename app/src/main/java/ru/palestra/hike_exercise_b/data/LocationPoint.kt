package ru.palestra.hike_exercise_b.data

/**
 * Модель точки местоположения.
 *
 * @param latitude ширина.
 * @param longitude долгота.
 * */
data class LocationPoint(
    val latitude: Double,
    val longitude: Double
) {
    companion object {
        const val START_WORD_MARKER = "start"
        const val END_WORD_MARKER = "end"
    }
}
