package com.example.aiexpensetracker

import android.content.res.AssetManager

object WhisperLib {

    init {
        System.loadLibrary("whisper")
    }

    external fun initContextFromAsset(
        assetManager: AssetManager,
        modelPath: String
    ): Long

    external fun fullTranscribe(
        ctx: Long,
        nSamples: Int,
        audio: FloatArray
    )

}

