package ru.palestra.hike_exercise_b.domain.audio

import ru.palestra.hike_exercise_b.domain.bluetooth.nearby_data_stream.BluetoothDataStreamApi

/** Описание объекта, отвечающего за работу со звуком на устройстве пользователя. */
interface AudioManagerApi {

    /** Слушатель состояния записи звука. */
    fun interface RecordStateListener {

        /**
         * Метод для подписки на статус записи звука через микрофон устройства.
         *
         * @param onRecordState статус записи.
         * */
        fun subscribeToRecordState(onRecordState: Boolean)
    }

    /** Метод предназначен для воспроизведения звука нажатия на кнопку рации. */
    fun playIntercomSound()

    /**
     * Метод предназначен для начала записи голоса при помощи микрофона устройства.
     *
     * @param bluetoothDataStreamApi Api для передачи данных на другое устройство.
     * @param ifOtherDeviceDisconnectedAction действие, если было потеряно соединение с устройством.
     * */
    suspend fun startRecordingAndStreamVoice(
        bluetoothDataStreamApi: BluetoothDataStreamApi,
        ifOtherDeviceDisconnectedAction: () -> Unit
    )

    /** Метод предназначен для прекращения записи голоса. */
    fun stopRecording()

    /**
     * Метод предназначен для начала проигрывания полученного потока данных из вне.
     *
     * @param audioStreamChunk чанк данных для воспроизведения.
     * */
    fun playStreamAudio(audioStreamChunk: ByteArray)

    /** Метод предназначен для прекращения воспроизведения потокового аудио. */
    fun stopAudio()
}