package ru.palestra.hike_exercise_b.domain.bluetooth.nearby_connect

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import com.github.ivbaranov.rxbluetooth.events.AclEvent
import ru.palestra.hike_exercise_b.domain.bluetooth.destroy.BluetoothDestroyApi

/** Описание объекта, отвечающего за соединение устройств между собой по Bluetooth. */
interface BluetoothNearbyConnectApi : BluetoothDestroyApi {

    /** Слушатель для подписки на события установки соединения с другим устройством. */
    fun interface ConnectedDeviceListener {

        /** Метод для нотификации о новом подключенном/поетрянном соединении. */
        fun onConnectedDeviceUpdated(bluetoothSocket: BluetoothSocket?)
    }

    /** Метод для установки слушателя новых подключений. */
    fun setupConnectedDeviceListener(listener: ConnectedDeviceListener)

    /**
     * Метод предназначен для попытки установки соединения с найденным устройством
     * посредством Bluetooth соединения.
     *
     * @param bluetoothDevice устройство, с которым пытаемся установить соединение.
     * @param onConnectionSuccessAction действие, если соединение было успешно установлено.
     * @param onConnectionFailedAction действие, если соединение установить не удалось.
     * */
    fun tryConnectToDevice(
        bluetoothDevice: BluetoothDevice,
        onConnectionSuccessAction: (BluetoothSocket) -> Unit,
        onConnectionFailedAction: (Throwable) -> Unit
    )

    /**
     * Метод для создания и запуска сокета, к которому смогут подключаться другие устройства.
     *
     * @param onConnectionSuccessAction действие, если соединение было успешно установлено.
     * @param onServerStartSuccessAction действие, если сервер был успешно запущен.
     * @param onServerStartFailedAction действие, если не удалось запустить сервер.
     * */
    fun startSocketServer(
        onConnectionSuccessAction: (BluetoothSocket) -> Unit,
        onServerStartSuccessAction: () -> Unit,
        onServerStartFailedAction: (Throwable) -> Unit
    )

    /** Метод для остановки сокета, к которому смогут подключаться другие устройства. */
    fun stopSocketServer()

    /**
     * Метод для подписки на события изменения статуса привязки устройств.
     *
     * @param onConnectionStateChangedAction действие, вызывается когда статус соединения изменился.
     * */
    fun subscribeToConnectionState(onConnectionStateChangedAction: (AclEvent) -> Unit)
}