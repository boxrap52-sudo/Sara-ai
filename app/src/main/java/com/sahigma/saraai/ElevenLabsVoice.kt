package com.sahigma.saraai

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class ElevenLabsVoice(
    private val context: Context,
    private val apiSettings: ApiSettings
) {
    private val client = OkHttpClient()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null

    // Default ElevenLabs voice ID; can be changed later.
    private val voiceId = "EXAVITQu4vr4xnSDxMaL"

    fun speak(text: String) {
        val apiKey = apiSettings.getElevenLabsKey()

        if (apiKey.isBlank()) {
            mainHandler.post {
                android.widget.Toast.makeText(
                    context,
                    "Add your ElevenLabs API key in API Settings.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
            return
        }

        val payload = JSONObject().apply {
            put("text", text)
            put("model_id", "eleven_multilingual_v2")
            put(
                "voice_settings",
                JSONObject().apply {
                    put("stability", 0.5)
                    put("similarity_boost", 0.75)
                }
            )
        }

        val request = Request.Builder()
            .url("https://api.elevenlabs.io/v1/text-to-speech/$voiceId")
            .addHeader("xi-api-key", apiKey)
            .addHeader("Accept", "audio/mpeg")
            .post(
                payload.toString().toRequestBody(
                    "application/json; charset=utf-8".toMediaType()
                )
            )
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                showError("Voice connection failed.")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        showError("ElevenLabs error (${it.code}). Check API key or quota.")
                        return
                    }

                    val audioBytes = it.body?.bytes()
                    if (audioBytes == null || audioBytes.isEmpty()) {
                        showError("No voice audio was received.")
                        return
                    }

                    mainHandler.post {
                        playAudio(audioBytes)
                    }
                }
            }
        })
    }

    private fun playAudio(audioBytes: ByteArray) {
        try {
            player?.release()
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(
                    java.io.ByteArrayInputStream(audioBytes).let { stream ->
                        val tempFile = java.io.File.createTempFile(
                            "sara_voice_", ".mp3", context.cacheDir
                        )
                        tempFile.outputStream().use { stream.copyTo(it) }
                        tempFile.absolutePath
                    }
                )
                setOnCompletionListener { mp ->
                    mp.release()
                    player = null
                }
                setOnErrorListener { mp, _, _ ->
                    mp.release()
                    player = null
                    true
                }
                setOnPreparedListener { it.start() }
                prepareAsync()
            }
        } catch (_: Exception) {
            showError("Could not play the voice response.")
        }
    }

    private fun showError(message: String) {
        mainHandler.post {
            android.widget.Toast.makeText(
                context,
                message,
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    fun stop() {
        mainHandler.post {
            player?.release()
            player = null
        }
    }
}
