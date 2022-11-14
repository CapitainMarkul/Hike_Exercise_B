package ru.palestra.hike_exercise_b.domain.bluetooth.nearby_connect

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED
import android.bluetooth.BluetoothSocket
import com.github.ivbaranov.rxbluetooth.RxBluetooth
import com.github.ivbaranov.rxbluetooth.events.AclEvent
import com.github.ivbaranov.rxbluetooth.events.ConnectionStateEvent
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import ru.palestra.hike_exercise_b.domain.bluetooth.nearby_connect.BluetoothNearbyConnectApi.ConnectedDeviceListener
import ru.palestra.hike_exercise_b.domain.utils.disposeIfNeeded
import java.util.UUID


/** Объект, отвечающего за соединение устройств между собой по Bluetooth. */
internal class BluetoothNearbyConnect(
    private val appBlChanelId: UUID,
    private val appBlServerName: String,
    private val rxBluetooth: RxBluetooth
) : BluetoothNearbyConnectApi {

    private var clientSocket: BluetoothSocket? = null
    private var serverSocket: BluetoothSocket? = null

    private var connectToDeviceDisposable: Disposable? = null
    private var socketOpenDisposable: Disposable? = null

    private var boundStateChangesDisposable: Disposable? = null

    private var connectedDeviceListener: ConnectedDeviceListener? = null

    override fun setupConnectedDeviceListener(listener: ConnectedDeviceListener) {
        connectedDeviceListener = listener

        connectedDeviceListener?.onConnectedDeviceUpdated(clientSocket ?: serverSocket)
    }

    override fun tryConnectToDevice(
        bluetoothDevice: BluetoothDevice,
        onConnectionSuccessAction: (BluetoothSocket) -> Unit,
        onConnectionFailedAction: (Throwable) -> Unit
    ) {
        connectToDeviceDisposable = rxBluetooth.connectAsClient(bluetoothDevice, appBlChanelId)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ bluetoothSocket ->
                clientSocket = bluetoothSocket
                connectedDeviceListener?.onConnectedDeviceUpdated(bluetoothSocket)

                onConnectionSuccessAction(bluetoothSocket)
            }, { error -> onConnectionFailedAction(error) })
    }

    @SuppressLint("MissingPermission")
    override fun startSocketServer(
        onConnectionSuccessAction: (BluetoothSocket) -> Unit,
        onServerStartSuccessAction: () -> Unit,
        onServerStartFailedAction: (Throwable) -> Unit
    ) {
        socketOpenDisposable = rxBluetooth.connectAsServer(appBlServerName, appBlChanelId)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe { onServerStartSuccessAction() }
            .doOnError { error -> onServerStartFailedAction(error) }
            .subscribe({ bluetoothSocket ->
                serverSocket = bluetoothSocket
                connectedDeviceListener?.onConnectedDeviceUpdated(bluetoothSocket)

                onConnectionSuccessAction(bluetoothSocket)
            }, { error -> onServerStartFailedAction(error) })
    }

    override fun stopSocketServer() {
        socketOpenDisposable?.disposeIfNeeded()
    }

    override fun subscribeToConnectionState(onConnectionStateChangedAction: (AclEvent) -> Unit) {
        if (boundStateChangesDisposable == null) {
            boundStateChangesDisposable = rxBluetooth.observeAclEvent()
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { connectionState ->
                        if (connectionState.action != BluetoothDevice.ACTION_ACL_CONNECTED) {
                            clientSocket = null
                            serverSocket = null
                            connectedDeviceListener?.onConnectedDeviceUpdated(null)
                        }

                        onConnectionStateChangedAction(connectionState)
                    },
                    { onConnectionStateChangedAction(AclEvent(ACTION_ACL_DISCONNECTED, null)) }
                )
        }
    }

    override fun onDestroy() {
        socketOpenDisposable?.disposeIfNeeded()
        connectToDeviceDisposable?.disposeIfNeeded()
        boundStateChangesDisposable?.disposeIfNeeded()

        serverSocket?.close()
        serverSocket = null

        connectedDeviceListener = null
    }
}