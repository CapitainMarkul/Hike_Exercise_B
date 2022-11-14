package ru.palestra.hike_exercise_b.domain.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.AudioTrack.PLAYSTATE_PLAYING
import android.media.AudioTrack.PLAYSTATE_STOPPED
import android.media.MediaPlayer
import android.media.MediaRecorder.AudioSource
import android.os.Build
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import io.reactivex.Emitter
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.palestra.hike_exercise_b.R
import ru.palestra.hike_exercise_b.domain.bluetooth.nearby_data_stream.BluetoothDataStreamApi
import ru.palestra.hike_exercise_b.domain.utils.disposeIfNeeded

/** Объект, отвечающий за работу со звуком на устройстве пользователя. */
@SuppressLint("MissingPermission")
internal class AudioManager(
    private val applicationContext: Context,
    private var lifecycleOwner: LifecycleOwner?
) : AudioManagerApi, LifecycleEventObserver {

    companion object {
        private const val DEFAULT_SAMPLING_RATE_IN_HZ = 8000
        private const val DEFAULT_CHANNEL_IN_MONO_CONFIG: Int = AudioFormat.CHANNEL_IN_DEFAULT
        private const val DEFAULT_CHANNEL_OUT_MONO_CONFIG: Int = AudioFormat.CHANNEL_OUT_MONO
        private const val DEFAULT_AUDIO_FORMAT: Int = AudioFormat.ENCODING_PCM_16BIT
        private const val DEFAULT_STREAM_TYPE: Int = AudioManager.STREAM_MUSIC
        private const val DEFAULT_TRANSFER_TYPE: Int = AudioTrack.MODE_STREAM

        /** Размер буфера, который используется для чтения/записи. */
        val MIN_BUFFER_SIZE by lazy {
            AudioTrack.getMinBufferSize(
                DEFAULT_SAMPLING_RATE_IN_HZ,
                DEFAULT_CHANNEL_OUT_MONO_CONFIG,
                DEFAULT_AUDIO_FORMAT
            )
        }
    }

    init {
        lifecycleOwner?.lifecycle?.addObserver(this)
    }

    private val doubleBufferSize by lazy { MIN_BUFFER_SIZE * 2 }

    /* Флаг активации записи звука с микрофона устройства. */
    private var isRecording = false
    private var writeBuffer: ByteArray = ByteArray(MIN_BUFFER_SIZE)

    private var emitterReadStream: Emitter<ByteArray>? = null

    private val observableReadStream: Observable<ByteArray> by lazy {
        Observable.create { emitterReadStream = it }
            .observeOn(Schedulers.computation())
    }

    private var audioStreamHandlerDisposable =
        observableReadStream.subscribe { audioStreamChunk ->
            audioPlayer.write(audioStreamChunk, 0, audioStreamChunk.size)
        }

    private val intercomClickSoundPlayer: MediaPlayer by lazy {
        MediaPlayer.create(applicationContext, R.raw.intercom_click).apply {
            isLooping = false
        }
    }

    private val audioRecorder: AudioRecord by lazy {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            AudioRecord(
                AudioSource.MIC,
                DEFAULT_SAMPLING_RATE_IN_HZ,
                DEFAULT_CHANNEL_IN_MONO_CONFIG,
                DEFAULT_AUDIO_FORMAT,
                doubleBufferSize
            )
        } else {
            AudioRecord.Builder()
                .setAudioSource(AudioSource.VOICE_COMMUNICATION)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(DEFAULT_AUDIO_FORMAT)
                        .setSampleRate(DEFAULT_SAMPLING_RATE_IN_HZ)
                        .setChannelMask(DEFAULT_CHANNEL_IN_MONO_CONFIG)
                        .build()
                )
                .setBufferSizeInBytes(doubleBufferSize)
                .build()
        }
    }

    private val audioPlayer: AudioTrack by lazy {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            AudioTrack(
                DEFAULT_STREAM_TYPE,
                DEFAULT_SAMPLING_RATE_IN_HZ,
                DEFAULT_CHANNEL_OUT_MONO_CONFIG,
                DEFAULT_AUDIO_FORMAT,
                doubleBufferSize,
                DEFAULT_TRANSFER_TYPE
            )
        } else {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setLegacyStreamType(DEFAULT_STREAM_TYPE)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(DEFAULT_AUDIO_FORMAT)
                        .setChannelMask(DEFAULT_CHANNEL_OUT_MONO_CONFIG)
                        .setSampleRate(DEFAULT_SAMPLING_RATE_IN_HZ)
                        .build()
                )
                .setBufferSizeInBytes(doubleBufferSize)
                .setTransferMode(DEFAULT_TRANSFER_TYPE)
                .build()
        }
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if (event == Lifecycle.Event.ON_DESTROY) {
            stopAudio()
            stopRecording()

            audioRecorder.release()
            audioPlayer.release()
            intercomClickSoundPlayer.release()

            audioStreamHandlerDisposable?.disposeIfNeeded()

            lifecycleOwner?.lifecycle?.removeObserver(this)
            lifecycleOwner = null
        }
    }

    override fun playIntercomSound() {
        intercomClickSoundPlayer.start()
    }

    override suspend fun startRecordingAndStreamVoice(
        bluetoothDataStreamApi: BluetoothDataStreamApi,
        ifOtherDeviceDisconnectedAction: () -> Unit
    ) {
        isRecording = true
        writeBuffer = ByteArray(MIN_BUFFER_SIZE)

        audioRecorder.startRecording()

        withContext(Dispatchers.IO) {
            while (isRecording) {
                try {
                    audioRecorder.read(writeBuffer, 0, MIN_BUFFER_SIZE)
                    bluetoothDataStreamApi.sendByteStream(writeBuffer, ifOtherDeviceDisconnectedAction)
                } catch (e: Exception) {
                    stopRecording()

                    /* Уведомляем пользователя об ошибке. */
                    Toast.makeText(
                        applicationContext,
                        applicationContext.getString(R.string.fatal_send_data_error),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    override fun stopRecording() {
        isRecording = false
        audioRecorder.stop()
    }

    override fun playAudio() {
        if (audioPlayer.playState == PLAYSTATE_STOPPED) {
            audioPlayer.play()
        }
    }

    override fun stopAudio() {
        if (audioPlayer.playState == PLAYSTATE_PLAYING) {
            audioPlayer.stop()
        }
    }

    override fun updateStreamAudio(audioStreamChunk: ByteArray) {
        emitterReadStream?.onNext(audioStreamChunk)
    }
}