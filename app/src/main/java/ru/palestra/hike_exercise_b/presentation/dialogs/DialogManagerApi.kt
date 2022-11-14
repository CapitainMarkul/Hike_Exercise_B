package ru.palestra.hike_exercise_b.presentation.dialogs

import androidx.annotation.StringRes

/** Описание объекта, который отвечает за создание и отображение диалоговых окон. */
interface DialogManagerApi {

    /**
     * Метод создания и отображения диалога-ошибки, для уведомления пользователя.
     *
     * @param errorTextResId идентификатор ресурса-сообщения для пользователя.
     * */
    fun showSimpleErrorDialog(@StringRes errorTextResId: Int)

    /**
     * Метод создания и отображения диалога-ошибки, для уведомления пользователя.
     *
     * @param errorText сообщение для пользователя.
     * */
    fun showSimpleErrorDialog(errorText: String?)

    /**
     * Метод создания и отображения диалога-ошибки о создании сервер-сокета.
     *
     * @param onPositiveAction действие, которое выполнится, если пользователь согласится.
     * @param onPositiveAction действие, которое выполнится, если пользователь откажется.
     * */
    fun showCreateServerSocketErrorDialog(onPositiveAction: () -> Unit, onNegativeAction: () -> Unit)

    /**
     * Метод для создания и отображения приветственного диалога с сообщением
     * о необходимых разрешениях.
     *
     * @param onPositiveAction действие, которое выполнится после ознакомления пользователя с сообщением.
     * */
    fun showOnboardingInfoDialog(onPositiveAction: () -> Unit)

    /**
     * Метод для создания и отображения диалога о выключенном Bluetooth.
     *
     * @param onPositiveAction действие, которое выполнится после ознакомления пользователя с сообщением.
     * */
    fun showDisabledBluetoothInfoDialog(onPositiveAction: () -> Unit)

    /**
     * Метод для создания и отображения диалога о выключенном Gps.
     *
     * @param onPositiveAction действие, которое выполнится после ознакомления пользователя с сообщением.
     * */
    fun showDisabledGpsInfoDialog(onPositiveAction: () -> Unit)

    /**
     * Метод для создания и отображения диалога о недоступности разрешения для работы с Gps.
     *
     * @param onPositiveAction действие, которое выполнится после ознакомления пользователя с сообщением.
     * */
    fun showGpsPermissionDeniedInfoDialog(onPositiveAction: () -> Unit)

    /**
     * Метод для создания и отображения диалога о недоступности разрешения для работы с Bluetooth.
     *
     * @param onPositiveAction действие, которое выполнится после ознакомления пользователя с сообщением.
     * */
    fun showBluetoothPermissionDeniedInfoDialog(onPositiveAction: () -> Unit)

    /**
     * Метод для создания и отображения диалога о недоступности разрешения для работы с микрофоном.
     *
     * @param onPositiveAction действие, которое выполнится после ознакомления пользователя с сообщением.
     * */
    fun showRecordAudioPermissionDeniedInfoDialog(onPositiveAction: () -> Unit)

    /**
     * Метод для создания и отображения диалога о недоступности разрешения для работы с микрофоном.
     * Используется для случая, если пользователь навсегда запретил доступ к микрофону.
     *
     * @param onPositiveAction действие, которое выполнится после ознакомления пользователя с сообщением.
    * */
    fun showRecordAudioPermissionPermanentDeniedInfoDialog(onPositiveAction: () -> Unit)

    /**
     * Метод для создания и отображения диалога с предложением разорвать существующее соединение.
     *
     * @param onPositiveAction действие, которое выполнится после ознакомления пользователя с сообщением.
     * */
    fun showDropConnectQuestionsDialog(onPositiveAction: () -> Unit)
}