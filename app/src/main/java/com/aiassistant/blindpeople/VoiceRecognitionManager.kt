package com.aiassistant.blindpeople

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

class VoiceRecognitionManager(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit
) {

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    init {
        initializeSpeechRecognizer()
    }

    private fun initializeSpeechRecognizer() {

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Speech recognition is not available on this device.")
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)

        speechRecognizer?.setRecognitionListener(
            object : RecognitionListener {

                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                    Log.d("VoiceRecognition", "Ready for speech")
                }

                override fun onBeginningOfSpeech() {
                    Log.d("VoiceRecognition", "Speech started")
                }

                override fun onRmsChanged(rmsdB: Float) {
                    // Audio volume changes
                }

                override fun onBufferReceived(buffer: ByteArray?) {
                    // Audio buffer received
                }

                override fun onEndOfSpeech() {
                    Log.d("VoiceRecognition", "Speech ended")
                }

                override fun onError(error: Int) {
                    isListening = false

                    val errorMessage = when (error) {
                        SpeechRecognizer.ERROR_AUDIO ->
                            "Audio recording error"

                        SpeechRecognizer.ERROR_CLIENT ->
                            "Speech recognition client error"

                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                            "Microphone permission not granted"

                        SpeechRecognizer.ERROR_NETWORK ->
                            "Network error"

                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                            "Network timeout"

                        SpeechRecognizer.ERROR_NO_MATCH ->
                            "Speech not recognized"

                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                            "Speech recognizer is busy"

                        SpeechRecognizer.ERROR_SERVER ->
                            "Speech recognition server error"

                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                            "No speech detected"

                        else ->
                            "Unknown speech recognition error"
                    }

                    Log.e("VoiceRecognition", errorMessage)
                    onError(errorMessage)
                }

                override fun onResults(results: Bundle?) {
                    isListening = false

                    val matches = results?.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION
                    )

                    val recognizedText =
                        matches?.firstOrNull()?.trim()

                    if (!recognizedText.isNullOrEmpty()) {
                        Log.d(
                            "VoiceRecognition",
                            "Recognized: $recognizedText"
                        )

                        onResult(recognizedText)
                    } else {
                        onError("No speech recognized")
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    // Partial results can be handled later
                }
                override fun onEvent(
                    eventType: Int,
                    params: Bundle?
                ) {
                    // Additional recognition events
                }
            }
        )
    }

    fun startListening() {

        if (isListening) {
            return
        }

        val recognizer = speechRecognizer

        if (recognizer == null) {
            onError("Speech recognizer is not initialized")
            return
        }

        val intent = Intent(
            RecognizerIntent.ACTION_RECOGNIZE_SPEECH
        ).apply {

            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )

            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                Locale.ENGLISH.toLanguageTag()
            )

            putExtra(
                RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                true
            )

            putExtra(
                RecognizerIntent.EXTRA_MAX_RESULTS,
                1
            )
        }

        isListening = true

        try {
            recognizer.startListening(intent)
        } catch (e: Exception) {
            isListening = false

            Log.e(
                "VoiceRecognition",
                "Failed to start listening",
                e
            )

            onError("Failed to start voice recognition")
        }
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
    }

    fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        isListening = false
    }
}