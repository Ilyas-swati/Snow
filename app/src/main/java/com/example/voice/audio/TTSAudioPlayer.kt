package com.example.voice.audio

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.io.FileOutputStream

class TTSAudioPlayer(
    private val context: Context,
    private val onPlaybackStarted: () -> Unit,
    private val onPlaybackCompleted: () -> Unit,
    private val onAmplitudeChanged: (Float) -> Unit,
    private val onError: (String) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var mediaPlayer: MediaPlayer? = null
    private var isCurrentlyPlaying = false
    private var amplitudeRunnable: Runnable? = null

    fun playAudio(audioBytes: ByteArray, mimeType: String = "audio/mpeg") {
        stopPlayback()

        try {
            val extension = if (mimeType.contains("wav")) "wav" else "mp3"
            val tempFile = File(context.cacheDir, "snow_tts_stream.$extension")
            FileOutputStream(tempFile).use { fos ->
                fos.write(audioBytes)
                fos.flush()
            }

            mediaPlayer = MediaPlayer().apply {
                setDataSource(tempFile.absolutePath)
                setOnPreparedListener { mp ->
                    try {
                        mp.start()
                        isCurrentlyPlaying = true
                        mainHandler.post { onPlaybackStarted() }
                        startAmplitudeEmulation()
                    } catch (e: Exception) {
                        Log.e("TTSAudioPlayer", "Error starting playback", e)
                        handleError("Playback start failed: ${e.message}")
                    }
                }
                setOnCompletionListener {
                    cleanup()
                    mainHandler.post { onPlaybackCompleted() }
                }
                setOnErrorListener { _, what, extra ->
                    cleanup()
                    handleError("MediaPlayer error ($what, $extra)")
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e("TTSAudioPlayer", "Failed to setup audio playback", e)
            cleanup()
            handleError("Audio setup error: ${e.message}")
        }
    }

    fun stopPlayback() {
        if (!isCurrentlyPlaying && mediaPlayer == null) return
        cleanup()
    }

    private fun startAmplitudeEmulation() {
        stopAmplitudeEmulation()
        var step = 0
        amplitudeRunnable = object : Runnable {
            override fun run() {
                val mp = mediaPlayer
                if (isCurrentlyPlaying && mp != null && mp.isPlaying) {
                    // Generate natural undulating speech amplitude curve between 3f and 10f
                    step++
                    val wave1 = Math.sin(step * 0.45).toFloat()
                    val wave2 = Math.cos(step * 0.22).toFloat()
                    val simulatedDb = 4.5f + (wave1 * 2.2f) + (wave2 * 1.5f)
                    onAmplitudeChanged(simulatedDb.coerceIn(1.0f, 10.0f))
                    mainHandler.postDelayed(this, 60)
                } else {
                    onAmplitudeChanged(0f)
                }
            }
        }
        mainHandler.post(amplitudeRunnable!!)
    }

    private fun stopAmplitudeEmulation() {
        amplitudeRunnable?.let { mainHandler.removeCallbacks(it) }
        amplitudeRunnable = null
        onAmplitudeChanged(0f)
    }

    private fun cleanup() {
        isCurrentlyPlaying = false
        stopAmplitudeEmulation()
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                reset()
                release()
            }
        } catch (e: Exception) {
            Log.w("TTSAudioPlayer", "Error releasing MediaPlayer", e)
        }
        mediaPlayer = null
    }

    private fun handleError(message: String) {
        onError(message)
    }
}
