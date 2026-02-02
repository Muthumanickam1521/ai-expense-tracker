package com.example.aiexpensetracker

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.aiexpensetracker.ui.theme.AIExpenseTrackerTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_WAKE_WORD_DETECTED = "wake_word_detected"
    }

    private val micPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startTracker()
                initWhisper() // POINT 7
            }
        }

    private var whisperCtx: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)

        val wakeWordDetected = intent.getBooleanExtra(EXTRA_WAKE_WORD_DETECTED, false)

        setContent {
            AIExpenseTrackerTheme {
                var isGreen by remember { mutableStateOf(wakeWordDetected) }

                // Automatically turn back to gray after 5 seconds
                LaunchedEffect(wakeWordDetected) {
                    if (wakeWordDetected) {
                        isGreen = true
                        delay(5000) // 5 seconds
                        isGreen = false
                    }
                }

                BoxWithConstraints(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    val circleSize = minOf(maxWidth, maxHeight) / 2
                    Box(
                        modifier = Modifier
                            .size(circleSize)
                            .background(
                                color = if (isGreen) Color.Green else Color.Gray,
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                    )
                }
            }
        }
    }

    private fun startTracker() {
        startForegroundService(Intent(this, TrackerService::class.java))
    }

    // POINT 7: Runtime Whisper usage
    private fun initWhisper() {
        whisperCtx = WhisperLib.initContextFromAsset(
            assets,
            "models/ggml-tiny.bin"
        )

        // dummy audio buffer placeholder (replace with real mic audio)
        val dummyAudio = FloatArray(16000) { 0f }

        WhisperLib.fullTranscribe(
            whisperCtx,
            Runtime.getRuntime().availableProcessors(),
            dummyAudio
        )

    }
}