package com.sahigma.saraai

import android.Manifest
import android.app.Activity
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.SpeechRecognizer
import android.speech.RecognitionListener
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.content.Intent
import android.provider.MediaStore
import android.speech.RecognizerIntent
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var apiSettings: ApiSettings
    private lateinit var gemini: GeminiClient
    private lateinit var openAI: OpenAIClient
    private lateinit var providerButton: Button
    private val providerPrefs by lazy {
        getSharedPreferences("sara_provider_settings", MODE_PRIVATE)
    }
    private var selectedProvider = "Gemini"
    private lateinit var voice: ElevenLabsVoice
    private lateinit var messages: LinearLayout
    private lateinit var input: EditText
    private lateinit var scroll: ScrollView
    private val conversationHistory = mutableListOf<Pair<String, String>>()
    private val historyPrefs by lazy {
        getSharedPreferences("sara_chat_history", MODE_PRIVATE)
    }

    private fun saveConversationHistory() {
        val encoded = conversationHistory.joinToString("\n") {
            android.util.Base64.encodeToString(
                (it.first + "\u0000" + it.second).toByteArray(Charsets.UTF_8),
                android.util.Base64.NO_WRAP
            )
        }
        historyPrefs.edit().putString("messages", encoded).apply()
    }

    private fun loadConversationHistory() {
        val saved = historyPrefs.getString("messages", "") ?: ""
        if (saved.isEmpty()) return
        conversationHistory.clear()
        saved.split("\n").forEach { item ->
            try {
                val decoded = String(
                    android.util.Base64.decode(item, android.util.Base64.NO_WRAP),
                    Charsets.UTF_8
                )
                val separator = decoded.indexOf('\u0000')
                if (separator >= 0) {
                    conversationHistory.add(
                        decoded.substring(0, separator) to decoded.substring(separator + 1)
                    )
                }
            } catch (_: Exception) {}
        }
    }

    private val bg = Color.rgb(5, 10, 25)
    private val cyan = Color.rgb(0, 220, 255)
    private val voiceRequest = 100
    private val micPermissionRequest = 101
    private lateinit var micButton: Button
    private var openMicActive = false
    private var speechRecognizer: SpeechRecognizer? = null
    private val micHandler = Handler(Looper.getMainLooper())
    private val restartListening = Runnable { listenAgain() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        apiSettings = ApiSettings(this)
        gemini = GeminiClient(apiSettings)
        openAI = OpenAIClient(apiSettings)
        selectedProvider = providerPrefs.getString("provider", "Gemini") ?: "Gemini"
        voice = ElevenLabsVoice(this, apiSettings)
        loadConversationHistory()

        window.statusBarColor = bg
        window.navigationBarColor = bg

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 12, 16, 12)
            setBackgroundColor(bg)
        }

        val header = TextView(this).apply {
            text = "S A R A   A I"
            textSize = 25f
            gravity = Gravity.CENTER
            setTextColor(cyan)
            setPadding(8, 12, 8, 12)
        }
        root.addView(header)

        val orb = TextView(this).apply {
            text = "◉"
            textSize = 54f
            gravity = Gravity.CENTER
            setTextColor(cyan)
            setShadowLayer(24f, 0f, 0f, cyan)
            setPadding(8, 0, 8, 8)
            contentDescription = "Sara AI visual indicator"
        }
        root.addView(orb, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 90
        ))
        ObjectAnimator.ofFloat(orb, View.ALPHA, 0.65f, 1f).apply {
            duration = 1100
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }
        ObjectAnimator.ofFloat(orb, View.SCALE_X, 0.94f, 1.06f).apply {
            duration = 1400
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }
        ObjectAnimator.ofFloat(orb, View.SCALE_Y, 0.94f, 1.06f).apply {
            duration = 1400
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }

        // TEMP_SARA_AVATAR_UI
        val avatar = TextView(this).apply {
            text = "👩🏻‍💻"
            textSize = 76f
            gravity = Gravity.CENTER
            setPadding(8, 12, 8, 8)
            contentDescription = "Temporary Sara AI avatar"
        }
        root.addView(avatar, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 150
        ))

        val tapToSpeak = Button(this).apply {
            text = "✦  TAP TO SPEAK  ✦"
            textSize = 16f
            setTextColor(cyan)
            setBackgroundColor(Color.rgb(17, 35, 55))
            setOnClickListener {
                if (openMicActive) stopOpenMic() else startVoiceInput()
            }
        }
        root.addView(tapToSpeak, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        val settingsButton = Button(this).apply {
            text = "⚙ API SETTINGS"
            setOnClickListener { showApiSettings() }
        }
        root.addView(settingsButton)

        providerButton = Button(this).apply {
            text = "AI PROVIDER: $selectedProvider (TAP TO SWITCH)"
            setOnClickListener {
                selectedProvider = if (selectedProvider == "Gemini") "OpenAI" else "Gemini"
                providerPrefs.edit().putString("provider", selectedProvider).apply()
                text = "AI PROVIDER: $selectedProvider (TAP TO SWITCH)"
                Toast.makeText(this@MainActivity, "Using $selectedProvider", Toast.LENGTH_SHORT).show()
            }
        }
        root.addView(providerButton)

        scroll = ScrollView(this)
        messages = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(4, 8, 4, 8)
        }
        scroll.addView(messages)
        if (conversationHistory.isNotEmpty()) {
            conversationHistory.forEach { (sender, message) ->
                addMessage(sender, message)
            }
        }
        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        input = EditText(this).apply {
            hint = "Message Sara AI..."
            setHintTextColor(Color.LTGRAY)
            setTextColor(Color.WHITE)
            setSingleLine(false)
            maxLines = 3
        }

        val camera = Button(this).apply {
            text = "＋"
            setOnClickListener { openCamera() }
        }

        val send = Button(this).apply {
            text = "➤"
            setOnClickListener { sendMessage() }
        }

        micButton = Button(this).apply {
            text = "🎙 START MIC"
            setTextColor(cyan)
            setBackgroundColor(Color.rgb(17, 35, 55))
            setOnClickListener {
                if (openMicActive) stopOpenMic() else startVoiceInput()
            }
        }

        row.addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(micButton)
        row.addView(camera)
        row.addView(send)
        root.addView(row)

        val footer = TextView(this).apply {
            text = "P O W E R E D   B Y   S A H I G M A"
            textSize = 10f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(105, 130, 160))
            setPadding(8, 12, 8, 8)
        }
        root.addView(footer)
        setContentView(root)

        addMessage("Sara", "Welcome back, Sahil! I'm Sara AI. How can I help you?")
    }

    private fun addMessage(sender: String, message: String) {
        val text = TextView(this).apply {
            this.text = "$sender: $message"
            textSize = 15.5f
            setTextColor(if (sender == "Sara") cyan else Color.WHITE)
            setPadding(14, 12, 14, 12)
        }
        messages.addView(text)
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun sendMessage() {
        val question = input.text.toString().trim()
        if (question.isEmpty()) return

        input.setText("")
        addMessage("You", question)
        addMessage("Sara", "Thinking...")

        val historySnapshot = conversationHistory.toList()
        val responseCallback: (String) -> Unit = { answer ->
            runOnUiThread {
                val last = messages.getChildAt(messages.childCount - 1) as? TextView
                if (last?.text?.toString()?.startsWith("Sara: Thinking...") == true) {
                    messages.removeView(last)
                }
                addMessage("Sara", answer)

                val isApiError =
                    answer.startsWith("Gemini error", ignoreCase = true) ||
                    answer.startsWith("OpenAI error", ignoreCase = true) ||
                    answer.startsWith("OpenAI connection error", ignoreCase = true) ||
                    answer.startsWith("Connection error", ignoreCase = true) ||
                    answer.startsWith("Sara AI couldn't", ignoreCase = true) ||
                    answer.startsWith("Unable to prepare", ignoreCase = true) ||
                    answer.startsWith("API key missing", ignoreCase = true) ||
                    answer.startsWith("OpenAI API key missing", ignoreCase = true) ||
                    answer.startsWith("Gemini blocked the prompt:", ignoreCase = true) ||
                    answer.startsWith("Gemini finished without text:", ignoreCase = true) ||
                    answer.startsWith("Gemini returned no text.", ignoreCase = true) ||
                    answer.startsWith("Sara AI received an empty response.", ignoreCase = true)

                if (!isApiError) {
                    conversationHistory.add("You" to question)
                    conversationHistory.add("Sara" to answer)
                    while (conversationHistory.size > 20) {
                        conversationHistory.removeAt(0)
                    }
                    saveConversationHistory()
                }

                if (answer.isNotBlank() && !isApiError &&
                    !answer.startsWith("Error", ignoreCase = true)) {
                    voice.speak(answer)
                }
            }
        }
        if (selectedProvider == "OpenAI") {
            openAI.askWithHistory(historySnapshot, question, responseCallback)
        } else {
            gemini.askWithHistory(historySnapshot, question, responseCallback)
        }
    }

    private fun showApiSettings() {
        val keyInput = EditText(this).apply {
            hint = "Gemini API key"
            setText(apiSettings.getGeminiKey())
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        val openAIKeyInput = EditText(this).apply {
            hint = "OpenAI API key"
            setText(apiSettings.getOpenAIKey())
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        val voiceKeyInput = EditText(this).apply {
            hint = "ElevenLabs API key (optional)"
            setText(apiSettings.getElevenLabsKey())
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 8, 24, 8)
            addView(keyInput)
            addView(openAIKeyInput)
            addView(voiceKeyInput)
        }

        android.app.AlertDialog.Builder(this)
            .setTitle("Sara AI API Settings")
            .setView(container)
            .setPositiveButton("SAVE") { _, _ ->
                apiSettings.saveGeminiKey(keyInput.text.toString())
                apiSettings.saveOpenAIKey(openAIKeyInput.text.toString())
                apiSettings.saveElevenLabsKey(voiceKeyInput.text.toString())
                Toast.makeText(
                    this,
                    "API settings saved",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        try {
            if (intent.resolveActivity(packageManager) != null) {
                startActivityForResult(intent, 102)
            } else {
                Toast.makeText(this, "No camera app found.", Toast.LENGTH_LONG).show()
            }
        } catch (_: Exception) {
            Toast.makeText(this, "Unable to open camera.", Toast.LENGTH_LONG).show()
        }
    }


    private fun handleVoiceCommand(spoken: String) {
        val command = spoken.trim().lowercase(Locale.ROOT)

        when {
            command.contains("open youtube") || command == "youtube" ||
            command.contains("youtube kholo") || command.contains("youtube khol") -> {
                startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com")))
            }

            command.contains("open whatsapp") || command == "whatsapp" ||
            command.contains("whatsapp kholo") || command.contains("whatsapp khol") -> {
                val intent = packageManager.getLaunchIntentForPackage("com.whatsapp")
                if (intent != null) startActivity(intent)
                else Toast.makeText(this, "WhatsApp installed nahi hai.", Toast.LENGTH_LONG).show()
            }

            command.contains("open chrome") || command == "chrome" ||
            command.contains("chrome kholo") || command.contains("chrome khol") -> {
                val intent = packageManager.getLaunchIntentForPackage("com.android.chrome")
                if (intent != null) startActivity(intent)
                else Toast.makeText(this, "Chrome installed nahi hai.", Toast.LENGTH_LONG).show()
            }

            command.contains("open settings") || command.contains("settings kholo") ||
            command.contains("settings khol") -> {
                startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
            }

            command.startsWith("message ") || command.startsWith("send message ") ||
            command.startsWith("message bhejo ") || command.startsWith("message bhej ") -> {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    setPackage("com.whatsapp")
                    putExtra(Intent.EXTRA_TEXT, spoken)
                }
                try {
                    startActivity(intent)
                } catch (_: Exception) {
                    Toast.makeText(this, "WhatsApp available nahi hai.", Toast.LENGTH_LONG).show()
                }
            }

            else -> {
                input.setText(spoken)
                sendMessage()
            }
        }
    }

    private fun startVoiceInput() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), micPermissionRequest)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Speech recognition isn't available.", Toast.LENGTH_LONG).show()
            return
        }
        try {
            openMicActive = true
            micButton.text = "■ STOP MIC"
            micButton.setTextColor(Color.WHITE)
            micButton.setBackgroundColor(Color.rgb(125, 35, 55))

            if (speechRecognizer == null) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
                speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        if (openMicActive) micButton.text = "🎙 LISTENING..."
                    }
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onPartialResults(results: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                    override fun onError(error: Int) {
                        if (openMicActive) micHandler.postDelayed(restartListening, 700)
                    }
                    override fun onResults(results: Bundle?) {
                        if (!openMicActive) return
                        val spoken = results?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )?.firstOrNull()?.trim()
                        if (!spoken.isNullOrBlank()) handleVoiceCommand(spoken)
                        if (openMicActive) micHandler.postDelayed(restartListening, 1200)
                    }
                })
            }
            listenAgain()
        } catch (_: Exception) {
            stopOpenMic()
            Toast.makeText(this, "Could not start microphone.", Toast.LENGTH_LONG).show()
        }
    }

    private fun listenAgain() {
        if (!openMicActive) return
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            speechRecognizer?.startListening(intent)
        } catch (_: Exception) {
            if (openMicActive) micHandler.postDelayed(restartListening, 1000)
        }
    }

    private fun stopOpenMic() {
        openMicActive = false
        micHandler.removeCallbacks(restartListening)
        try {
            speechRecognizer?.cancel()
        } catch (_: Exception) {}
        micButton.text = "🎙 START MIC"
        micButton.setTextColor(cyan)
        micButton.setBackgroundColor(Color.rgb(17, 35, 55))
    }

    override fun onPause() {
        stopOpenMic()
        super.onPause()
    }

    override fun onDestroy() {
        micHandler.removeCallbacksAndMessages(null)
        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
        super.onDestroy()
    }

    @Deprecated("Using the speech recognizer activity for compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 102 && resultCode == RESULT_OK) {
            val photo = data?.extras?.get("data") as? android.graphics.Bitmap
            if (photo != null) {
                addMessage("You", "Photo captured.")
                addMessage("Sara", "Analyzing photo...")
                gemini.analyzeImage("Describe this image and answer helpfully.", photo) { answer ->
                    runOnUiThread {
                        for (i in messages.childCount - 1 downTo 0) {
                            val view = messages.getChildAt(i)
                            if (view is android.widget.TextView &&
                                view.text.toString().contains("Analyzing photo...")) {
                                messages.removeViewAt(i)
                                break
                            }
                        }
                        addMessage("Sara", answer)
                        if (answer.isNotBlank()) voice.speak(answer)
                    }
                }
            } else {
                addMessage("Sara", "Camera closed. No photo preview returned.")
            }
            return
        }

        if (requestCode == voiceRequest && resultCode == RESULT_OK) {
            val spoken = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                handleVoiceCommand(spoken)
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == micPermissionRequest &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startVoiceInput()
        }
    }
}
