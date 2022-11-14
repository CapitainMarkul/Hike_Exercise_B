package ru.palestra.hike_exercise_b.domain.bluetooth.nearby_scaner

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE
import android.bluetooth.BluetoothDevice
import androidx.appcompat.app.AppCompatActivity
import com.github.ivbaranov.rxbluetooth.RxBluetooth
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import ru.palestra.hike_exercise_b.domain.utils.disposeIfNeeded

/** Реализация объекта, предназначенного для поиска девайсов поблизости текущего устройства. */
internal class BluetoothNearbyScanner(
    private val rxBluetooth: RxBluetooth,
    private val compositeDisposable: CompositeDisposable = CompositeDisposable()
) : BluetoothNearbyScannerApi {

    private companion object {
        private const val ENABLE_BLUETOOTH_DISCOVERABILITY_REQUEST_CODE = 102
    }

    private var observeDevicesDisposable: Disposable? = null
    private var observeDiscoveryStatesDisposable: Disposable? = null
    private var observeBluetoothScanModeDisposable: Disposable? = null
    private var observeBluetoothStatesDisposable: Disposable? = null

    private val bluetoothAdapter: BluetoothAdapter by lazy {
        BluetoothAdapter.getDefaultAdapter()
    }

    override fun startScanNearbyDevices(
        onDiscoveryStartedAction: () -> Unit,
        onDiscoveryFinishedAction: () -> Unit,
        onDiscoveryFailedAction: (Throwable) -> Unit,
        onDeviceFindAction: (BluetoothDevice) -> Unit
    ) {
        if (isDiscoveringNow()) stopScanNearbyDevices()

        if (observeDiscoveryStatesDisposable == null) {
            observeDiscoveryStatesDisposable = rxBluetooth.observeDiscovery()
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ result ->
                    when (result) {
                        BluetoothAdapter.ACTION_DISCOVERY_STARTED -> onDiscoveryStartedAction()
                        BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> onDiscoveryFinishedAction()
                    }
                }, { error -> onDiscoveryFailedAction(error) })

            observeDiscoveryStatesDisposable?.let { compositeDisposable.add(it) }
        }

        if (observeDevicesDisposable == null) {
            observeDevicesDisposable = rxBluetooth.observeDevices()
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { findItem -> onDeviceFindAction(findItem) },
                    { error -> onDiscoveryFailedAction(error) }
                )

            observeDevicesDisposable?.let { compositeDisposable.add(it) }
        }

        rxBluetooth.startDiscovery()
    }

    override fun stopScanNearbyDevices() {
        rxBluetooth.cancelDiscovery()
    }

    override fun isDiscoveringNow(): Boolean = rxBluetooth.isDiscovering

    @SuppressLint("MissingPermission")
    override fun obtainDiscoverableMode(
        onDiscoverabilityEnabledAction: () -> Unit,
        onDiscoverabilityDisabledAction: () -> Unit
    ) {
        if (observeBluetoothScanModeDisposable == null) {
            observeBluetoothScanModeDisposable = rxBluetooth.observeScanMode()
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe {
                    if (bluetoothAdapter.scanMode == SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
                        onDiscoverabilityEnabledAction()
                    } else {
                        onDiscoverabilityDisabledAction()
                    }
                }
                .subscribe({ scanMode ->
                    if (scanMode?.let { it == SCAN_MODE_CONNECTABLE_DISCOVERABLE } == true) {
                        onDiscoverabilityEnabledAction()
                    } else {
                        onDiscoverabilityDisabledAction()
                    }
                }, { onDiscoverabilityDisabledAction() })

            observeBluetoothScanModeDisposable?.let { compositeDisposable.add(it) }
        }
    }

    override fun makeDiscoverable(activity: AppCompatActivity) {
        rxBluetooth.enableDiscoverability(activity, ENABLE_BLUETOOTH_DISCOVERABILITY_REQUEST_CODE)
    }

    override fun onDestroy() {
        rxBluetooth.cancelDiscovery()
        compositeDisposable.disposeIfNeeded()
    }
}