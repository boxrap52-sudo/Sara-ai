package com.sahigma.saraai

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class GeminiClient(
    private val apiSettings: ApiSettings
) {
    private val client = OkHttpClient()

    fun askWithHistory(
        history: List<Pair<String, String>>,
        question: String,
        callback: (String) -> Unit
    ) {
        val apiKey = apiSettings.getGeminiKey()
        if (apiKey.isBlank()) {
            callback("API key missing. Please add your Gemini API key in Settings.")
            return
        }

        try {
            val contents = org.json.JSONArray()

            history.takeLast(20).forEach { (role, text) ->
                val apiRole = if (role == "Sara") "model" else "user"
                contents.put(JSONObject()
                    .put("role", apiRole)
                    .put("parts", org.json.JSONArray()
                        .put(JSONObject().put("text", text))))
            }

            contents.put(JSONObject()
                .put("role", "user")
                .put("parts", org.json.JSONArray()
                    .put(JSONObject().put("text", question))))

            val json = JSONObject().put("contents", contents)
            val body = json.toString().toRequestBody(
                "application/json; charset=utf-8".toMediaType()
            )
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent")
                .addHeader("x-goog-api-key", apiKey)
                .post(body)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    callback("Connection error: ${e.message ?: "Please try again."}")
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        val responseText = it.body?.string().orEmpty()
                        if (!it.isSuccessful) {
                            val detail = try {
                            JSONObject(responseText).optJSONObject("error")
                                ?.optString("message", "")
                        } catch (_: Exception) { "" }
                        callback("Gemini error (${it.code}): " +
                            if (detail.isNullOrBlank())
                                "Check your API key, model access, or quota."
                            else detail)
                            return
                        }
                        try {
                            val root = JSONObject(responseText)
                            val candidates = root.optJSONArray("candidates")
                            val parts = candidates?.optJSONObject(0)
                                ?.optJSONObject("content")
                                ?.optJSONArray("parts")
                            val answerBuilder = StringBuilder()

                            if (parts != null) {
                                for (i in 0 until parts.length()) {
                                    val part = parts.optJSONObject(i)
                                    val text = part?.optString("text", "").orEmpty()
                                    if (text.isNotBlank()) answerBuilder.append(text)
                                }
                            }

                            val answer = answerBuilder.toString()
                            if (answer.isBlank()) {
                                val feedback = root.optJSONObject("promptFeedback")
                                val blockReason = feedback?.optString("blockReason", "").orEmpty()
                                val candidate = candidates?.optJSONObject(0)
                                val finishReason = candidate?.optString("finishReason", "").orEmpty()

                                val diagnostic = when {
                                    blockReason.isNotBlank() ->
                                        "Gemini blocked the prompt: $blockReason"
                                    finishReason.isNotBlank() && finishReason != "STOP" ->
                                        "Gemini finished without text: $finishReason"
                                    else ->
                                        "Gemini returned no text. Check API response and model access."
                                }
                                callback(diagnostic)
                                return
                            }
                            callback(answer)
                        } catch (_: Exception) {
                            callback("Sara AI couldn't read the response. Please try again.")
                        }
                    }
                }
            })
        } catch (_: Exception) {
            callback("Unable to prepare the conversation. Please try again.")
        }
    }

    fun analyzeImage(
        question: String,
        bitmap: android.graphics.Bitmap,
        callback: (String) -> Unit
    ) {
        val apiKey = apiSettings.getGeminiKey()
        if (apiKey.isBlank()) {
            callback("API key missing. Please add your Gemini API key in Settings.")
            return
        }

        try {
            val stream = java.io.ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, stream)
            val imageData = android.util.Base64.encodeToString(stream.toByteArray(), android.util.Base64.NO_WRAP)

            val parts = org.json.JSONArray()
                .put(JSONObject().put("text", question))
                .put(JSONObject().put("inline_data", JSONObject()
                    .put("mime_type", "image/jpeg")
                    .put("data", imageData)))

            val json = JSONObject().put("contents",
                org.json.JSONArray().put(JSONObject().put("parts", parts)))

            val body = json.toString().toRequestBody(
                "application/json; charset=utf-8".toMediaType()
            )
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent")
                .addHeader("x-goog-api-key", apiKey)
                .post(body)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    callback("Connection error: ${e.message ?: "Please try again."}")
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        val responseText = it.body?.string().orEmpty()
                        if (!it.isSuccessful) {
                            val detail = try {
                            JSONObject(responseText).optJSONObject("error")
                                ?.optString("message", "")
                        } catch (_: Exception) { "" }
                        callback("Gemini error (${it.code}): " +
                            if (detail.isNullOrBlank())
                                "Check your API key, model access, or quota."
                            else detail)
                            return
                        }
                        try {
                            val root = JSONObject(responseText)
                            val answer = root.getJSONArray("candidates")
                                .getJSONObject(0).getJSONObject("content")
                                .getJSONArray("parts").getJSONObject(0).getString("text")
                            callback(answer)
                        } catch (_: Exception) {
                            callback("Sara AI couldn't read the image response.")
                        }
                    }
                }
            })
        } catch (_: Exception) {
            callback("Unable to process this photo.")
        }
    }

    fun ask(
        question: String,
        callback: (String) -> Unit
    ) {
        val apiKey = apiSettings.getGeminiKey()

        if (apiKey.isBlank()) {
            callback("API key missing. Please add your Gemini API key in Settings.")
            return
        }

        val json = JSONObject().apply {
            put(
                "contents",
                org.json.JSONArray().put(
                    JSONObject().put(
                        "parts",
                        org.json.JSONArray().put(
                            JSONObject().put("text", question)
                        )
                    )
                )
            )
        }

        val body = json.toString().toRequestBody(
            "application/json; charset=utf-8".toMediaType()
        )

        val request = Request.Builder()
            .url(
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"
            )
            .addHeader("x-goog-api-key", apiKey)
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback("Connection error: ${e.message ?: "Please try again."}")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val responseText = it.body?.string().orEmpty()

                    if (!it.isSuccessful) {
                        val detail = try {
                            JSONObject(responseText).optJSONObject("error")
                                ?.optString("message", "")
                        } catch (_: Exception) { "" }
                        callback("Gemini error (${it.code}): " +
                            if (detail.isNullOrBlank())
                                "Check your API key, model access, or quota."
                            else detail)
                        return
                    }

                    try {
                        val root = JSONObject(responseText)
                        val answer = root
                            .getJSONArray("candidates")
                            .getJSONObject(0)
                            .getJSONObject("content")
                            .getJSONArray("parts")
                            .getJSONObject(0)
                            .getString("text")

                        callback(answer)
                    } catch (_: Exception) {
                        callback("Sara AI couldn't read the response. Please try again.")
                    }
                }
            }
        })
    }
}
