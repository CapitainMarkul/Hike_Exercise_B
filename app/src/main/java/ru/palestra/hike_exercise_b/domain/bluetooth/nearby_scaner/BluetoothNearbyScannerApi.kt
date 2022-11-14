package ru.palestra.hike_exercise_b.domain.bluetooth.nearby_scaner

import android.bluetooth.BluetoothDevice
import androidx.appcompat.app.AppCompatActivity
import ru.palestra.hike_exercise_b.domain.bluetooth.destroy.BluetoothDestroyApi

/** Описание объекта, предназначенного для поиска девайсов поблизости текущего устройства. */
interface BluetoothNearbyScannerApi : BluetoothDestroyApi {

    /**
     * Метод используется для запуска процесса поиска девайсов вокруг текущего устройства.
     *
     * @param onDiscoveryStartedAction действие вызовется при успешном начале процесса сканирования.
     * @param onDiscoveryFinishedAction действие вызовется при окончании процесса сканирования.
     * @param onDiscoveryFailedAction действие вызовется при возникновении ошибки.
     * @param onDeviceFindAction действие вызовется при нахождении устройства доступного для подключения.
     * */
    fun startScanNearbyDevices(
        onDiscoveryStartedAction: () -> Unit,
        onDiscoveryFinishedAction: () -> Unit,
        onDiscoveryFailedAction: (Throwable) -> Unit,
        onDeviceFindAction: (BluetoothDevice) -> Unit
    )

    /** Метод используется для остановки процесса поиска девайсов вокруг текущего устройства. */
    fun stopScanNearbyDevices()

    /** Метод используется получения состояния поиска. */
    fun isDiscoveringNow(): Boolean

    /**
     * Метод для проверки, видно ли утсройство для остальных или нет.
     *
     * @param onDiscoverabilityEnabledAction действие, если устройство видно другим пользователям.
     * @param onDiscoverabilityDisabledAction действие, если устройство не видно другим пользователям.
     * */
    fun obtainDiscoverableMode(
        onDiscoverabilityEnabledAction: () -> Unit,
        onDiscoverabilityDisabledAction: () -> Unit
    )

    /** Метод для того, чтобы сделать устройство видимым для других. */
    fun makeDiscoverable(activity: AppCompatActivity)
}