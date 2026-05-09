package com.example.swifttalkai
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.speech.*
import android.speech.tts.TextToSpeech
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import java.io.IOException
import java.util.*
import org.json.JSONObject
import okhttp3.RequestBody.Companion.toRequestBody

import org.json.JSONArray
class VoiceViewModel(application: Application) : AndroidViewModel(application) {

    // ------------------ STATE ------------------
    private val conversationHistory = JSONArray()
    private val _spokenText = MutableStateFlow("Tap mic to speak")
    val spokenText: StateFlow<String> = _spokenText
    private val _openCamera = MutableStateFlow(false)
    val openCamera: StateFlow<Boolean> = _openCamera

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking

    private val speechRecognizer =
        SpeechRecognizer.createSpeechRecognizer(application)

    private var textToSpeech: TextToSpeech? = null

    private var lastTopic: String? = null
    private var lastPersonDetected: String? = null
    private var lastSearchTime: String? = null


    // ------------------ INIT ------------------

    init {
        textToSpeech = TextToSpeech(getApplication()) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale.US
            }
        }
    }

    // ------------------ GEMINI CALL ------------------

    private fun callGemini(userText: String) {
        val visualContext = if (lastPersonDetected != null) {
            "Note: You are currently looking at $lastPersonDetected via the camera."
        } else {
            "Note: No recognized faces are currently visible."
        }

        val finalPrompt = """
        Context: $visualContext
        User says: $userText
        Reply in 2 short sentences.
    """.trimIndent()

        Thread.sleep(800)
        val prompt = if (lastTopic != null &&
            (userText.contains("his", true) ||
                    userText.contains("her", true) ||
                    userText.contains("their", true))
        ) {

            "Context: We are talking about $lastTopic.\n\n$userText"

        } else {
            userText
        }
        val jsonObject = JSONObject()

        val userObject = JSONObject()
        userObject.put("role", "user")

        val partsArray = JSONArray()
        val textObject = JSONObject()

        textObject.put(
            "text",
            "Reply in maximum 2 short sentences. Be clear and concise.\n\n$prompt"
        )

        partsArray.put(textObject)
        userObject.put("parts", partsArray)

        conversationHistory.put(userObject)

        val contents = JSONArray()
        contents.put(userObject)
        jsonObject.put("contents", contents)


        val client = OkHttpClient.Builder()
            .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .build()

    

        val requestBody =
            jsonObject.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
                CoroutineScope(Dispatchers.Main).launch {
                    _spokenText.value = "Network Error: ${e.message}"
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.code == 429) {
                    CoroutineScope(Dispatchers.Main).launch {
                        _spokenText.value = "Too many requests. Please wait 1 minute."
                    }
                   return
                }
                CoroutineScope(Dispatchers.Main).launch {

                    if (!response.isSuccessful) {
                        _spokenText.value = "Server Error: ${response.code}"
                        return@launch
                    }

                    val responseBody = response.body?.string()

                    if (responseBody.isNullOrEmpty()) {
                        _spokenText.value = "Empty response"
                        return@launch
                    }

                    try {
                        val json = JSONObject(responseBody)

                        val candidates = json.optJSONArray("candidates")

                        if (candidates != null && candidates.length() > 0) {

                            val content = candidates
                                .getJSONObject(0)
                                .optJSONObject("content")

                            val parts = content?.optJSONArray("parts")

                            val finalText = parts
                                ?.optJSONObject(0)
                                ?.optString("text", "")

                            if (!finalText.isNullOrBlank()) {

                                _spokenText.value = finalText
                                lastTopic = userText
                                val modelObject = JSONObject()
                                modelObject.put("role", "model")

                                val modelParts = JSONArray()
                                val modelText = JSONObject()
                                modelText.put("text", finalText)

                                modelParts.put(modelText)
                                modelObject.put("parts", modelParts)

                                conversationHistory.put(modelObject)

                                if (conversationHistory.length() > 10) {
                                    conversationHistory.remove(0)
                                }

                                textToSpeech?.speak(
                                    finalText,
                                    TextToSpeech.QUEUE_FLUSH,
                                    null,
                                    "AI_REPLY"
                                )

                            } else {
                                _spokenText.value = "I couldn't understand. Try again."
                            }

                        } else {
                            _spokenText.value = "No AI response"
                        }

                    } catch (e: Exception) {
                        _spokenText.value = "Parsing error"
                    }
                }

            }
        })
    }

    fun startListening() {

        textToSpeech?.stop()

        speechRecognizer.cancel()

        _isListening.value = true

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )

        speechRecognizer.setRecognitionListener(object : RecognitionListener {

            override fun onResults(results: Bundle?) {

                val matches =
                    results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)


                val userText = matches?.getOrNull(0) ?: return

                sendText(userText)   // only one call
                _isListening.value = false

            }

            override fun onError(error: Int) {
                _spokenText.value = "Listening error"
                _isListening.value = false
            }

            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        speechRecognizer.startListening(intent)

    }

       fun openCameraManually() {
           _openCamera.value = true
       }
     fun sendText(text: String) {
         if (text.contains("person at")) {

             val hour = 2 // example parse

             val event = EventManager.searchEvent(hour)

             if (event != null) {
                 _spokenText.value = "Person found at that time"
             } else {
                 _spokenText.value = "No person detected at that time"
             }

             return
         }



         val lower = text.lowercase()

         // ---- TIME / DATE ----
         if (lower.contains("time") || lower.contains("date")) {

             val current = java.text.SimpleDateFormat(
                 "EEEE, dd MMMM yyyy  HH:mm",
                 java.util.Locale.getDefault()
             ).format(java.util.Date())

             _spokenText.value = "Current date and time is $current"

             textToSpeech?.speak(
                 _spokenText.value,
                 TextToSpeech.QUEUE_FLUSH,
                 null,
                 null
             )

             return
         }

         // ---- CAMERA COMMAND ----
         if (lower.contains("camera") ||
             lower.contains("show person") ||
             lower.contains("open camera")) {

             _spokenText.value = "Opening camera"
             _openCamera.value = true
             return
         }

         // ---- NORMAL AI ----
         _spokenText.value = "Thinking..."

         val intent = classifyIntent(text)

         when (intent) {

             "DELIVERY_SEARCH" -> handleCCTVQuery(text)

             else -> callGemini(text)
         }
     }


        private fun classifyIntent(text: String): String {

            val lower = text.lowercase()

            return when {


                lower.contains("show person") ||
                        lower.contains("show me the person") ||
                        lower.contains("open camera") ->
                    "OPEN_CAMERA"

                lower.contains("delivery") ||
                        lower.contains("package") ->
                    "DELIVERY_SEARCH"

                else -> "GENERAL"
            }

        }

        private fun handleCCTVQuery(text: String) {

            val lower = text.lowercase()

            when {

                lower.contains("2") && lower.contains("am") -> {

                    lastSearchTime = "2 AM"
                    lastPersonDetected = "Blue Shirt Male"

                    _spokenText.value = "Person detected at 2 AM. Showing footage."
                }

                lower.contains("how many") && lastPersonDetected != null -> {

                    _spokenText.value =
                        "$lastPersonDetected visited 3 times this week."
                }

                else -> {
                    _spokenText.value = "Searching CCTV records..."
                }
            }
        }
    }








