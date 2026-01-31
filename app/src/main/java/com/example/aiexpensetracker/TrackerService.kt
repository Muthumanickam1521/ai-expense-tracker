package com.example.aiexpensetracker

import android.app.*
import android.content.Intent
import android.media.*
import android.os.*
import org.vosk.*
import org.json.JSONObject
import kotlin.concurrent.thread
import android.util.Log

import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

class TrackerService : Service() {

    private lateinit var model: Model
    private lateinit var recognizer: Recognizer
    private lateinit var audioRecord: AudioRecord
    private var running = true

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        android.util.Log.e("TRACKER", "🔥 TrackerService onStartCommand CALLED")

        Log.d("TRACKER", "filesDir = ${filesDir.absolutePath}")

        Log.d("TRACKER", "Service started")

        startForegroundNotification()

        try {
            startWakeWord()
        } catch (e: Exception) {
            Log.e("TRACKER", "CRASH", e)
        }

        return START_STICKY
    }

    private fun prepareModel(): String {
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
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { output ->
                            zip.copyTo(output)
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }

        return modelDir.absolutePath
    }


    private fun startForegroundNotification() {
        val channelId = "tracker_channel"
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                channelId, "AI Tracker",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
        val notification = Notification.Builder(this, channelId)
            .setContentTitle("AI Expense Tracker")
            .setContentText("Say: hey tracker")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()
        startForeground(1, notification)
    }

    private fun startWakeWord() {
        Log.d("TRACKER", "Loading Vosk model")

        val modelPath = prepareModel()
        Log.d("TRACKER", "Model files = ${File(modelPath).list()?.toList()}")
        model = Model(modelPath)

        recognizer = Recognizer(model, 16000f,
            """["hey tracker"]"""
        )

        Log.d("TRACKER", "Model & recognizer ready")

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            16000,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            4096
        )

        audioRecord.startRecording()

        Log.d("TRACKER", "Audio recording started")

        thread {
            val buffer = ShortArray(2048)
            while (running) {

                val read = audioRecord.read(buffer, 0, buffer.size)

                Log.d("TRACKER", "Audio read bytes=$read")

                if (read > 0 && recognizer.acceptWaveForm(buffer, read)) {
                    val text = JSONObject(recognizer.result)
                        .optString("text")

                    Log.d("TRACKER", "Recognized text=$text")

                    if (text.contains("hey tracker")) {
                        openApp()

                        Log.d("TRACKER", "🔥 WAKE WORD DETECTED")

                        stopSelf()
                        break
                    }
                }
            }
        }
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("wake_word_detected", true)
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        running = false
        audioRecord.stop()
        audioRecord.release()
        recognizer.close()
        model.close()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
