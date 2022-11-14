package ru.palestra.hike_exercise_b.domain.bluetooth.destroy

/** Описание объекта, который должен осводождать занятые ресурсы. */
interface BluetoothDestroyApi {

    /** Метод для освобождения занятых ресурсов. */
    fun onDestroy()
}