package ru.palestra.hike_exercise_b.presentation.permissions

import android.Manifest.permission.BLUETOOTH_CONNECT
import android.content.Context
import androidx.appcompat.app.AppCompatActivity

/** Описание объекта, который работает с Runtime-разрешениями пользователя. */
interface PermissionManagerApi<T> {

    /** Описание объекта, который отвечает за обобщение логики проверки разрешений пользователя. */
    fun interface PermissionStaticHelper {

        /**
         * Метод для проверки наличия Runtime-разрешения от пользователя.
         *
         * @param context [Context]
         * @param permission название Runtime-разрешения для проверки доступности.
         *
         * @return флаг о наличии или отсутствии доступа к пермишену.
         * */
        fun isPermissionGranted(context: Context, permission: String): Boolean
    }

    /**
     * Метод для запроса разрешения у пользователя на пермишн [BLUETOOTH_CONNECT],
     * который доступен начиная с 31 Android Api.
     * Если запрос разрешения не требуется, то будет сразу вызван [onPermissionGranted].
     *
     * @param onPermissionGranted действие, которое выполняется, если пользователь дал доступ.
     * @param onPermissionDenied действие, которое выполняется, если пользователь отказал в доступе.
     * */
    fun checkBluetoothPermissionIfNeeded(
        onPermissionGranted: () -> Unit,
        onPermissionDenied: () -> Unit
    )

    /**
     * Метод для запроса разрешения у пользователя на пермишн [RECORD_AUDIO].
     * Если запрос разрешения не требуется, то будет сразу вызван [onPermissionGranted].
     *
     * @param onPermissionGranted действие, которое выполняется, если пользователь дал доступ.
     * @param onPermissionDenied действие, которое выполняется, если пользователь отказал в доступе.
     * @param onPermissionDeniedPermanent действие, которое выполняется, если пользователь отказал в доступе навсегда.
     * */
    fun checkVoiceRecordPermissionIfNeeded(
        onPermissionGranted: () -> Unit,
        onPermissionDenied: () -> Unit = {},
        onPermissionDeniedPermanent: () -> Unit = {}
    )

    /**
     * Метод для множественного запроса разрешений у пользователя.
     *
     * @param permissions список Runtime-разрешений для запроса доступа к ним.
     * @param onCheckPermissionsResultAction действие с результатами запроса разрешений.
     * */
    fun checkMultiplePermissions(
        permissions: Array<String>,
        onCheckPermissionsResultAction: (Map<String, Boolean>) -> Unit
    )

    /**
     * Метод для множественного запроса разрешений у пользователя.
     *
     * @param permissions список Runtime-разрешений для запроса доступа к ним.
     * @param onRequestPermissionsResultAction действие с результатами запроса разрешений.
     * */
    fun requestMultiplePermissions(
        permissions: Array<String>,
        onRequestPermissionsResultAction: (Map<String, Boolean>) -> Unit
    )

    /**
     * Метод для получения списка Runtime-разрешений, которые необходимы данной
     * версии Android для работы с Bluetooth.
     * */
    fun getBluetoothPermissionsForCurrentAndroidDevice(): Array<String>

    /**
     * Метод для получения списка Runtime-разрешений, которые необходимы данной
     * версии Android для работы с Gps.
     * */
    fun getGpsPermissionsForCurrentAndroidDevice(): Array<String>

    /** Метод просит пользователя предоставить необходимые разрешения на системном экране настроек. */
    fun requestToEnablePermissions(activity: AppCompatActivity)
}