package ru.palestra.hike_exercise_b.presentation.dialogs

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.callbacks.onCancel
import ru.palestra.hike_exercise_b.R

/** Объект, который отвечает за создание и отображение диалоговых окон. */
internal class DialogManager(
    private val context: Context,
    private var lifecycleOwner: LifecycleOwner? = (context as? LifecycleOwner)
) : DialogManagerApi, LifecycleEventObserver {

    private var defaultErrorDialog: MaterialDialog? = null
    private var defaultInfoDialog: MaterialDialog? = null

    init {
        lifecycleOwner?.lifecycle?.addObserver(this)
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if (event == Lifecycle.Event.ON_DESTROY) {
            destroyAllDialogs()

            lifecycleOwner?.lifecycle?.removeObserver(this)
            lifecycleOwner = null
        }
    }

    override fun showSimpleErrorDialog(@StringRes errorTextResId: Int) =
        showSimpleErrorDialog(context.getString(errorTextResId))

    override fun showSimpleErrorDialog(errorText: String?) =
        buildAndShowErrorDialog(errorText)

    override fun showCreateServerSocketErrorDialog(onPositiveAction: () -> Unit, onNegativeAction: () -> Unit) =
        buildAndShowErrorDialog(
            errorMessage = context.getString(R.string.dialog_error_start_socket_server),
            onPositiveButtonTextRes = R.string.dialog_error_start_socket_server_yes,
            onNegativeButtonTextRes = R.string.dialog_error_start_socket_server_no,
            onPositiveAction = onPositiveAction,
            onNegativeAction = onNegativeAction
        )

    override fun showOnboardingInfoDialog(onPositiveAction: () -> Unit) =
        buildAndShowInfoDialog(true, R.string.dialog_onboarding_info, onPositiveAction = onPositiveAction)

    override fun showDisabledBluetoothInfoDialog(onPositiveAction: () -> Unit) =
        buildAndShowInfoDialog(false, R.string.dialog_error_bluetooth_disabled, onPositiveAction = onPositiveAction)

    override fun showDisabledGpsInfoDialog(onPositiveAction: () -> Unit) =
        buildAndShowInfoDialog(
            force = false,
            message = R.string.dialog_error_gps_disabled,
            onPositiveButtonTextRes = R.string.dialog_info_gps_permission_yes,
            onPositiveAction = onPositiveAction
        )

    override fun showGpsPermissionDeniedInfoDialog(onPositiveAction: () -> Unit) =
        buildAndShowInfoDialog(
            force = false,
            message = R.string.dialog_info_gps_permission_denied,
            onPositiveButtonTextRes = R.string.dialog_info_gps_permission_yes,
            onPositiveAction = onPositiveAction
        )

    override fun showBluetoothPermissionDeniedInfoDialog(onPositiveAction: () -> Unit) =
        buildAndShowInfoDialog(
            force = false,
            message = R.string.dialog_info_bluetooth_permission_denied,
            onPositiveButtonTextRes = R.string.dialog_info_permission_yes,
            onPositiveAction = onPositiveAction
        )

    override fun showRecordAudioPermissionDeniedInfoDialog(onPositiveAction: () -> Unit) =
        buildAndShowInfoDialog(
            force = false,
            message = R.string.dialog_info_record_audio_permission_denied,
            onPositiveAction = onPositiveAction
        )

    override fun showRecordAudioPermissionPermanentDeniedInfoDialog(onPositiveAction: () -> Unit) =
        buildAndShowInfoDialog(
            force = false,
            message = R.string.dialog_info_record_audio_permission_denied,
            onPositiveButtonTextRes = R.string.dialog_info_permission_yes,
            onPositiveAction = onPositiveAction,
            onCloseAction = {}
        )

    override fun showDropConnectQuestionsDialog(onPositiveAction: () -> Unit) =
        buildAndShowErrorDialog(
            errorMessage = context.getString(R.string.dialog_questions_drop_connect),
            onPositiveButtonTextRes = R.string.dialog_error_default_yes,
            onNegativeButtonTextRes = R.string.dialog_error_default_no,
            onPositiveAction = onPositiveAction,
            onNegativeAction = {},
            onCloseAction = {}
        )

    private fun buildAndShowInfoDialog(
        force: Boolean,
        @StringRes message: Int,
        @StringRes onPositiveButtonTextRes: Int = android.R.string.ok,
        onPositiveAction: () -> Unit = {},
        onCloseAction: () -> Unit = onPositiveAction
    ) {
        if (force || defaultInfoDialog == null || defaultInfoDialog?.isShowing == false) {
            defaultInfoDialog = createBaseDesignedDialog()
                .message(res = message)
                .positiveButton(onPositiveButtonTextRes)
                .positiveButton { onPositiveAction() }
                .onCancel { onCloseAction() }
                .apply { show() }
        }
    }

    private fun buildAndShowErrorDialog(
        errorMessage: String?,
        @StringRes onPositiveButtonTextRes: Int = android.R.string.ok,
        @StringRes onNegativeButtonTextRes: Int? = null,
        onPositiveAction: (() -> Unit)? = null,
        onNegativeAction: (() -> Unit)? = null,
        onCloseAction: (() -> Unit)? = onNegativeAction
    ) {
        if (errorMessage != null) {
            defaultErrorDialog = createBaseDesignedDialog()
                .message(text = errorMessage)
                .positiveButton(onPositiveButtonTextRes)
                .positiveButton { onPositiveAction?.invoke() }

            if (onNegativeButtonTextRes != null) {
                defaultErrorDialog?.negativeButton(res = onNegativeButtonTextRes)
            }

            /* Добавляем в диалог кнопку отрицательного ответа пользователя. */
            if (onNegativeAction != null) {
                defaultErrorDialog?.negativeButton { onNegativeAction() }
            }

            /* Добавляем в диалог кнопку отмены. */
            if (onCloseAction != null) {
                defaultErrorDialog?.onCancel { onCloseAction() }
            }

            defaultErrorDialog?.show()
        }
    }

    private fun createBaseDesignedDialog(): MaterialDialog =
        MaterialDialog(context).cornerRadius(res = R.dimen.default_dialog_corner_radius)

    private fun destroyAllDialogs() {
        defaultErrorDialog?.dismiss()
        defaultErrorDialog = null

        defaultInfoDialog?.dismiss()
        defaultInfoDialog = null
    }
}