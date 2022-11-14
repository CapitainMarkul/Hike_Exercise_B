package ru.palestra.hike_exercise_b.presentation

import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.BLUETOOTH_ADVERTISE
import android.Manifest.permission.BLUETOOTH_CONNECT
import android.Manifest.permission.BLUETOOTH_SCAN
import android.Manifest.permission.RECORD_AUDIO
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED
import android.bluetooth.BluetoothSocket
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager.VERTICAL
import com.github.ivbaranov.rxbluetooth.events.AclEvent
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import kotlinx.coroutines.launch
import ru.palestra.hike_exercise_b.R
import ru.palestra.hike_exercise_b.databinding.ActivityMainBinding
import ru.palestra.hike_exercise_b.domain.audio.AudioManager
import ru.palestra.hike_exercise_b.domain.audio.AudioManagerApi
import ru.palestra.hike_exercise_b.domain.bluetooth.BluetoothManager
import ru.palestra.hike_exercise_b.domain.bluetooth.BluetoothManagerApi
import ru.palestra.hike_exercise_b.domain.bluetooth.nearby_connect.BluetoothNearbyConnectApi
import ru.palestra.hike_exercise_b.domain.location.NearbyLocationManager
import ru.palestra.hike_exercise_b.domain.location.NearbyLocationManagerApi
import ru.palestra.hike_exercise_b.presentation.adapter.DeviceListAdapter
import ru.palestra.hike_exercise_b.presentation.dialogs.DialogManager
import ru.palestra.hike_exercise_b.presentation.dialogs.DialogManagerApi
import ru.palestra.hike_exercise_b.presentation.permissions.PermissionManager
import ru.palestra.hike_exercise_b.presentation.permissions.PermissionManagerApi
import java.lang.String.format


/** Основной экран приложения. */
class MainActivity : AppCompatActivity(),
    BluetoothNearbyConnectApi.ConnectedDeviceListener {

    private lateinit var binding: ActivityMainBinding

    private var distanceToOtherDevice: Float? = null

    private var bluetoothManager: BluetoothManagerApi? = null
    private var currentConnectedSocket: BluetoothSocket? = null

    private var iAmTalkingNow: Boolean = false

    private val permissionManager: PermissionManagerApi<String> = PermissionManager(this)

    private val audioManager: AudioManagerApi by lazy {
        AudioManager(applicationContext, this)
    }

    private val locationManager: NearbyLocationManagerApi by lazy {
        NearbyLocationManager(this)
    }

    private val dialogManager: DialogManagerApi by lazy {
        DialogManager(this)
    }

    private val bottomSheetBehavior: BottomSheetBehavior<LinearLayout> by lazy {
        BottomSheetBehavior.from(binding.bshFindDevices)
    }

    private val ifOtherDeviceDisconnectedAction: () -> Unit = {
        binding.btnTalk.clearFocus()
        binding.btnTalk.isActivated = false

        onConnectedDeviceUpdated(null)
        updateConnectViewState(AclEvent(ACTION_ACL_DISCONNECTED, null))
    }

    @SuppressLint("MissingPermission")
    private val deviceListAdapter: DeviceListAdapter = DeviceListAdapter(this) { bluetoothDevice ->
        if (bluetoothDevice.address == currentConnectedSocket?.remoteDevice?.address) {
            /* Попытка подключиться к тому же самому устройству. */
            showToastMessage(R.string.already_connected)
            return@DeviceListAdapter
        }

        /* Закрываем соединение с предыдущим устройством, если такое было. */
        currentConnectedSocket?.close()

        updateBottomSheetState(STATE_COLLAPSED)

        /* Считаем, что пользователь уже нашел того, кого искал, останавливаем поиск, чтобы не высаживать батарею. */
        bluetoothManager?.stopScanNearbyDevices()

        bluetoothManager?.tryConnectToDevice(
            bluetoothDevice = bluetoothDevice,
            onConnectionSuccessAction = { bluetoothSocket ->
                subscribeToBluetoothSocketData(bluetoothSocket)
            },
            onConnectionFailedAction = { bluetoothManager?.let { startServerSocket(it) } }
        )
    }

    override fun onConnectedDeviceUpdated(bluetoothSocket: BluetoothSocket?) {
        /* Закрываем соединение с другим устройством. */
        if (currentConnectedSocket?.isConnected == true) {
            currentConnectedSocket?.close()
        }

        setNewConnectedSocket(bluetoothSocket)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater).also {
            setContentView(it.root)
        }

        onBackPressedDispatcher.addCallback(object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (bottomSheetBehavior.state == STATE_EXPANDED) {
                    updateBottomSheetState(STATE_COLLAPSED)
                } else onBackPressedDispatcher.onBackPressed()
            }
        })
    }

    override fun onStart() {
        super.onStart()

        val neededPermissions = permissionManager.let {
            it.getBluetoothPermissionsForCurrentAndroidDevice() + it.getGpsPermissionsForCurrentAndroidDevice()
        }

        /* Шаг 0 - проверим наличие всех необходимых пермишенов. */
        permissionManager.checkMultiplePermissions(
            neededPermissions, ::handleCheckMultiplyPermissions
        )
    }

    override fun onStop() {
        stopVoiceRecord()
        super.onStop()
    }

    override fun onDestroy() {
        bluetoothManager?.onDestroy()
        super.onDestroy()
    }

    private fun continueInitialize() {
        /* Шаг 1. Инициализация BluetoothAdapter'a. */
        initializeBluetoothManagerIfNeeded()

        bluetoothManager?.let {
            /* Шаг 2. Инициализируем View компоненты. */
            initializeViews(it)

            /* Шаг 3. Инициализируем подписки на Bluetooth события. */
            observeAppStateEvents(it)
        }
    }

    private fun initializeBluetoothManagerIfNeeded() {
        bluetoothManager = BluetoothManager.getInstance(this).also {
            it.setupConnectedDeviceListener(this)
        }
    }

    private fun initializeViews(bluetoothManager: BluetoothManagerApi) {
        /* Устанавливаем имя устройства пользователя. */
        binding.txtMyDeviceStateTitle.text = format(
            getString(R.string.my_device_state_title_text),
            bluetoothManager.getCurrentDeviceNameOrDefault()
        )

        /* Настраиваем отображение списка найденных девайсов. */
        with(binding.rvFindDevices) {
            adapter = deviceListAdapter
            layoutManager = LinearLayoutManager(this@MainActivity, VERTICAL, false)
            addItemDecoration(DividerItemDecoration(this@MainActivity, VERTICAL))
        }

        /* Обработчик нажатия кнопки "Начать поиск". */
        binding.btnStartSearch.setOnClickListener {
            bluetoothManager.runIfHardwareAvailable {
                deviceListAdapter.removeAll()

                updateBottomSheetState(STATE_EXPANDED)

                bluetoothManager.startScanNearbyDevices(
                    onDiscoveryStartedAction = {
                        binding.pbSearchState.visibility = View.VISIBLE
                    },
                    onDiscoveryFinishedAction = {
                        binding.pbSearchState.visibility = View.GONE
                    },
                    onDiscoveryFailedAction = { showToastMessage(it.message, Toast.LENGTH_LONG) },
                    onDeviceFindAction = { findItem -> deviceListAdapter.addItem(findItem) }
                )
            }
        }

        /* Обработчик нажатия кнопки "Остановить поиск". */
        binding.btnStopSearch.setOnClickListener { bluetoothManager.stopScanNearbyDevices() }

        /* Обработчик нажатия кнопки "Исправить проблему доступа к моему устройству". */
        binding.txtDeviceStateConnectToMe.setOnClickListener {
            startServerSocket(bluetoothManager)
        }

        /* Обработчик нажатия кнопки "Текущая видимость для поиска". */
        binding.txtDeviceScanVisibility.setOnClickListener {
            bluetoothManager.makeDiscoverable(this)
        }

        /* Обработчик нажатия кнопки "Текущая отправка местоположения". */
        binding.txtDeviceLocationVisibility.setOnClickListener {
            dialogManager.showSimpleErrorDialog(R.string.dialog_info_location_not_sending_text)
        }

        /* Обработчик нажатия кнопки "Текущее подключение". */
        binding.txtConnectStates.setOnClickListener {
            if (currentConnectedSocket != null) {
                dialogManager.showDropConnectQuestionsDialog {
                    currentConnectedSocket?.close()
                    changeCurrentConnectButtonState()
                }
            }
        }

        /* Обработчики кнопки "Говорить". */
        with(binding.btnTalk) {
            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        iAmTalkingNow = true
                        binding.btnTalk.isActivated = true

                        handleRecordAction(bluetoothManager)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        iAmTalkingNow = false
                        binding.btnTalk.isActivated = false

                        stopVoiceRecord {
                            /* Произрываем звуковой сигнал окончания записи. */
                            audioManager.playIntercomSound()
                        }
                        v.performClick()
                        true
                    }

                    else -> false
                }
            }
        }
    }

    private fun handleRecordAction(bluetoothManager: BluetoothManagerApi): Boolean {
        /* Нужно проверить наличие разрешений на запись голоса. */
        var permissionGranted = false
        permissionManager.checkVoiceRecordPermissionIfNeeded(
            onPermissionGranted = {
                /* Разрешения получены, разрешаем запись голоса.  */
                permissionGranted = true
            },
            onPermissionDenied = {
                /* Разрешения не были получены, объявняем почему они нам необходимы.  */
                dialogManager.showRecordAudioPermissionDeniedInfoDialog {
                    permissionManager.requestMultiplePermissions(arrayOf(RECORD_AUDIO)) {}
                }
                return@checkVoiceRecordPermissionIfNeeded
            },
            onPermissionDeniedPermanent = {
                /*
                    Разрешения не были получены и пользователь отказался навсегда.
                    Открываем системные настройки, т.к. дальше работать не можем.
                */
                dialogManager.showRecordAudioPermissionPermanentDeniedInfoDialog {
                    permissionManager.requestToEnablePermissions(this@MainActivity)
                }
                return@checkVoiceRecordPermissionIfNeeded
            }
        )

        if (!permissionGranted) {
            /* Блокируем дальнейшую работу кнопки. */
            return false
        }

        /* Произрываем звуковой сигнал начала записи. */
        audioManager.playIntercomSound()

        /* Начинаем запись голоса. */
        lifecycleScope.launch {
            audioManager.startRecordingAndStreamVoice(bluetoothManager, ifOtherDeviceDisconnectedAction)
        }

        return true
    }

    @SuppressLint("MissingPermission")
    private fun observeAppStateEvents(bluetoothManager: BluetoothManagerApi) {
        /* Следим за проблемами подключения к другим устройствам. */
        bluetoothManager.observeBluetoothState(
            onBluetoothChangeAvailableStateAction = { bluetoothEnable ->
                if (bluetoothEnable) startServerSocket(bluetoothManager)
                else {
                    updateConnectToMeButtonState(false)
                    changeCurrentConnectButtonState()
                    showDisabledBluetoothInfoDialog()
                }
            }
        )

        /* Следим за статусом видимости нашего устройства. */
        bluetoothManager.obtainDiscoverableMode(
            onDiscoverabilityEnabledAction = {
                binding.txtDeviceScanVisibility.changeState(
                    text = getString(R.string.my_device_discovering_enabled_text), enabled = false
                )
            },
            onDiscoverabilityDisabledAction = {
                binding.txtDeviceScanVisibility.changeState(
                    text = getString(R.string.my_device_discovering_disabled_text), enabled = true
                )
            }
        )

        /* Следим за статусом соединения устройства. */
        bluetoothManager.subscribeToConnectionState { connectionStateEvent ->
            updateConnectViewState(connectionStateEvent)
        }

        /* Следим за необходимостью запросить актулаьную точку текущего местоположения устройства. */
        locationManager.observeRequestGeolocationChanges {
            /* Запрашиваем геолокацию, только если GPS включен, есть разрешение и подключенное устройство. */
            if (locationManager.isGpsLocationEnabled()) {
                permissionManager.checkMultiplePermissions(
                    permissions = permissionManager.getGpsPermissionsForCurrentAndroidDevice(),
                    onCheckPermissionsResultAction = { permissions ->
                        if (permissions[ACCESS_FINE_LOCATION] == true) {
                            currentConnectedSocket?.remoteDevice?.let { connectedDevice ->
                                updateDistanceInfoAction(connectedDevice, bluetoothManager)
                            }
                        }
                    }
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startServerSocket(bluetoothManager: BluetoothManagerApi) {
        bluetoothManager.runIfHardwareAvailable {
            bluetoothManager.startSocketServer(
                onConnectionSuccessAction = { bluetoothSocket ->
                    subscribeToBluetoothSocketData(bluetoothSocket)
                },
                onServerStartSuccessAction = { updateConnectToMeButtonState(true) },
                onServerStartFailedAction = {
                    dialogManager.showCreateServerSocketErrorDialog(
                        onPositiveAction = {
                            startServerSocket(bluetoothManager)
                            updateConnectToMeButtonState(true)
                        },
                        onNegativeAction = { updateConnectToMeButtonState(false) }
                    )
                }
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun updateConnectViewState(connectionStateEvent: AclEvent) =
        when (connectionStateEvent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                /* Если мы уже соединились, то останавливаем поиск. */
                bluetoothManager?.stopScanNearbyDevices()

                changeCurrentConnectButtonState(
                    text = format(
                        getString(R.string.connected_device_name_simple_text),
                        connectionStateEvent.bluetoothDevice.name
                    ),
                    enabled = true,
                    visible = true
                )

                updateBottomSheetState(STATE_COLLAPSED)
                changeStateTalkButton(true)
            }

            else -> {
                changeCurrentConnectButtonState()
                changeStateTalkButton(false)
            }
        }

    @SuppressLint("MissingPermission")
    private fun subscribeToBluetoothSocketData(bluetoothSocket: BluetoothSocket) {
        /* События передачи данных. */
        bluetoothManager?.observeByteStream(
            byteBufferSize = AudioManager.MIN_BUFFER_SIZE,
            connectionSocket = bluetoothSocket,
            onObtainedVoiceChunkDataAction = { byteChunk ->
                changeStateTalkButton(false)

                audioManager.updateStreamAudio(byteChunk)
            },
            onObtainedLocationRawDataAction = { rawOtherDeviceGeolocation ->
                val otherDeviceLocation = locationManager.tryConvertRawDataToLocation(rawOtherDeviceGeolocation)
                if (otherDeviceLocation != null) {
                    locationManager.getMyDeviceLocationPoint()?.let { myDevicePosition ->
                        distanceToOtherDevice = locationManager
                            .calculateDistanceBetweenLocationPoints(myDevicePosition, otherDeviceLocation)

                        currentConnectedSocket?.remoteDevice?.let { connectedDevice ->
                            changeCurrentConnectButtonState(
                                text = "${
                                    format(
                                        getString(R.string.connected_device_name_simple_text),
                                        connectedDevice.name
                                    )
                                }${
                                    format(
                                        getString(R.string.connected_device_distance_text),
                                        String.format("%.1f", distanceToOtherDevice)
                                    )
                                }",
                                enabled = true,
                                visible = true
                            )
                        }
                    }
                }
            },
            onErrorAction = {
                stopVoiceRecord()
                showToastMessage(R.string.dialog_error_connect_was_lost)

                if (currentConnectedSocket == null || !currentConnectedSocket!!.isConnected) {
                    /* Потеряли соединение с собеседником. */
                    changeCurrentConnectButtonState()
                    changeStateTalkButton(false)
                }
            }
        )

        /* События окончания/начала передачи данных от другого устройства. */
        bluetoothManager?.observeIncomingTransmissionState(
            onIncomingTransmissionChangeStateAction = { needEnableTalkButton ->
                currentConnectedSocket?.let { connectionSocket ->
                    if (connectionSocket.isConnected) {
                        changeStateTalkButton(needEnableTalkButton)
                    }
                }
            }
        )
    }

    @SuppressLint("MissingPermission")
    private fun updateDistanceInfoAction(connectedDevice: BluetoothDevice, bluetoothManager: BluetoothManagerApi) {
        val currentGeolocation = locationManager.getLastLastGeolocationAndRequestUpdate()
        locationManager.tryConvertLocationToRawData(currentGeolocation)?.let { rawData ->
            bluetoothManager.sendByteStream(rawData, ifOtherDeviceDisconnectedAction)
        }

        /* Проверяем статус доступности нашей геолокации. */
        with(binding.txtDeviceLocationVisibility) {
            val btnTextResId =
                if (currentGeolocation == null) R.string.connected_device_location_not_sending_text
                else R.string.connected_device_location_sending_text

            changeState(
                text = getString(btnTextResId),
                enabled = currentGeolocation == null,
                visible = true
            )
        }

        /* Если мы еще не знаем геолокацию собеседника. */
        if (distanceToOtherDevice == null) {
            changeCurrentConnectButtonState(
                text = format(
                    getString(R.string.connected_device_name_simple_text),
                    connectedDevice.name
                ),
                enabled = true,
                visible = true
            )
        }
    }

    private fun handleCheckMultiplyPermissions(result: Map<String, Boolean>) {
        if (bluetoothManager == null) {
            if (result.all { it.value }) {
                /* Все пермишены получены - продолжаем работу. */
                continueInitialize()
            } else {
                /* Разрашений недостаточно - просим пользователя о помощи. */
                dialogManager.showOnboardingInfoDialog { requestRequiredPermissions(result) }
            }
        }
    }

    private fun requestRequiredPermissions(result: Map<String, Boolean>) {
        permissionManager.requestMultiplePermissions(result.keys.toTypedArray()) { permissions ->
            permissions.entries.forEach { permissionMap ->
                when (permissionMap.key) {
                    BLUETOOTH_SCAN, BLUETOOTH_CONNECT, BLUETOOTH_ADVERTISE -> {
                        /* Если пользователь не предоставил данное разрешение - объявняем и открываем настройки. */
                        dialogManager.showBluetoothPermissionDeniedInfoDialog {
                            permissionManager.requestToEnablePermissions(this)
                        }

                        return@requestMultiplePermissions
                    }

                    ACCESS_FINE_LOCATION -> {
                        /* Если пользователь не предоставил данное разрешение - объявняем и открываем настройки. */
                        dialogManager.showGpsPermissionDeniedInfoDialog {
                            locationManager.requestToEnableGpsLocation()
                        }
                        return@requestMultiplePermissions
                    }
                }
            }
        }
    }

    private fun updateBottomSheetState(state: Int) {
        if (bottomSheetBehavior.state != state) {
            bottomSheetBehavior.state = state
        }
    }

    private fun showDisabledGpsInfoDialog() {
        dialogManager.showDisabledGpsInfoDialog {
            locationManager.requestToEnableGpsLocation()
        }
    }

    private fun showDisabledBluetoothInfoDialog() {
        dialogManager.showDisabledBluetoothInfoDialog {
            bluetoothManager?.requestToEnableBluetooth(this)
        }
    }

    private fun updateConnectToMeButtonState(isSuccess: Boolean) {
        val textRes =
            if (isSuccess) R.string.my_device_state_connect_to_me_success
            else R.string.my_device_state_connect_to_me_failed

        binding.txtDeviceStateConnectToMe.changeState(text = getString(textRes), enabled = !isSuccess)
    }

    private fun changeCurrentConnectButtonState(text: String = "", enabled: Boolean = false, visible: Boolean = false) =
        binding.txtConnectStates.changeState(text, enabled, visible)

    private fun stopVoiceRecord(block: () -> Unit = {}) {
        permissionManager.checkVoiceRecordPermissionIfNeeded(
            onPermissionGranted = {
                audioManager.stopRecording()

                block()
            }
        )
    }

    private fun setNewConnectedSocket(connectedSocket: BluetoothSocket?) {
        binding.txtDeviceLocationVisibility.isVisible = connectedSocket != null

        currentConnectedSocket = connectedSocket
        distanceToOtherDevice = null
    }

    private fun showToastMessage(@StringRes messageRes: Int, duration: Int = Toast.LENGTH_SHORT) =
        showToastMessage(getString(messageRes), duration)

    private fun showToastMessage(messageText: String?, duration: Int = Toast.LENGTH_SHORT) =
        Toast.makeText(this, messageText, duration).show()


    private fun changeStateTalkButton(enabled: Boolean) =
        with(binding.btnTalk) {
            if (isEnabled != enabled) {
                isEnabled = enabled
                if (!enabled) {
                    /* Радио-эфир занят, то молчим и слушаем. */
                    stopVoiceRecord()

                    audioManager.playAudio()
                } else {
                    audioManager.stopAudio()
                }
            }
        }

    private fun TextView.changeState(text: String, enabled: Boolean, visible: Boolean = true) =
        with(this) {
            setText(text)
            isEnabled = enabled
            isVisible = visible
        }

    private inline fun BluetoothManagerApi.runIfHardwareAvailable(crossinline block: () -> Unit) {
        if (!isBluetoothEnabled()) showDisabledBluetoothInfoDialog()
        else if (!locationManager.isGpsLocationEnabled()) showDisabledGpsInfoDialog()
        else block()
    }
}