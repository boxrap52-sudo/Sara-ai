package com.sahigma.saraai

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class OpenAIClient(private val apiSettings: ApiSettings) {

    private val client = OkHttpClient()

    fun askWithHistory(
        history: List<Pair<String, String>>,
        question: String,
        callback: (String) -> Unit
    ) {
        val apiKey = apiSettings.getOpenAIKey()
        if (apiKey.isBlank()) {
            callback("OpenAI API key missing. Add it in API Settings.")
            return
        }

        try {
            val messages = JSONArray()
            messages.put(
                JSONObject()
                    .put("role", "system")
                    .put("content", "You are Sara AI, a helpful assistant. Reply clearly and naturally.")
            )

            history.takeLast(20).forEach { (sender, text) ->
                val role = if (sender == "Sara") "assistant" else "user"
                messages.put(
                    JSONObject()
                        .put("role", role)
                        .put("content", text)
                )
            }

            messages.put(
                JSONObject()
                    .put("role", "user")
                    .put("content", question)
            )

            val json = JSONObject()
                .put("model", "gpt-4.1-mini")
                .put("messages", messages)

            val body = json.toString().toRequestBody(
                "application/json; charset=utf-8".toMediaType()
            )

            val request = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(body)
                .build()

            client.newCall(request).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: IOException) {
                    callback("OpenAI connection error: ${e.message ?: "Please try again."}")
                }

                override fun onResponse(
                    call: okhttp3.Call,
                    response: okhttp3.Response
                ) {
                    response.use {
                        val responseText = it.body?.string().orEmpty()

                        if (!it.isSuccessful) {
                            val detail = try {
                                JSONObject(responseText)
                                    .optJSONObject("error")
                                    ?.optString("message", "")
                            } catch (_: Exception) {
                                ""
                            }
                            callback(
                                "OpenAI error (${it.code}): " +
                                    if (detail.isNullOrBlank()) "Check API key, model access, or quota."
                                    else detail
                            )
                            return
                        }

                        try {
                            val answer = JSONObject(responseText)
                                .getJSONArray("choices")
                                .getJSONObject(0)
                                .getJSONObject("message")
                                .getString("content")
                            callback(answer)
                        } catch (_: Exception) {
                            callback("Sara AI couldn't read the OpenAI response.")
                        }
                    }
                }
            })
        } catch (_: Exception) {
            callback("Unable to prepare the OpenAI request.")
        }
    }
}
