package com.example.aiexpensetracker

object WhisperNative {
    init {
        System.loadLibrary("native-lib")
    }

    external fun transcribe(
        audioPath: String,
        modelPath: String
    ): String
}
