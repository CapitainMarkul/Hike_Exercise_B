package ru.palestra.hike_exercise_b.domain.bluetooth.nearby_data_stream

import android.bluetooth.BluetoothSocket
import io.reactivex.Emitter
import io.reactivex.Observable
import io.reactivex.ObservableEmitter
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import ru.palestra.hike_exercise_b.data.LocationPoint.Companion.END_WORD_MARKER
import ru.palestra.hike_exercise_b.data.LocationPoint.Companion.START_WORD_MARKER
import ru.palestra.hike_exercise_b.domain.utils.disposeIfNeeded
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Объект, который отвечает за передачу данных между устройствами. */
internal class BluetoothDataStream : BluetoothDataStreamApi {

    private companion object {
        private const val VALUE_BEFORE_ENABLE_TALK_FEATURE = 1000L
        private const val PERIOD_CHECK_INCOMING_TRANSMISSION_STATE = 1000L
    }

    /* Время последней полученной передачи. */
    private var lastDataVoiceChunkObtainedTime: Long = System.currentTimeMillis()

    private var resultRawString: String = ""

    private var voiceBuffer: ByteArray = byteArrayOf()
    private var voiceBufferCounter: Int = 0

    private var locationBuffer = mutableListOf<Byte>()

    private var ifOtherDeviceDisconnectedAction: (() -> Unit)? = null
    private var bluetoothConnectionSocket: BluetoothSocket? = null

    private var emitterWriteStream: Emitter<ByteArray>? = null

    private val observableWrite: Observable<ByteArray> =
        Observable.create { emitterWriteStream = it }

    private val observableReadStream: Observable<Byte> by lazy {
        Observable.create<Byte> { emitter ->
            bluetoothConnectionSocket?.let { socket ->
                while (!emitter.isDisposed) {
                    try {
                        if (socket.isConnected) {
                            emitter.onNext(socket.inputStream.read().toByte())
                        }
                    } catch (_: Exception) {
                        notifierUiAboutConnectionError?.onNext(Unit)
                    }
                }
            }
        }
    }

    /* Для безопасного обновления UI при ошибках в других подписках. */
    private var notifierUiAboutConnectionError: ObservableEmitter<Unit>? = null
    private val mainThreadDisposable =
        Observable.create<Unit> { notifierUiAboutConnectionError = it }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                ifOtherDeviceDisconnectedAction?.invoke()
            }

    private var readStreamDisposable: Disposable? = null
    private var observeIncomingTransmissionStateDisposable: Disposable? = null
    private var writeStreamDisposable: Disposable? =
        observableWrite
            .subscribeOn(Schedulers.io())
            .subscribe { bytes ->
                bluetoothConnectionSocket?.also { socket ->
                    if (socket.isConnected) {
                        try {
                            socket.outputStream.write(bytes)
                            socket.outputStream.flush()
                        } catch (e: IOException) {
                            e.printStackTrace()
                        } finally {
                            if (!socket.isConnected) {
                                notifierUiAboutConnectionError?.onNext(Unit)
                                closeAllConnectionsIfNeeded()
                            }
                        }
                    }
                }
            }

    override fun sendByteStream(dataChunk: ByteArray, ifOtherDeviceDisconnectedAction: () -> Unit) {
        this.ifOtherDeviceDisconnectedAction = ifOtherDeviceDisconnectedAction
        emitterWriteStream?.onNext(dataChunk)
    }

    override fun observeByteStream(
        byteBufferSize: Int,
        connectionSocket: BluetoothSocket,
        onObtainedVoiceChunkDataAction: (ByteArray) -> Unit,
        onObtainedLocationRawDataAction: (String) -> Unit,
        onErrorAction: (Throwable) -> Unit
    ) {
        dropVoiceBuffer(byteBufferSize)

        bluetoothConnectionSocket = connectionSocket

        readStreamDisposable = observableReadStream
            .doOnNext { readByte ->
                handleByteData(readByte, byteBufferSize)
            }
            .filter { voiceBufferCounter >= byteBufferSize || resultRawString.isNotEmpty() }
            .map {
                val resultPair = Pair(
                    if (voiceBufferCounter >= byteBufferSize) byteArrayOf(*voiceBuffer) else null, resultRawString
                )

                dropVoiceBuffer(byteBufferSize)

                resultPair
            }
            .doOnNext {
                /* Дропаем состояние строки. */
                resultRawString = ""
            }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { pairResult ->
                    pairResult.first?.let { soundRawData ->
                        lastDataVoiceChunkObtainedTime = System.currentTimeMillis()
                        onObtainedVoiceChunkDataAction(soundRawData)
                    }

                    if (pairResult.second.isNotEmpty()) {
                        onObtainedLocationRawDataAction(pairResult.second)
                    }
                },
                { error ->
                    onErrorAction(error)
                    error.printStackTrace()
                }
            )
    }

    override fun observeIncomingTransmissionState(onIncomingTransmissionChangeStateAction: (Boolean) -> Unit) {
        observeIncomingTransmissionStateDisposable = Observable
            .interval(PERIOD_CHECK_INCOMING_TRANSMISSION_STATE, TimeUnit.MILLISECONDS)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                onIncomingTransmissionChangeStateAction(
                    System.currentTimeMillis() - lastDataVoiceChunkObtainedTime >= VALUE_BEFORE_ENABLE_TALK_FEATURE
                )
            }
    }

    override fun closeStream() {
        closeAllConnectionsIfNeeded()
    }

    override fun onDestroy() {
        ifOtherDeviceDisconnectedAction = null
        closeAllConnectionsIfNeeded()

        mainThreadDisposable.disposeIfNeeded()
        readStreamDisposable?.disposeIfNeeded()
        writeStreamDisposable?.disposeIfNeeded()
        observeIncomingTransmissionStateDisposable?.disposeIfNeeded()
    }

    private fun closeAllConnectionsIfNeeded() {
        bluetoothConnectionSocket?.also { socket ->
            if (!socket.isConnected) {
                socket.inputStream.close()
                socket.outputStream.close()
                socket.close()
            }
        }
    }

    private fun handleByteData(readByte: Byte, byteBufferSize: Int) {
        if (readByte.toInt().toChar() == START_WORD_MARKER.first()) {
            locationBuffer.add(readByte)
        } else if (locationBuffer.isNotEmpty()) {
            val readSymbol = readByte.toInt().toChar()
            val lastSymbol = locationBuffer[locationBuffer.size - 1].toInt().toChar()

            if (locationBuffer.size < START_WORD_MARKER.length && readSymbol == START_WORD_MARKER[locationBuffer.size] && readSymbol != lastSymbol) {
                locationBuffer.add(readByte)
            } else if (locationBuffer.size >= START_WORD_MARKER.length) {
                /* Нашли строку с данными, пишем к себе. */
                locationBuffer.add(readByte)

                if (readSymbol == END_WORD_MARKER.last() && lastSymbol == END_WORD_MARKER[END_WORD_MARKER.length - 2]) {
                    /* Нашли слово 'end'. */
                    resultRawString = String(
                        locationBuffer.drop(START_WORD_MARKER.length).dropLast(END_WORD_MARKER.length).toByteArray()
                    )

                    locationBuffer.clear()
                }
            } else {
                locationBuffer.add(readByte)

                /* Выплевываем все, что сохранили к себе. */
                val iterator = locationBuffer.iterator()
                while (iterator.hasNext() && voiceBufferCounter <= byteBufferSize) {
                    writeToVoiceBuffer(iterator.next())

                    iterator.remove()
                }
            }
        } else {
            /* Это голос - записываем с буффер голоса. */
            writeToVoiceBuffer(readByte)
        }
    }

    private fun writeToVoiceBuffer(readByte: Byte) {
        voiceBuffer[voiceBufferCounter] = readByte
        voiceBufferCounter++
    }

    private fun dropVoiceBuffer(byteBufferSize: Int) {
        voiceBufferCounter = 0
        voiceBuffer = ByteArray(byteBufferSize)
    }
}