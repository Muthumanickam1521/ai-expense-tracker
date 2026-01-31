package com.example.aiexpensetracker

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.aiexpensetracker.ui.theme.AIExpenseTrackerTheme

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_WAKE_WORD_DETECTED = "wake_word_detected"
    }

    private val micPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startTracker()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)

        val wakeWordDetected = intent.getBooleanExtra(EXTRA_WAKE_WORD_DETECTED, false)

        setContent {
            AIExpenseTrackerTheme {
                BoxWithConstraints(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    val circleSize = minOf(maxWidth, maxHeight) / 2
                    Box(
                        modifier = Modifier
                            .size(circleSize)
                            .background(
                                color = if (wakeWordDetected) Color.Green else Color.Gray,
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
}