package com.example.aiexpensetracker

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File

class MainActivity : ComponentActivity() {

    private val wakeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // handled in composable
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mic permission
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                1001
            )
        }

        // Start service
        val serviceIntent = Intent(this, TrackerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        val audioPath = intent.getStringExtra("audio_path")
        val commandText = intent.getStringExtra("command_text")

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AudioResultScreen(audioPath, commandText)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter().apply {
            addAction("com.example.aiexpensetracker.WAKE_ACTIVATED")
            addAction("com.example.aiexpensetracker.WAKE_DEACTIVATED")
        }
        registerReceiver(wakeReceiver, filter)
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(wakeReceiver)
        } catch (_: Exception) {}

        // Ask service to restart wake listening
        val intent = Intent(this, TrackerService::class.java)
        intent.putExtra("restart_wake", true)
        startService(intent)
    }
}

@Composable
fun AudioResultScreen(audioPath: String?, commandText: String?) {
    val context = LocalContext.current
    var status by remember { mutableStateOf("Idle") }
    var isWakeActive by remember { mutableStateOf(false) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    "com.example.aiexpensetracker.WAKE_ACTIVATED" -> isWakeActive = true
                    "com.example.aiexpensetracker.WAKE_DEACTIVATED" -> isWakeActive = false
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction("com.example.aiexpensetracker.WAKE_ACTIVATED")
            addAction("com.example.aiexpensetracker.WAKE_DEACTIVATED")
        }

        context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)

        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    LaunchedEffect(audioPath) {
        audioPath?.let { path ->
            if (File(path).exists()) {
                try {
                    val player = MediaPlayer().apply {
                        setDataSource(path)
                        prepare()
                        start()
                    }
                    mediaPlayer = player
                    status = "Auto-playing recorded audio"
                    player.setOnCompletionListener {
                        status = "Playback completed"
                    }
                } catch (_: Exception) {
                    status = "Playback error"
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { mediaPlayer?.release() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(if (isWakeActive) Color.Green else Color.Red),
            contentAlignment = Alignment.Center
        ) {
            Text(if (isWakeActive) "ACTIVE" else "IDLE", color = Color.White)
        }

        Text(
            if (isWakeActive) "Wake Word Activated ✅" else "Waiting for wake word…",
            style = MaterialTheme.typography.headlineSmall
        )

        commandText?.let { Text("Command: $it") }
        Text(audioPath ?: "No audio")

        Button(onClick = {
            if (audioPath != null && File(audioPath).exists()) {
                try {
                    mediaPlayer?.release()
                    val player = MediaPlayer().apply {
                        setDataSource(audioPath)
                        prepare()
                        start()
                    }
                    mediaPlayer = player
                    status = "Playing audio"
                    player.setOnCompletionListener {
                        status = "Playback completed"
                    }
                } catch (_: Exception) {
                    status = "Playback error"
                }
            }
        }) {
            Text("Play Recorded Audio")
        }

        Text(status)
    }
}
