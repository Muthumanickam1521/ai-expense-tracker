package com.example.aiexpensetracker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import kotlin.concurrent.thread

class TrackerService : Service() {

    @Volatile
    private var running = false

    private lateinit var model: Model
    private lateinit var recognizer: Recognizer
    private lateinit var audioRecord: AudioRecord

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("TRACKER", "🚀 TrackerService started")
        startWakeWord()
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d("TRACKER", "🧹 Service destroyed")
        running = false
        cleanupWakeResources()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundNotification() {
        val channelId = "tracker_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "AI Expense Tracker",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }

        val notification = Notification.Builder(this, channelId)
            .setContentTitle("AI Expense Tracker")
            .setContentText("Listening for: hey tracker")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()

        startForeground(1, notification)
    }

    @Synchronized
    private fun startWakeWord() {
        Log.d("TRACKER", "🔁 startWakeWord")

        running = true
        cleanupWakeResources()

        val modelPath = prepareVoskModel()
        model = Model(modelPath)

        recognizer = Recognizer(
            model,
            16000f,
            """["hey tracker"]"""
        )

        val minBuf = AudioRecord.getMinBufferSize(
            16000,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        if (minBuf <= 0) {
            Log.e("TRACKER", "❌ AudioRecord not supported")
            return
        }

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            16000,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuf
        )

        audioRecord.startRecording()
        Log.d("TRACKER", "🎙️ Wake mic active")

        thread(name = "WakeWordThread") {
            val buffer = ShortArray(2048)
            while (running) {
                val read = audioRecord.read(buffer, 0, buffer.size)
                if (read > 0 && recognizer.acceptWaveForm(buffer, read)) {
                    val text = JSONObject(recognizer.result).optString("text", "")
                    if (text.contains("hey tracker")) {
                        handleWakeDetected()
                    }
                }
            }
        }
    }

    @Synchronized
    private fun handleWakeDetected() {
        Log.d("TRACKER", "🔥 WAKE WORD DETECTED")

        running = false
        try {
            audioRecord.stop()
            audioRecord.release()
        } catch (_: Exception) {}

        try {
            recognizer.close()
            model.close()
        } catch (_: Exception) {}

        sendBroadcast(Intent("com.example.aiexpensetracker.WAKE_ACTIVATED"))

        val audioFile = File(filesDir, "command_${System.currentTimeMillis()}.wav")
        recordCommandWav(audioFile, 4000)

        Log.d("TRACKER", "🎧 Command recorded: ${audioFile.absolutePath}")

        val uiIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("audio_path", audioFile.absolutePath)
        }
        startActivity(uiIntent)

        sendBroadcast(Intent("com.example.aiexpensetracker.WAKE_DEACTIVATED"))

        // ❌ DO NOT restart wake word here
    }

    private fun recordCommandWav(outFile: File, durationMs: Int) {
        val sampleRate = 16000
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            4096
        )

        val pcmBuffer = ByteArrayOutputStream()
        val buffer = ByteArray(2048)

        recorder.startRecording()
        val endTime = System.currentTimeMillis() + durationMs

        while (System.currentTimeMillis() < endTime) {
            val read = recorder.read(buffer, 0, buffer.size)
            if (read > 0) pcmBuffer.write(buffer, 0, read)
        }

        recorder.stop()
        recorder.release()

        writeWavFile(outFile, pcmBuffer.toByteArray(), sampleRate)
    }

    private fun writeWavFile(file: File, pcm: ByteArray, sampleRate: Int) {
        val header = ByteArray(44)
        val totalDataLen = pcm.size + 36
        val byteRate = sampleRate * 2

        fun writeInt(offset: Int, value: Int) {
            header[offset] = (value and 0xff).toByte()
            header[offset + 1] = (value shr 8).toByte()
            header[offset + 2] = (value shr 16).toByte()
            header[offset + 3] = (value shr 24).toByte()
        }

        fun writeShort(offset: Int, value: Short) {
            header[offset] = (value.toInt() and 0xff).toByte()
            header[offset + 1] = (value.toInt() shr 8).toByte()
        }

        "RIFF".forEachIndexed { i, c -> header[i] = c.code.toByte() }
        writeInt(4, totalDataLen)
        "WAVEfmt ".forEachIndexed { i, c -> header[8 + i] = c.code.toByte() }
        writeInt(16, 16)
        writeShort(20, 1)
        writeShort(22, 1)
        writeInt(24, sampleRate)
        writeInt(28, byteRate)
        writeShort(32, 2)
        writeShort(34, 16)
        "data".forEachIndexed { i, c -> header[36 + i] = c.code.toByte() }
        writeInt(40, pcm.size)

        FileOutputStream(file).use {
            it.write(header)
            it.write(pcm)
        }
    }

    private fun prepareVoskModel(): String {
        val modelDir = File(filesDir, "vosk-model")
        if (modelDir.exists() && modelDir.listFiles()?.isNotEmpty() == true) {
            return modelDir.absolutePath
        }

        modelDir.mkdirs()
        assets.open("model.zip").use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val outFile = File(modelDir, entry.name)
                    if (entry.isDirectory) outFile.mkdirs()
                    else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { zip.copyTo(it) }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        return modelDir.absolutePath
    }

    private fun cleanupWakeResources() {
        try {
            if (::audioRecord.isInitialized) {
                audioRecord.stop()
                audioRecord.release()
            }
        } catch (_: Exception) {}

        try {
            if (::recognizer.isInitialized) recognizer.close()
            if (::model.isInitialized) model.close()
        } catch (_: Exception) {}
    }
}
