package ru.palestra.hike_exercise_b.domain.bluetooth.nearby_data_stream

import android.bluetooth.BluetoothSocket
import ru.palestra.hike_exercise_b.domain.bluetooth.destroy.BluetoothDestroyApi

/** Описание объекта, который отвечает за передачу данных между устройствами. */
interface BluetoothDataStreamApi : BluetoothDestroyApi {

    /**
     * Метод для передачи массива байт сопряженному устройству.
     *
     * @param dataChunk массив байт.
     * @param ifOtherDeviceDisconnectedAction действие, если было потеряно соединение с устройством.
     * */
    fun sendByteStream(dataChunk: ByteArray, ifOtherDeviceDisconnectedAction: () -> Unit)

    /**
     * Метод для подписки на получение массива передаваемых байт.
     *
     * @param byteBufferSize размер буфера для чтения байт.
     * @param connectionSocket объект подключения, полученный при сопряжении устройств.
     * @param onObtainedVoiceChunkDataAction действие, вызывается при получении порции байт голоса.
     * @param onObtainedLocationRawDataAction действие, вызывается при получении "сырой" геолокации.
     * @param onErrorAction действие, вызывается в случае возникновения ошибки.
     * */
    fun observeByteStream(
        byteBufferSize: Int,
        connectionSocket: BluetoothSocket,
        onObtainedVoiceChunkDataAction: (ByteArray) -> Unit,
        onObtainedLocationRawDataAction: (String) -> Unit,
        onErrorAction: (Throwable) -> Unit
    )

    /**
     * Метод для подписки на событие окончания передачи данных со стороны другого устройства.
     *
     * @param onIncomingTransmissionChangeStateAction действие с результатом окончания передачи данных.
     * */
    fun observeIncomingTransmissionState(onIncomingTransmissionChangeStateAction: (Boolean) -> Unit)

    /** Метод для закрытия потока данных. */
    fun closeStream()
}