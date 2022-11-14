package ru.palestra.hike_exercise_b.domain.bluetooth.device_info

import android.Manifest.permission.BLUETOOTH_CONNECT
import ru.palestra.hike_exercise_b.domain.bluetooth.destroy.BluetoothDestroyApi

/** Описание объекта, который предоставляет информацию о текущем устройстве пользователя. */
interface BluetoothDeviceInfoApi : BluetoothDestroyApi {

    /**
     * Метод для получения Bluetooth-имени устройства пользователя.
     *
     * Вернет дефолтное значение, если пользователь не предоставил
     * разрешение [BLUETOOTH_CONNECT] или информация не может быть
     * получена по иным причинам.
     * */
    fun getCurrentDeviceNameOrDefault() : String

    /**
     * Метод для подписки на события доступности Bluetooth модуля.
     *
     * @param onBluetoothChangeAvailableStateAction действие, сообщающее о смене доступности Bluetooth модуля.
     * */
    fun observeBluetoothState(
        onBluetoothChangeAvailableStateAction: (Boolean) -> Unit
    )
}