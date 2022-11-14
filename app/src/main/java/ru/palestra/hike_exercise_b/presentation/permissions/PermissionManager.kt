package ru.palestra.hike_exercise_b.presentation.permissions

import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.BLUETOOTH_ADVERTISE
import android.Manifest.permission.BLUETOOTH_CONNECT
import android.Manifest.permission.BLUETOOTH_SCAN
import android.Manifest.permission.RECORD_AUDIO
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat.checkSelfPermission
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner


/** Объект-реализация, который работает с Runtime-разрешениями пользователя. */
internal class PermissionManager(
    private var activity: AppCompatActivity?
) : PermissionManagerApi<String>, LifecycleEventObserver {

    companion object : PermissionManagerApi.PermissionStaticHelper {
        override fun isPermissionGranted(context: Context, permission: String): Boolean =
            checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    private var requestSinglePermissionsLauncher: ActivityResultLauncher<String>? = null
    private var requestMultiplePermissionsLauncher: ActivityResultLauncher<Array<String>>? = null

    init {
        activity?.lifecycle?.addObserver(this)
    }

    private var onSinglePermissionsGrantedAction: (() -> Unit)? = null
    private var onSinglePermissionsDeniedAction: (() -> Unit)? = null
    private var onMultiplePermissionsAction: ((Map<String, Boolean>) -> Unit)? = null

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_CREATE -> {
                requestSinglePermissionsLauncher =
                    activity?.registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
                        if (isGranted) onSinglePermissionsGrantedAction?.invoke() else onSinglePermissionsDeniedAction?.invoke()
                    }

                requestMultiplePermissionsLauncher =
                    activity?.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
                        onMultiplePermissionsAction?.invoke(permissions)
                    }
            }
            Lifecycle.Event.ON_DESTROY -> {
                onSinglePermissionsGrantedAction = null
                onSinglePermissionsDeniedAction = null
                onMultiplePermissionsAction = null

                activity?.lifecycle?.removeObserver(this)
                activity = null
            }

            else -> Unit
        }
    }

    override fun checkBluetoothPermissionIfNeeded(onPermissionGranted: () -> Unit, onPermissionDenied: () -> Unit) {
        if (VERSION.SDK_INT < VERSION_CODES.S || activity?.let { isPermissionGranted(it, BLUETOOTH_CONNECT) } == true) {
            onPermissionGranted()
        } else {
            onSinglePermissionsGrantedAction = onPermissionGranted
            onSinglePermissionsDeniedAction = onPermissionDenied

            requestSinglePermissionsLauncher?.launch(BLUETOOTH_CONNECT)
        }
    }

    @RequiresApi(api = VERSION_CODES.M)
    private fun neverAskAgainSelected(activity: Activity, permission: String?): Boolean {
        val prevShouldShowStatus: Boolean = getRationaleDisplayStatus(activity, permission)
        val currShouldShowStatus = activity.shouldShowRequestPermissionRationale(permission!!)
        return prevShouldShowStatus != currShouldShowStatus
    }

    private fun getRationaleDisplayStatus(context: Context, permission: String?): Boolean {
        val genPrefs = context.getSharedPreferences("GENERIC_PREFERENCES", Context.MODE_PRIVATE)
        return genPrefs.getBoolean(permission, false)
    }

    override fun checkVoiceRecordPermissionIfNeeded(
        onPermissionGranted: () -> Unit,
        onPermissionDenied: () -> Unit,
        onPermissionDeniedPermanent: () -> Unit
    ) {
        activity?.let { context ->
            if (isPermissionGranted(context, RECORD_AUDIO)) onPermissionGranted()
            else {
                if (VERSION.SDK_INT < VERSION_CODES.M) onPermissionGranted()
                else {
                    if (neverAskAgainSelected(context, RECORD_AUDIO)) onPermissionDeniedPermanent()
                    else onPermissionDenied()
                }
            }
        }
    }

    override fun checkMultiplePermissions(
        permissions: Array<String>,
        onCheckPermissionsResultAction: (Map<String, Boolean>) -> Unit
    ) {
        val maps = mutableMapOf<String, Boolean>()
        activity?.let { context ->
            permissions.forEach { permissionName ->
                maps[permissionName] = isPermissionGranted(context, permissionName)
            }
        }

        onCheckPermissionsResultAction(maps)
    }

    override fun requestMultiplePermissions(
        permissions: Array<String>,
        onRequestPermissionsResultAction: (Map<String, Boolean>) -> Unit
    ) {
        onMultiplePermissionsAction = onRequestPermissionsResultAction

        requestMultiplePermissionsLauncher?.launch(permissions)
    }

    override fun getBluetoothPermissionsForCurrentAndroidDevice(): Array<String> =
        if (VERSION.SDK_INT < VERSION_CODES.S) arrayOf()
        else arrayOf(BLUETOOTH_SCAN, BLUETOOTH_CONNECT, BLUETOOTH_ADVERTISE)

    override fun getGpsPermissionsForCurrentAndroidDevice(): Array<String> =
        arrayOf(ACCESS_FINE_LOCATION)

    override fun requestToEnablePermissions(activity: AppCompatActivity) =
        activity.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", activity.packageName, null))
        )
}