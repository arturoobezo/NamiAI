package com.example.namiasistenteaioffline.data

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

class VoiceManager(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var speechRecognizer: SpeechRecognizer? = null
    
    private val _isSpeakingFlow = MutableStateFlow(false)
    val isSpeakingFlow: StateFlow<Boolean> = _isSpeakingFlow

    private val _isListeningFlow = MutableStateFlow(false)
    val isListeningFlow: StateFlow<Boolean> = _isListeningFlow
    
    private var preferredVoiceName: String? = null
    private var isRetrying = false

    init {
        initializeTts(true)
    }

    private fun initializeTts(useGoogle: Boolean) {
        tts = if (useGoogle) {
            TextToSpeech(context, this, "com.google.android.tts")
        } else {
            TextToSpeech(context, this)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            Log.d("VoiceManager", "TTS inicializado con éxito.")
            isTtsReady = true
            
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) AudioAttributes.USAGE_ASSISTANT else AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            tts?.setAudioAttributes(audioAttributes)
            
            setupProgressListener()
            
            // Aplicar voz guardada o buscar la mejor en español
            preferredVoiceName?.let { applyVoice(it) } ?: setupDefaultLanguage()
            
        } else if (!isRetrying) {
            isRetrying = true
            Log.e("VoiceManager", "Fallo al inicializar motor específico. Reintentando con el motor por defecto del sistema.")
            initializeTts(false)
        }
    }

    private fun setupDefaultLanguage() {
        val locale = Locale("es", "ES")
        tts?.setLanguage(locale)
        
        // Intentar buscar una voz de alta calidad por defecto si no hay preferida
        try {
            val voices = tts?.voices
            val bestVoice = voices?.find { it.locale.language == "es" && !it.isNetworkConnectionRequired }
                ?: voices?.find { it.locale.language == "es" }
            if (bestVoice != null) tts?.voice = bestVoice
        } catch (e: Exception) { /* Ignorar */ }
    }

    private fun setupProgressListener() {
        tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { _isSpeakingFlow.value = true }
            override fun onDone(utteranceId: String?) { _isSpeakingFlow.value = false }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { _isSpeakingFlow.value = false }
            override fun onError(utteranceId: String?, errorCode: Int) {
                Log.e("VoiceManager", "Error en reproducción TTS: $errorCode")
                _isSpeakingFlow.value = false
            }
        })
    }

    fun speak(text: String) {
        if (!isTtsReady) return
        stopListening()
        
        val textToSpeak = cleanText(text)
        if (textToSpeak.isEmpty()) return

        _isSpeakingFlow.value = true
        
        // 1. Configurar idioma
        val locale = Locale("es", "ES")
        tts?.setLanguage(locale)
        
        // 2. Re-aplicar voz preferida explícitamente después del idioma
        preferredVoiceName?.let { name ->
            val voice = tts?.voices?.find { it.name == name }
            if (voice != null && voice.locale.language == locale.language) {
                tts?.voice = voice
            }
        }

        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "NamiUtterance")
        }
        
        tts?.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, params, "NamiUtterance")
    }

    fun speakWithLanguage(text: String, languageCode: String) {
        if (!isTtsReady) return
        stopListening()
        
        val textToSpeak = cleanText(text)
        if (textToSpeak.isEmpty()) return

        _isSpeakingFlow.value = true

        val locale = getLocaleFromCode(languageCode)
        tts?.setLanguage(locale)
        
        // Re-aplicar voz preferida si es del mismo idioma
        preferredVoiceName?.let { name ->
            val voice = tts?.voices?.find { it.name == name }
            if (voice != null && voice.locale.language == locale.language) {
                tts?.voice = voice
            }
        }

        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "TranslatorUtterance")
        }
        tts?.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, params, "TranslatorUtterance")
    }

    private fun cleanText(text: String): String {
        return text
            .replace(Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("[*#`_]"), "")
            .trim()
    }

    private fun getLocaleFromCode(code: String): Locale {
        return when (code) {
            "es" -> Locale("es", "ES")
            "en" -> Locale("en", "US")
            "fr" -> Locale("fr", "FR")
            "de" -> Locale("de", "DE")
            "it" -> Locale("it", "IT")
            "pt" -> Locale("pt", "BR")
            "zh" -> Locale("zh", "CN")
            "ja" -> Locale("ja", "JP")
            "ko" -> Locale("ko", "KR")
            "ar" -> Locale("ar", "SA")
            "ru" -> Locale("ru", "RU")
            "hi" -> Locale("hi", "IN")
            else -> Locale(code)
        }
    }

    fun applyVoice(voiceName: String) {
        preferredVoiceName = voiceName
        if (!isTtsReady || voiceName.isEmpty()) return
        try {
            val voice = tts?.voices?.find { it.name == voiceName }
            if (voice != null) {
                tts?.setLanguage(voice.locale)
                tts?.voice = voice
                Log.d("VoiceManager", "Voz aplicada: $voiceName")
            }
        } catch (e: Exception) {
            Log.e("VoiceManager", "Error al aplicar voz: ${e.message}")
        }
    }

    fun getAvailableVoices(): List<Voice> {
        return try {
            tts?.voices?.filter { !it.isNetworkConnectionRequired }?.sortedBy { it.locale.displayName } ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    fun startListening(onResult: (String) -> Unit, onListeningStateChange: (Boolean) -> Unit) {
        // Siempre destruir el recognizer anterior completamente
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (e: Exception) { }
        
        // Crear uno nuevo limpio
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onListeningStateChange(false)
            return
        }
        
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale("es", "ES"))
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                onListeningStateChange(true)
                _isListeningFlow.value = true
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                onListeningStateChange(false)
                _isListeningFlow.value = false
            }
            override fun onError(error: Int) {
                Log.e("VoiceManager", "Error de reconocimiento: $error")
                onListeningStateChange(false)
                _isListeningFlow.value = false
                // Destruir para forzar recreación en el próximo intento
                try {
                    speechRecognizer?.destroy()
                    speechRecognizer = null
                } catch (e: Exception) { }
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    onResult(matches[0])
                }
                onListeningStateChange(false)
                _isListeningFlow.value = false
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        speechRecognizer?.startListening(intent)
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        _isListeningFlow.value = false
    }

    fun cancelListening() {
        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
        _isListeningFlow.value = false
    }

    fun isSpeaking(): Boolean {
        return _isSpeakingFlow.value || (tts?.isSpeaking ?: false)
    }

    fun stopAll() {
        tts?.stop()
        cancelListening()
        _isSpeakingFlow.value = false
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        cancelListening()
    }
}
