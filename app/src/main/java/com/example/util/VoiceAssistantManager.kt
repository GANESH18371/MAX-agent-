package com.example.util

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class VoiceAssistantManager(private val context: Context) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "VoiceAssistantManager"
        const val SAMPLE_PHRASE = "नमस्ते, मैं Max हूं"

        fun getDefaultVoiceOptions(): List<MaxVoiceOption> {
            return listOf(
                MaxVoiceOption(
                    id = "voice_male_hindi",
                    title = "Voice 1 - Male (Hindi)",
                    subtitle = "गंभीर एवं स्पष्ट आवाज़ (Natural Warm Baritone)",
                    gender = VoiceGender.MALE,
                    systemVoiceName = null,
                    pitch = 0.95f,
                    speechRate = 0.96f,
                    languageCode = "hi-IN",
                    qualityTag = "Natural Warm"
                ),
                MaxVoiceOption(
                    id = "voice_female_hindi",
                    title = "Voice 2 - Female (Hindi)",
                    subtitle = "मधुर एवं स्वाभाविक आवाज़ (Smooth Natural Melodic)",
                    gender = VoiceGender.FEMALE,
                    systemVoiceName = null,
                    pitch = 1.10f,
                    speechRate = 0.97f,
                    languageCode = "hi-IN",
                    qualityTag = "Natural Smooth"
                ),
                MaxVoiceOption(
                    id = "voice_assistant_hinglish",
                    title = "Voice 3 - Smart Assistant (Hinglish)",
                    subtitle = "तेज़ एवं दोस्ताना आवाज़ (Conversational Assistant)",
                    gender = VoiceGender.NEUTRAL,
                    systemVoiceName = null,
                    pitch = 1.02f,
                    speechRate = 1.00f,
                    languageCode = "hi-IN / en-IN",
                    qualityTag = "Expressive"
                )
            )
        }
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _speechAmplitude = MutableStateFlow(0f)
    val speechAmplitude: StateFlow<Float> = _speechAmplitude.asStateFlow()

    private val _transcript = MutableStateFlow("")
    val transcript: StateFlow<String> = _transcript.asStateFlow()

    private val _voiceStatus = MutableStateFlow("Tayyar hoon (Ready)")
    val voiceStatus: StateFlow<String> = _voiceStatus.asStateFlow()

    // Voice Selection States
    private val _availableVoices = MutableStateFlow<List<MaxVoiceOption>>(getDefaultVoiceOptions())
    val availableVoices: StateFlow<List<MaxVoiceOption>> = _availableVoices.asStateFlow()

    private val _selectedVoice = MutableStateFlow<MaxVoiceOption>(_availableVoices.value.first())
    val selectedVoice: StateFlow<MaxVoiceOption> = _selectedVoice.asStateFlow()

    private val _previewingVoiceId = MutableStateFlow<String?>(null)
    val previewingVoiceId: StateFlow<String?> = _previewingVoiceId.asStateFlow()

    var onSpeechResultListener: ((String) -> Unit)? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    private fun runOnMainThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }

    init {
        initTts()
        initSpeechRecognizer()
        BatteryOptimizationManager.registerInactivityCallback {
            if (_isListening.value) {
                stopListening()
                _voiceStatus.value = "Smart Standby (Microphone sleeping - Eco Mode)"
                Log.d(TAG, "Smart listening put to sleep to conserve battery.")
            }
        }
    }

    private fun initTts() {
        tts = TextToSpeech(context, this)
    }

    private var activeOnCompleteCallback: (() -> Unit)? = null

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val hindiLocale = Locale("hi", "IN")
            val result = tts?.setLanguage(hindiLocale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "Hindi language missing in TTS, using default locale")
                tts?.setLanguage(Locale.getDefault())
            }

            // Discover and catalog available high-quality voices on device
            discoverAndCatalogVoices()

            // Restore saved voice preference
            val savedVoiceId = VoicePreferences.getSelectedVoiceId(context)
            val matchingOption = _availableVoices.value.find { it.id == savedVoiceId }
                ?: _availableVoices.value.firstOrNull()
                ?: getDefaultVoiceOptions().first()

            _selectedVoice.value = matchingOption
            isTtsInitialized = true
            applyVoiceSettings(matchingOption)

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                    if (utteranceId?.startsWith("sample_preview_") == true) {
                        val vid = utteranceId.removePrefix("sample_preview_")
                        _previewingVoiceId.value = vid
                        _voiceStatus.value = "Sample sun rahe hain..."
                    } else {
                        _voiceStatus.value = "Max bol raha hai..."
                    }
                }

                override fun onDone(utteranceId: String?) {
                    _isSpeaking.value = false
                    if (utteranceId?.startsWith("sample_preview_") == true) {
                        _previewingVoiceId.value = null
                        applyVoiceSettings(_selectedVoice.value)
                    }
                    _voiceStatus.value = "Tayyar hoon"
                    val cb = activeOnCompleteCallback
                    activeOnCompleteCallback = null
                    cb?.invoke()
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _isSpeaking.value = false
                    if (utteranceId?.startsWith("sample_preview_") == true) {
                        _previewingVoiceId.value = null
                        applyVoiceSettings(_selectedVoice.value)
                    }
                    _voiceStatus.value = "TTS error occurred"
                    val cb = activeOnCompleteCallback
                    activeOnCompleteCallback = null
                    cb?.invoke()
                }
            })
        } else {
            Log.e(TAG, "TTS Initialization failed!")
        }
    }

    private fun detectVoiceGender(voice: android.speech.tts.Voice): VoiceGender {
        val name = voice.name.lowercase()
        val features = try {
            voice.features?.map { it.lowercase() } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

        // Female detection
        if (features.any { it.contains("female") || it.contains("gender=female") } ||
            name.contains("female") || name.contains("#female") || name.contains("-fem") ||
            name.contains("hid") || name.contains("hif") || name.contains("cfc") || name.contains("cfa") ||
            name.contains("end") || name.contains("woman")) {
            return VoiceGender.FEMALE
        }

        // Male detection
        if (features.any { it.contains("male") || it.contains("gender=male") } ||
            name.contains("male") || name.contains("#male") || name.contains("-male") ||
            name.contains("hie") || name.contains("hic") || name.contains("ene") || name.contains("enc") ||
            name.contains("man")) {
            return VoiceGender.MALE
        }

        return VoiceGender.NEUTRAL
    }

    private fun discoverAndCatalogVoices() {
        val systemVoices = try {
            tts?.voices?.toList() ?: emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "Unable to query tts.voices", e)
            emptyList()
        }

        if (systemVoices.isEmpty()) {
            _availableVoices.value = getDefaultVoiceOptions()
            return
        }

        // Filter Hindi voices
        val hindiVoices = systemVoices.filter { v ->
            val lang = v.locale.language.lowercase()
            val name = v.name.lowercase()
            lang == "hi" || name.contains("hi-in") || name.contains("hi_in")
        }.sortedWith(
            compareByDescending<android.speech.tts.Voice> { !it.isNetworkConnectionRequired }
                .thenByDescending { it.quality }
        )

        // Filter English India / Hinglish voices
        val indianEnVoices = systemVoices.filter { v ->
            val lang = v.locale.language.lowercase()
            val country = v.locale.country.lowercase()
            val name = v.name.lowercase()
            (lang == "en" && (country == "in" || country == "")) || name.contains("en-in") || name.contains("en_in")
        }.sortedWith(
            compareByDescending<android.speech.tts.Voice> { !it.isNetworkConnectionRequired }
                .thenByDescending { it.quality }
        )

        val bestMaleVoice = hindiVoices.firstOrNull { detectVoiceGender(it) == VoiceGender.MALE }
            ?: indianEnVoices.firstOrNull { detectVoiceGender(it) == VoiceGender.MALE }

        val bestFemaleVoice = hindiVoices.firstOrNull { detectVoiceGender(it) == VoiceGender.FEMALE }
            ?: indianEnVoices.firstOrNull { detectVoiceGender(it) == VoiceGender.FEMALE }

        val bestAssistantVoice = hindiVoices.firstOrNull { it != bestMaleVoice && it != bestFemaleVoice }
            ?: indianEnVoices.firstOrNull { it != bestMaleVoice && it != bestFemaleVoice }
            ?: hindiVoices.firstOrNull()

        val curated = mutableListOf<MaxVoiceOption>()

        // 1. Male Option
        curated.add(
            MaxVoiceOption(
                id = bestMaleVoice?.name ?: "voice_male_hindi",
                title = "Voice 1 - Male (Hindi)",
                subtitle = if (bestMaleVoice != null)
                    "गंभीर एवं स्पष्ट पुरुष स्वर (${cleanVoiceLabel(bestMaleVoice.name)})"
                else
                    "गंभीर एवं स्पष्ट पुरुष स्वर (Natural Warm Baritone)",
                gender = VoiceGender.MALE,
                systemVoiceName = bestMaleVoice?.name,
                pitch = 0.95f,      // Tuned natural warm baritone
                speechRate = 0.96f, // Clear, composed pacing
                languageCode = "hi-IN",
                isNetworkRequired = bestMaleVoice?.isNetworkConnectionRequired ?: false,
                qualityTag = if (bestMaleVoice != null && !bestMaleVoice.isNetworkConnectionRequired) "High Quality (Local)" else "Natural Warm"
            )
        )

        // 2. Female Option
        curated.add(
            MaxVoiceOption(
                id = bestFemaleVoice?.name ?: "voice_female_hindi",
                title = "Voice 2 - Female (Hindi)",
                subtitle = if (bestFemaleVoice != null)
                    "मधुर एवं स्वाभाविक महिला स्वर (${cleanVoiceLabel(bestFemaleVoice.name)})"
                else
                    "मधुर एवं स्वाभाविक महिला स्वर (Smooth Natural Melodic)",
                gender = VoiceGender.FEMALE,
                systemVoiceName = bestFemaleVoice?.name,
                pitch = 1.10f,      // Tuned sweet, pleasant melodious pitch
                speechRate = 0.97f, // Conversational pacing
                languageCode = "hi-IN",
                isNetworkRequired = bestFemaleVoice?.isNetworkConnectionRequired ?: false,
                qualityTag = if (bestFemaleVoice != null && !bestFemaleVoice.isNetworkConnectionRequired) "High Quality (Local)" else "Natural Smooth"
            )
        )

        // 3. Smart Assistant (Hinglish/Neutral) Option
        curated.add(
            MaxVoiceOption(
                id = bestAssistantVoice?.name ?: "voice_assistant_hinglish",
                title = "Voice 3 - Smart Assistant (Hinglish)",
                subtitle = if (bestAssistantVoice != null)
                    "तेज़ एवं दोस्ताना आधुनिक स्वर (${cleanVoiceLabel(bestAssistantVoice.name)})"
                else
                    "तेज़ एवं दोस्ताना आधुनिक स्वर (Conversational Assistant)",
                gender = bestAssistantVoice?.let { detectVoiceGender(it) } ?: VoiceGender.NEUTRAL,
                systemVoiceName = bestAssistantVoice?.name,
                pitch = 1.02f,
                speechRate = 1.00f,
                languageCode = "hi-IN / en-IN",
                isNetworkRequired = bestAssistantVoice?.isNetworkConnectionRequired ?: false,
                qualityTag = "Expressive"
            )
        )

        // Check if there are other distinct high quality Hindi voices available (up to 4 total)
        val remainingDistinctHindi = hindiVoices.filter { hv ->
            curated.none { it.systemVoiceName == hv.name }
        }.take(1)

        for ((index, extraVoice) in remainingDistinctHindi.withIndex()) {
            val gender = detectVoiceGender(extraVoice)
            val genderLabel = if (gender == VoiceGender.FEMALE) "Female" else "Male"
            curated.add(
                MaxVoiceOption(
                    id = extraVoice.name,
                    title = "Voice ${4 + index} - $genderLabel (Hindi)",
                    subtitle = "वैकल्पिक स्वर (${cleanVoiceLabel(extraVoice.name)})",
                    gender = gender,
                    systemVoiceName = extraVoice.name,
                    pitch = if (gender == VoiceGender.FEMALE) 1.08f else 0.96f,
                    speechRate = 0.97f,
                    languageCode = "hi-IN",
                    isNetworkRequired = extraVoice.isNetworkConnectionRequired,
                    qualityTag = "Neural Voice"
                )
            )
        }

        _availableVoices.value = curated
    }

    private fun cleanVoiceLabel(name: String): String {
        return name.substringBefore("-local")
            .replace("#", " ")
            .replace("_", " ")
            .replace("-", " ")
            .trim()
    }

    fun applyVoiceSettings(option: MaxVoiceOption) {
        if (!isTtsInitialized) return
        try {
            val targetLocale = if (option.languageCode.contains("en")) Locale("en", "IN") else Locale("hi", "IN")
            tts?.setLanguage(targetLocale)

            if (!option.systemVoiceName.isNullOrBlank()) {
                val matchingSysVoice = tts?.voices?.firstOrNull { it.name == option.systemVoiceName }
                if (matchingSysVoice != null) {
                    tts?.voice = matchingSysVoice
                    Log.d(TAG, "Applied TTS voice: ${matchingSysVoice.name}")
                }
            }

            tts?.setPitch(option.pitch)
            tts?.setSpeechRate(option.speechRate)
            Log.d(TAG, "Applied voice settings: ${option.title} (pitch=${option.pitch}, rate=${option.speechRate})")
        } catch (e: Exception) {
            Log.e(TAG, "Error applying voice settings", e)
        }
    }

    fun selectVoice(option: MaxVoiceOption) {
        VoicePreferences.saveSelectedVoiceId(context, option.id)
        _selectedVoice.value = option
        applyVoiceSettings(option)
    }

    fun previewVoice(option: MaxVoiceOption, sampleText: String = SAMPLE_PHRASE) {
        runOnMainThread {
            if (!isTtsInitialized) return@runOnMainThread

            if (_previewingVoiceId.value == option.id && _isSpeaking.value) {
                stopSpeaking()
                _previewingVoiceId.value = null
                applyVoiceSettings(_selectedVoice.value)
                return@runOnMainThread
            }

            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping SpeechRecognizer before preview", e)
            }
            _isListening.value = false
            tts?.stop()

            applyVoiceSettings(option)
            _previewingVoiceId.value = option.id
            _isSpeaking.value = true
            _voiceStatus.value = "Sample bol raha hai..."

            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "sample_preview_${option.id}")
            }
            tts?.speak(sampleText, TextToSpeech.QUEUE_FLUSH, params, "sample_preview_${option.id}")
        }
    }

    private fun initSpeechRecognizer() {
        runOnMainThread {
            if (SpeechRecognizer.isRecognitionAvailable(context)) {
                try {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                        setRecognitionListener(object : RecognitionListener {
                            override fun onReadyForSpeech(params: Bundle?) {
                                _isListening.value = true
                                _voiceStatus.value = "Suno... Abhi bolo"
                            }

                            override fun onBeginningOfSpeech() {
                                BatteryOptimizationManager.reportUserActivity()
                                _voiceStatus.value = "Aapki aawaaz sun raha hoon..."
                            }

                            override fun onRmsChanged(rmsdB: Float) {
                                // Normalize amplitude (roughly 0.0 to 1.0)
                                val norm = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                                _speechAmplitude.value = norm
                            }

                            override fun onBufferReceived(buffer: ByteArray?) {}

                            override fun onEndOfSpeech() {
                                _isListening.value = false
                                _voiceStatus.value = "Aawaaz analyze ho rahi hai..."
                                _speechAmplitude.value = 0f
                            }

                            override fun onError(error: Int) {
                                _isListening.value = false
                                _speechAmplitude.value = 0f
                                val errMsg = when (error) {
                                    SpeechRecognizer.ERROR_NO_MATCH -> "Kuch samajh nahi aaya, dobara bolo."
                                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Samay khatam ho gaya."
                                    else -> "Mic error ($error)"
                                }
                                _voiceStatus.value = errMsg
                            }

                            override fun onResults(results: Bundle?) {
                                _isListening.value = false
                                _speechAmplitude.value = 0f
                                BatteryOptimizationManager.reportUserActivity()
                                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                val text = matches?.firstOrNull() ?: ""
                                if (text.isNotBlank()) {
                                    _transcript.value = text
                                    _voiceStatus.value = "Command: \"$text\""
                                    onSpeechResultListener?.invoke(text)
                                } else {
                                    _voiceStatus.value = "Aawaaz spashth nahi thi."
                                }
                            }

                            override fun onPartialResults(partialResults: Bundle?) {
                                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                matches?.firstOrNull()?.let {
                                    _transcript.value = it
                                }
                            }

                            override fun onEvent(eventType: Int, params: Bundle?) {}
                        })
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to create SpeechRecognizer", e)
                }
            }
        }
    }

    fun startListening() {
        runOnMainThread {
            BatteryOptimizationManager.reportUserActivity()
            if (_isSpeaking.value) {
                tts?.stop()
                _isSpeaking.value = false
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }

            try {
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Error starting SpeechRecognizer", e)
            }
        }
    }

    fun stopListening() {
        runOnMainThread {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping SpeechRecognizer", e)
            }
            _isListening.value = false
            _speechAmplitude.value = 0f
        }
    }

    fun speak(hindiText: String, onComplete: (() -> Unit)? = null) {
        runOnMainThread {
            BatteryOptimizationManager.reportUserActivity()
            if (!isTtsInitialized) {
                onComplete?.invoke()
                return@runOnMainThread
            }
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping SpeechRecognizer before speak", e)
            }
            _isListening.value = false

            // Always ensure user's selected voice and tuning are applied
            applyVoiceSettings(_selectedVoice.value)

            activeOnCompleteCallback = onComplete
            _voiceStatus.value = "Max bol raha hai..."
            tts?.speak(hindiText, TextToSpeech.QUEUE_FLUSH, null, "max_tts_utterance")
        }
    }

    fun stopSpeaking() {
        runOnMainThread {
            tts?.stop()
            _isSpeaking.value = false
            if (_previewingVoiceId.value != null) {
                _previewingVoiceId.value = null
                applyVoiceSettings(_selectedVoice.value)
            }
        }
    }

    fun destroy() {
        BatteryOptimizationManager.forceSleepListening()
        BatteryOptimizationManager.releaseAllWakeLocks()
        runOnMainThread {
            try {
                speechRecognizer?.destroy()
            } catch (e: Exception) {
                Log.w(TAG, "Error destroying SpeechRecognizer", e)
            }
            speechRecognizer = null
            tts?.shutdown()
        }
    }
}
