package ru.palestra.hike_exercise_b.domain.bluetooth.device_info

import android.Manifest.permission.BLUETOOTH_CONNECT
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.os.Build
import com.github.ivbaranov.rxbluetooth.RxBluetooth
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import ru.palestra.hike_exercise_b.R
import ru.palestra.hike_exercise_b.presentation.permissions.PermissionManager
import ru.palestra.hike_exercise_b.presentation.permissions.PermissionManagerApi
import ru.palestra.hike_exercise_b.domain.utils.disposeIfNeeded

/** Реализация бъекта, который предоставляет информацию о текущем устройстве пользователя. */
internal class BluetoothDeviceInfo(
    private val applicationContext: Context,
    private val rxBluetooth: RxBluetooth
) : BluetoothDeviceInfoApi {

    private val permissionStaticHelper: PermissionManagerApi.PermissionStaticHelper by lazy {
        PermissionManager.Companion
    }

    /* Необходим для доступа к информации о текущем устройстве пользователя. */
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        BluetoothAdapter.getDefaultAdapter()
    }

    private val unknownDeviceText by lazy {
        applicationContext.getText(R.string.unknown_user_device_info).toString()
    }

    private var bluetoothStateDisposable: Disposable? = null

    @SuppressLint("MissingPermission")
    override fun getCurrentDeviceNameOrDefault(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !permissionStaticHelper.isPermissionGranted(applicationContext, BLUETOOTH_CONNECT)
        ) {
            /* Устройства с SDK >= 31, требуют явное разрешение у пользователя. */
            unknownDeviceText
        } else {
            bluetoothAdapter?.name ?: unknownDeviceText
        }

    override fun observeBluetoothState(onBluetoothChangeAvailableStateAction: (Boolean) -> Unit) {
        bluetoothStateDisposable = rxBluetooth.observeBluetoothState()
            .subscribeOn(Schedulers.computation())
            .distinctUntilChanged()
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe {
                getBluetoothEnabledState(bluetoothAdapter?.state)?.let { stateEnable ->
                    onBluetoothChangeAvailableStateAction(stateEnable)
                }
            }
            .subscribe { state ->
                getBluetoothEnabledState(state)?.let { onBluetoothChangeAvailableStateAction(it) }
            }
    }

    override fun onDestroy() {
        bluetoothStateDisposable?.disposeIfNeeded()
    }

    private fun getBluetoothEnabledState(enabledState: Int?): Boolean? =
        when (enabledState) {
            BluetoothAdapter.STATE_ON -> true
            BluetoothAdapter.STATE_OFF -> false

            else -> null
        }
}