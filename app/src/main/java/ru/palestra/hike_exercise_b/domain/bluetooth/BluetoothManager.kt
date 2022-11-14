package ru.palestra.hike_exercise_b.domain.bluetooth

import android.content.Context
import android.location.LocationManager
import androidx.appcompat.app.AppCompatActivity
import com.github.ivbaranov.rxbluetooth.RxBluetooth
import ru.palestra.hike_exercise_b.R
import ru.palestra.hike_exercise_b.domain.bluetooth.device_info.BluetoothDeviceInfo
import ru.palestra.hike_exercise_b.domain.bluetooth.device_info.BluetoothDeviceInfoApi
import ru.palestra.hike_exercise_b.domain.bluetooth.nearby_connect.BluetoothNearbyConnect
import ru.palestra.hike_exercise_b.domain.bluetooth.nearby_connect.BluetoothNearbyConnectApi
import ru.palestra.hike_exercise_b.domain.bluetooth.nearby_data_stream.BluetoothDataStream
import ru.palestra.hike_exercise_b.domain.bluetooth.nearby_data_stream.BluetoothDataStreamApi
import ru.palestra.hike_exercise_b.domain.bluetooth.nearby_scaner.BluetoothNearbyScanner
import ru.palestra.hike_exercise_b.domain.bluetooth.nearby_scaner.BluetoothNearbyScannerApi
import timber.log.Timber
import java.util.UUID

/**
 * Объект для инкапсуляции логики работы с Bluetooth LE.
 *
 * Используем формат синглтона, чтобы упростить обработку ConfigChanges.
 * */
object BluetoothManager {

    private const val ENABLE_BLUETOOTH_REQUEST_CODE = 101

    private var applicationContext: Context? = null

    private val rxBluetooth: RxBluetooth by lazy { RxBluetooth(applicationContext) }
    private val bluetoothDataStream: BluetoothDataStreamApi by lazy { BluetoothDataStream() }
    private val bluetoothNearbyScanner: BluetoothNearbyScannerApi by lazy { BluetoothNearbyScanner(rxBluetooth) }
    private val bluetoothNearbyConnector: BluetoothNearbyConnectApi by lazy {
        BluetoothNearbyConnect(appBlChanelId, appBlServerName, rxBluetooth)
    }

    private val appBlServerName by lazy {
        applicationContext?.getString(R.string.app_bl_server_name)
            ?: throw BluetoothManagerNotInitialized()
    }

    private val appBlChanelId by lazy {
        applicationContext?.let {
            UUID.fromString(it.getString(R.string.app_bl_uuid))
        } ?: throw BluetoothManagerNotInitialized()
    }

    private val bluetoothDeviceInfoApi: BluetoothDeviceInfoApi by lazy {
        applicationContext?.let { BluetoothDeviceInfo(it, rxBluetooth) }
            ?: throw BluetoothManagerNotInitialized()
    }

    private val bluetoothManagerApi: BluetoothManagerApi by lazy {
        object : BluetoothManagerApi,
            BluetoothNearbyScannerApi by bluetoothNearbyScanner,
            BluetoothNearbyConnectApi by bluetoothNearbyConnector,
            BluetoothDeviceInfoApi by bluetoothDeviceInfoApi,
            BluetoothDataStreamApi by bluetoothDataStream {

            override fun isBluetoothEnabled(): Boolean = rxBluetooth.isBluetoothEnabled

            override fun requestToEnableBluetooth(activity: AppCompatActivity) =
                rxBluetooth.enableBluetooth(activity, ENABLE_BLUETOOTH_REQUEST_CODE)

            override fun onDestroy() {
                bluetoothDataStream.onDestroy()
                bluetoothNearbyScanner.onDestroy()
                bluetoothNearbyConnector.onDestroy()
            }
        }
    }

    /** Метод для инициализации и получения объекта [BluetoothManagerApi]. */
    @Synchronized
    fun getInstance(context: Context): BluetoothManagerApi {
        if (applicationContext == null) {
            applicationContext = context.applicationContext

            Timber.plant(Timber.DebugTree())
        }

        return bluetoothManagerApi
    }

    private class BluetoothManagerNotInitialized : Throwable(
        "Before call API, you must call BluetoothManager.getInstance(context: Context)"
    )
}