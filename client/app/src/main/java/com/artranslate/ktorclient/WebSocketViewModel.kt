package com.example.text

import android.annotation.SuppressLint
import android.app.Application
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.*
import okio.ByteString
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WebSocketViewModel(application: Application) : AndroidViewModel(application) {
    private val _receivedMessage = MutableStateFlow("")
    val receivedMessage: StateFlow<String> = _receivedMessage

    private lateinit var webSocket: WebSocket
    private val client = OkHttpClient()

    private var audioRecord: AudioRecord? = null
    private val sampleRate = 16000
    private val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )

    private val vad = VoiceActivityDetector()
    private val speechBuffer = mutableListOf<Short>()

    init {
        connectWebSocket()
    }

    private fun connectWebSocket() {
        val request = Request.Builder().url("ws://192.168.3.124:8000/whisper").build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                _receivedMessage.value = text
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("WebSocket", "Error: ${t.message}")
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun startAudioCapture() {
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
        audioRecord?.startRecording()

        viewModelScope.launch(Dispatchers.IO) {
            val buffer = ShortArray(bufferSize)
            while (this.isActive) {
                val readCount = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (readCount > 0) {
                    speechBuffer.addAll(buffer.take(readCount))
                    val ended = vad.processBuffer(buffer.take(readCount).toShortArray())
                    if (ended) {
                        val chunk = speechBuffer.toShortArray()
                        speechBuffer.clear()
                        launchRecognition(chunk)
                    }
                }
            }
        }
    }

    private fun launchRecognition(audioData: ShortArray) {
        viewModelScope.launch(Dispatchers.IO) {
            val byteBuffer = ByteBuffer.allocate(audioData.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (s in audioData) {
                byteBuffer.putShort(s)
            }
            val audioBytes = byteBuffer.array()
            val byteString = ByteString.of(*audioBytes)
            webSocket.send(byteString)
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        webSocket.close(1000, "ViewModel cleared")
    }
}



