package com.sahigma.saraai

import android.content.Context

class ApiSettings(context: Context) {

    private val prefs = context.getSharedPreferences(
        "sara_ai_settings",
        Context.MODE_PRIVATE
    )

    fun saveGeminiKey(key: String) {
        prefs.edit().putString("gemini_api_key", key.trim()).apply()
    }

    fun getGeminiKey(): String {
        return prefs.getString("gemini_api_key", "") ?: ""
    }

    fun saveOpenAIKey(key: String) {
        prefs.edit().putString("openai_api_key", key.trim()).apply()
    }

    fun getOpenAIKey(): String {
        return prefs.getString("openai_api_key", "") ?: ""
    }

    fun saveElevenLabsKey(key: String) {
        prefs.edit().putString("elevenlabs_api_key", key.trim()).apply()
    }

    fun getElevenLabsKey(): String {
        return prefs.getString("elevenlabs_api_key", "") ?: ""
    }
}
