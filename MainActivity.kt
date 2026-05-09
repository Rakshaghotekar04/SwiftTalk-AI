package com.example.swifttalkai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {


    private lateinit var speechLauncher:
            ActivityResultLauncher<Intent>
    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestPermission()
        tts = TextToSpeech(this) { }


        speechLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->

                if (result.resultCode == RESULT_OK) {

                    val spokenText =
                        result.data?.getStringArrayListExtra(
                            RecognizerIntent.EXTRA_RESULTS
                        )?.get(0)

                    handleVoiceCommand(spokenText ?: "")
                }
            }

        setContent {
            VoiceScreen()
        }
    }


    fun startVoiceRecognition() {

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)

        intent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )

        intent.putExtra(
            RecognizerIntent.EXTRA_PROMPT,
            "Ask your question"
        )

        speechLauncher.launch(intent)
    }

     fun handleVoiceCommand(command: String) {

        if (command.contains("open camera", ignoreCase = true)) {
            val intent = Intent(this, CameraActivity::class.java)
            startActivity(intent)
            return
        }

        if (command.contains("person at", ignoreCase = true)) {
            val hour = 2
            val event = EventManager.searchEvent(hour)

            if (event != null) {
                tts?.speak("Person detected at that time", TextToSpeech.QUEUE_FLUSH, null, null)
            } else {
                tts?.speak("No person detected at that time", TextToSpeech.QUEUE_FLUSH, null, null)
            }
        }
     }

    private fun requestPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                1
            )
        }
    }
}