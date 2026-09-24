package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.GeminiClient
import com.example.data.api.MaxAiDecision
import com.example.data.db.ActionLogEntity
import com.example.data.db.AppDatabase
import com.example.data.db.UserMemoryEntity
import com.example.data.model.ScreenStateDump
import com.example.data.repository.MaxRepository
import com.example.service.MaxAccessibilityService
import com.example.service.MaxNotificationListenerService
import com.example.service.MaxOverlayService
import com.example.util.ConfirmationManager
import com.example.util.LocalCommandRouter
import com.example.util.MaxVoiceOption
import com.example.util.TermuxCommandManager
import com.example.util.VoiceAssistantManager
import com.example.util.VoiceGender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import com.example.util.SecureApiKeyStorage

sealed interface KeyValidationState {
    object Idle : KeyValidationState
    object Testing : KeyValidationState
    data class Success(val message: String) : KeyValidationState
    data class Error(val message: String) : KeyValidationState
}

class MaxViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MaxViewModel"
        private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        var activeInstance: MaxViewModel? = null
            private set
    }

    private val db = AppDatabase.getDatabase(application)
    private val repository = MaxRepository(db.actionLogDao(), db.userMemoryDao(), db.reminderDao())

    val voiceAssistant = VoiceAssistantManager(application)

    val actionLogs: StateFlow<List<ActionLogEntity>> = repository.allLogs
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val userMemories: StateFlow<List<UserMemoryEntity>> = repository.allMemories
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val activeReminders = repository.activeReminders
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _isServiceEnabled = MutableStateFlow(false)
    val isServiceEnabled: StateFlow<Boolean> = _isServiceEnabled.asStateFlow()

    private val _canDrawOverlays = MutableStateFlow(false)
    val canDrawOverlays: StateFlow<Boolean> = _canDrawOverlays.asStateFlow()

    private val _isOverlayActive = MutableStateFlow(false)
    val isOverlayActive: StateFlow<Boolean> = _isOverlayActive.asStateFlow()

    private val _isDefaultAssistant = MutableStateFlow(false)
    val isDefaultAssistant: StateFlow<Boolean> = _isDefaultAssistant.asStateFlow()

    private val _isAutoReplyActive = MutableStateFlow(false)
    val isAutoReplyActive: StateFlow<Boolean> = _isAutoReplyActive.asStateFlow()

    private val _isNotificationListenerGranted = MutableStateFlow(false)
    val isNotificationListenerGranted: StateFlow<Boolean> = _isNotificationListenerGranted.asStateFlow()

    private val _isPhonePermissionGranted = MutableStateFlow(false)
    val isPhonePermissionGranted: StateFlow<Boolean> = _isPhonePermissionGranted.asStateFlow()

    private val _currentApp = MutableStateFlow("com.android.settings")
    val currentApp: StateFlow<String> = _currentApp.asStateFlow()

    private val _previousApp = MutableStateFlow("com.android.settings")

    private val _screenDump = MutableStateFlow<ScreenStateDump?>(null)
    val screenDump: StateFlow<ScreenStateDump?> = _screenDump.asStateFlow()

    private val _lastDecision = MutableStateFlow<MaxAiDecision?>(null)
    val lastDecision: StateFlow<MaxAiDecision?> = _lastDecision.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    // Termux Integration State
    val termuxLastExecution = TermuxCommandManager.lastExecution
    private val _isTermuxInstalled = MutableStateFlow(false)
    val isTermuxInstalled: StateFlow<Boolean> = _isTermuxInstalled.asStateFlow()
    private val _isTermuxApiInstalled = MutableStateFlow(false)
    val isTermuxApiInstalled: StateFlow<Boolean> = _isTermuxApiInstalled.asStateFlow()

    // Gemini API Key Management
    private val _isApiKeyConfigured = MutableStateFlow(false)
    val isApiKeyConfigured: StateFlow<Boolean> = _isApiKeyConfigured.asStateFlow()

    private val _hasCustomKey = MutableStateFlow(false)
    val hasCustomKey: StateFlow<Boolean> = _hasCustomKey.asStateFlow()

    private val _maskedApiKey = MutableStateFlow("")
    val maskedApiKey: StateFlow<String> = _maskedApiKey.asStateFlow()

    private val _keyValidationState = MutableStateFlow<KeyValidationState>(KeyValidationState.Idle)
    val keyValidationState: StateFlow<KeyValidationState> = _keyValidationState.asStateFlow()

    // Voice Selection & Tuning States
    val availableVoices: StateFlow<List<MaxVoiceOption>> = voiceAssistant.availableVoices
    val selectedVoice: StateFlow<MaxVoiceOption> = voiceAssistant.selectedVoice
    val previewingVoiceId: StateFlow<String?> = voiceAssistant.previewingVoiceId

    fun selectVoice(option: MaxVoiceOption) {
        voiceAssistant.selectVoice(option)
        postToastAndLog("${option.title} chuni gayi.")
        viewModelScope.launch(Dispatchers.IO) {
            repository.logAction(
                userCommand = "Voice Selection",
                targetApp = "com.example",
                aiReasoning = "User selected ${option.title}",
                actionType = "VOICE_SETTING",
                actionDetail = "Voice: ${option.title} (${option.gender.name}, ${option.languageCode})",
                isSuccess = true
            )
        }
    }

    fun previewVoice(option: MaxVoiceOption) {
        voiceAssistant.previewVoice(option)
    }

    fun stopVoicePreview() {
        voiceAssistant.stopSpeaking()
    }

    init {
        activeInstance = this
        SecureApiKeyStorage.init(getApplication())
        com.example.util.SecurityManager.init(getApplication())
        refreshApiKeyStatus()
        voiceAssistant.onSpeechResultListener = { text ->
            processVoiceCommand(text)
        }
        checkServiceStatus()
    }

    private fun postToastAndLog(message: String, isLong: Boolean = false) {
        Log.d(TAG, message)
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(getApplication(), message, if (isLong) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
        }
    }

    fun checkServiceStatus() {
        val context = getApplication<Application>()
        val isRunning = MaxAccessibilityService.isServiceRunning
        _isServiceEnabled.value = isRunning
        _canDrawOverlays.value = Settings.canDrawOverlays(context)
        _isOverlayActive.value = MaxOverlayService.isOverlayRunning
        _isDefaultAssistant.value = com.example.util.DefaultAssistantHelper.isDefaultAssistant(context)
        _isAutoReplyActive.value = MaxNotificationListenerService.isAutoReplyEnabled
        _isNotificationListenerGranted.value = MaxNotificationListenerService.isNotificationListenerEnabled(context)
        _isPhonePermissionGranted.value = context.checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
                context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        _isTermuxInstalled.value = TermuxCommandManager.isTermuxInstalled(context)
        _isTermuxApiInstalled.value = TermuxCommandManager.isTermuxApiInstalled(context)
        refreshApiKeyStatus()

        MaxAccessibilityService.instance?.let { service ->
            val newApp = MaxAccessibilityService.currentAppPackage.value
            if (newApp != _currentApp.value && newApp != "Unknown") {
                _previousApp.value = _currentApp.value
                _currentApp.value = newApp
            }
            _screenDump.value = service.getScreenStateDump()
        }
    }

    fun openDefaultAssistantSettings() {
        com.example.util.DefaultAssistantHelper.openAssistantSettings(getApplication())
    }

    fun refreshApiKeyStatus() {
        val context = getApplication<Application>()
        val configured = SecureApiKeyStorage.isConfigured(context)
        val hasCustom = SecureApiKeyStorage.hasCustomKey(context)
        val rawKey = SecureApiKeyStorage.getApiKey(context)

        _isApiKeyConfigured.value = configured
        _hasCustomKey.value = hasCustom
        _maskedApiKey.value = when {
            rawKey.length > 8 -> "${rawKey.take(4)}••••••••${rawKey.takeLast(4)}"
            rawKey.isNotBlank() -> "••••••••"
            else -> ""
        }
    }

    fun saveAndValidateApiKey(newKey: String, onComplete: ((Boolean) -> Unit)? = null) {
        val context = getApplication<Application>()
        val cleanKey = newKey.trim()
        if (cleanKey.isBlank()) {
            _keyValidationState.value = KeyValidationState.Error("API key khali (empty) nahi ho sakti!")
            return
        }

        viewModelScope.launch {
            _keyValidationState.value = KeyValidationState.Testing
            val (isValid, message) = GeminiClient.validateApiKey(cleanKey)
            if (isValid) {
                val saved = SecureApiKeyStorage.saveApiKey(context, cleanKey)
                if (saved) {
                    _keyValidationState.value = KeyValidationState.Success(message)
                    refreshApiKeyStatus()
                    voiceAssistant.speak("Gemini API key successfully verify aur save ho gayi hai.")
                    onComplete?.invoke(true)
                } else {
                    _keyValidationState.value = KeyValidationState.Error("Storage error: Key save nahi ho saki.")
                    onComplete?.invoke(false)
                }
            } else {
                _keyValidationState.value = KeyValidationState.Error(message)
                voiceAssistant.speak("API key invalid hai. Kripya sahi key check karein.")
                onComplete?.invoke(false)
            }
        }
    }

    fun removeCustomApiKey() {
        val context = getApplication<Application>()
        SecureApiKeyStorage.removeCustomApiKey(context)
        refreshApiKeyStatus()
        _keyValidationState.value = KeyValidationState.Idle
        postToastAndLog("Custom API Key removed.")
    }

    fun resetValidationState() {
        _keyValidationState.value = KeyValidationState.Idle
    }

    fun refreshScreenDump() {
        MaxAccessibilityService.instance?.let { service ->
            _screenDump.value = service.getScreenStateDump()
            val newApp = MaxAccessibilityService.currentAppPackage.value
            if (newApp != _currentApp.value && newApp != "Unknown") {
                _previousApp.value = _currentApp.value
                _currentApp.value = newApp
            }
        } ?: run {
            postToastAndLog("Max Accessibility Service is NOT running! Please enable in Settings.", true)
        }
    }

    fun processVoiceCommand(commandText: String) {
        if (commandText.isBlank()) return

        postToastAndLog("🎙️ Command received: \"$commandText\"")

        viewModelScope.launch(Dispatchers.IO) {
            _isProcessing.value = true
            try {
                // STEP -2: CONFIRMATION STATE HANDLING (Checked FIRST before any normal command or context processing)
                if (com.example.util.ConfirmationManager.isAwaitingConfirmation) {
                    val pendingCmd = com.example.util.ConfirmationManager.pendingCommand
                    android.util.Log.d("ConfirmationManager", "CONFIRMATION_STATE: awaiting=true, pendingCommand=\"$pendingCmd\"")
                    val resolution = com.example.util.ConfirmationManager.handleConfirmationResponse(getApplication(), commandText)
                    if (resolution.isHandled) {
                        postToastAndLog("📋 Confirmation Handled: ${resolution.action} -> ${resolution.spokenResponseHindi}")
                        if (resolution.spokenResponseHindi.isNotBlank()) {
                            launch(Dispatchers.Main) {
                                voiceAssistant.speak(resolution.spokenResponseHindi)
                            }
                        }
                        _lastDecision.value = MaxAiDecision(
                            action = if (resolution.action == "executed") "OPEN_APP" else "CANCEL",
                            spokenResponseHindi = resolution.spokenResponseHindi,
                            reasoning = "Confirmation state resolved: ${resolution.action}"
                        )
                        repository.logAction(
                            userCommand = commandText,
                            targetApp = resolution.executedAppName.ifBlank { "Confirmation" },
                            aiReasoning = "Confirmation ${resolution.action}",
                            actionType = if (resolution.action == "executed") "OPEN_APP" else "CANCEL",
                            actionDetail = resolution.spokenResponseHindi,
                            isSuccess = resolution.action == "executed"
                        )
                        if (resolution.action == "executed" && resolution.executedPackage.isNotBlank()) {
                            com.example.util.ContextManager.recordInteraction(
                                userCommand = pendingCmd.ifBlank { commandText },
                                resolvedCommand = resolution.executedAppName,
                                appPackage = resolution.executedPackage,
                                appName = resolution.executedAppName,
                                actionType = "OPEN_APP",
                                targetText = resolution.executedAppName,
                                spokenResponse = resolution.spokenResponseHindi
                            )
                        }
                        _isProcessing.value = false
                        return@launch
                    }
                }

                // STEP -1: CONTEXT AWARENESS & REFERRING WORDS RESOLUTION ("iska", "yeh wala", "usko", "wahi")
                val activePkg = MaxAccessibilityService.currentAppPackage.value.takeIf { it != "Unknown" } ?: _currentApp.value
                com.example.util.ContextManager.updateCurrentApp(getApplication(), activePkg)

                val contextResolution = com.example.util.ContextManager.resolveCommandContext(commandText)

                if (contextResolution.needsClarification) {
                    val prompt = contextResolution.clarificationPrompt
                    postToastAndLog("❓ Context Ambiguity: \"$commandText\" -> Asking user clarification")
                    launch(Dispatchers.Main) {
                        voiceAssistant.speak(prompt)
                    }
                    _lastDecision.value = MaxAiDecision(
                        action = "NONE",
                        spokenResponseHindi = prompt,
                        reasoning = "Referring word '${contextResolution.referringWordFound}' detected without clear context."
                    )
                    repository.logAction(
                        userCommand = commandText,
                        targetApp = com.example.util.ContextManager.getCurrentAppName(),
                        aiReasoning = "Context Clarification",
                        actionType = "CLARIFY",
                        actionDetail = prompt,
                        isSuccess = true
                    )
                    _isProcessing.value = false
                    return@launch
                }

                val effectiveCommand = contextResolution.resolvedCommand
                if (contextResolution.isContextUsed) {
                    postToastAndLog("🧠 Context Resolved: \"$commandText\" ➔ \"$effectiveCommand\"")
                }

                // STEP 0: EXPLICIT MEMORY CHECK ("yaad rakho ki...", "mera favorite app X hai")
                val isExplicitMemorySaved = repository.checkForExplicitMemoryCommand(effectiveCommand)
                if (isExplicitMemorySaved) {
                    val responseHindi = "Ji, maine yeh baat yaad rakh li hai!"
                    postToastAndLog("🧠 Saved to Local Memory DB: \"$effectiveCommand\"")
                    launch(Dispatchers.Main) {
                        voiceAssistant.speak(responseHindi)
                    }
                    _lastDecision.value = MaxAiDecision(
                        action = "SAVE_MEMORY",
                        spokenResponseHindi = responseHindi,
                        reasoning = "Stored explicit user preference/fact into local Room database."
                    )
                    repository.logAction(
                        userCommand = commandText,
                        targetApp = "Local Memory",
                        aiReasoning = "Stored user memory fact locally",
                        actionType = "SAVE_MEMORY",
                        actionDetail = effectiveCommand,
                        isSuccess = true
                    )
                    com.example.util.ContextManager.recordInteraction(
                        userCommand = commandText,
                        resolvedCommand = effectiveCommand,
                        appPackage = "com.example.memory",
                        appName = "Memory",
                        actionType = "SAVE_MEMORY",
                        targetText = effectiveCommand,
                        spokenResponse = responseHindi
                    )
                    _isProcessing.value = false
                    return@launch
                }

                // STEP 0.5: TERMUX HYBRID ENGINE (Priority 1: LOCAL OFFLINE Mapping List; Priority 2: GEMINI API Shell Translator)
                val termuxLocalPattern = TermuxCommandManager.matchLocalCommand(effectiveCommand)
                if (termuxLocalPattern != null) {
                    val bashCmd = termuxLocalPattern.bashCommand
                    val isDestructive = termuxLocalPattern.isDestructive || TermuxCommandManager.isDestructiveCommand(bashCmd, effectiveCommand)

                    if (isDestructive) {
                        ConfirmationManager.setPendingTermuxDestructive(effectiveCommand, bashCmd, "LOCAL")
                        val warnText = "Yeh command files delete ya badlav kar sakti hai: $bashCmd. Kya aap pakka chalana chahte hain?"
                        postToastAndLog("⚠️ Destructive command needs confirmation: $bashCmd")
                        launch(Dispatchers.Main) {
                            voiceAssistant.speak(warnText)
                        }
                        _lastDecision.value = MaxAiDecision(
                            action = "AWAITING_CONFIRMATION",
                            spokenResponseHindi = warnText,
                            reasoning = "Pending destructive Termux execution confirmation"
                        )
                        _isProcessing.value = false
                        return@launch
                    }

                    postToastAndLog("TERMUX_COMMAND_SOURCE: LOCAL")
                    postToastAndLog("TERMUX_COMMAND_EXECUTED: $bashCmd")
                    launch(Dispatchers.Main) {
                        voiceAssistant.speak("${termuxLocalPattern.descriptionHindi}...")
                    }

                    TermuxCommandManager.executeTermuxCommand(getApplication(), bashCmd, "LOCAL") { startedMsg ->
                        postToastAndLog("⚡ $startedMsg")
                    }

                    _lastDecision.value = MaxAiDecision(
                        action = "TERMUX_LOCAL",
                        spokenResponseHindi = termuxLocalPattern.descriptionHindi,
                        reasoning = "Termux RUN_COMMAND (LOCAL): $bashCmd"
                    )

                    repository.logAction(
                        userCommand = commandText,
                        targetApp = "Termux",
                        aiReasoning = "Local Offline Mapping",
                        actionType = "TERMUX_LOCAL",
                        actionDetail = bashCmd,
                        isSuccess = true
                    )

                    com.example.util.ContextManager.recordInteraction(
                        userCommand = commandText,
                        resolvedCommand = effectiveCommand,
                        appPackage = TermuxCommandManager.TERMUX_PACKAGE,
                        appName = "Termux",
                        actionType = "TERMUX_LOCAL",
                        targetText = bashCmd,
                        spokenResponse = termuxLocalPattern.descriptionHindi
                    )

                    _isProcessing.value = false
                    return@launch
                }

                // If not matched in local list, check if user requested a Termux / Linux Shell command -> Ask GEMINI
                val isExplicitTermuxReq = isTermuxTriggerPhrase(effectiveCommand)
                if (isExplicitTermuxReq) {
                    postToastAndLog("🌐 Local list me match nahi mila. Asking Gemini AI: 'Iska sahi Linux/Termux shell command kya banega'...")
                    launch(Dispatchers.Main) {
                        voiceAssistant.speak("Command samajh kar create kar raha hoon...")
                    }

                    val geminiShell = GeminiClient.askGeminiForTermuxCommand(effectiveCommand)
                    if (geminiShell != null && geminiShell.command.isNotBlank()) {
                        val bashCmd = geminiShell.command
                        val isDestructive = geminiShell.isDestructive || TermuxCommandManager.isDestructiveCommand(bashCmd, effectiveCommand)

                        if (isDestructive) {
                            ConfirmationManager.setPendingTermuxDestructive(effectiveCommand, bashCmd, "GEMINI")
                            val warnText = "Yeh command files delete ya system badlav kar sakti hai: $bashCmd. Kya aap pakka chalana chahte hain?"
                            postToastAndLog("⚠️ Destructive command from Gemini requires confirmation: $bashCmd")
                            launch(Dispatchers.Main) {
                                voiceAssistant.speak(warnText)
                            }
                            _lastDecision.value = MaxAiDecision(
                                action = "AWAITING_CONFIRMATION",
                                spokenResponseHindi = warnText,
                                reasoning = "Pending destructive Gemini Termux confirmation"
                            )
                            _isProcessing.value = false
                            return@launch
                        }

                        postToastAndLog("TERMUX_COMMAND_SOURCE: GEMINI")
                        postToastAndLog("TERMUX_COMMAND_EXECUTED: $bashCmd")
                        launch(Dispatchers.Main) {
                            voiceAssistant.speak("${geminiShell.explanationHindi}...")
                        }

                        TermuxCommandManager.executeTermuxCommand(getApplication(), bashCmd, "GEMINI") { startedMsg ->
                            postToastAndLog("⚡ $startedMsg")
                        }

                        _lastDecision.value = MaxAiDecision(
                            action = "TERMUX_GEMINI",
                            spokenResponseHindi = geminiShell.explanationHindi,
                            reasoning = "Termux RUN_COMMAND (GEMINI): $bashCmd"
                        )

                        repository.logAction(
                            userCommand = commandText,
                            targetApp = "Termux",
                            aiReasoning = "Gemini AI Shell Translator",
                            actionType = "TERMUX_GEMINI",
                            actionDetail = bashCmd,
                            isSuccess = true
                        )

                        com.example.util.ContextManager.recordInteraction(
                            userCommand = commandText,
                            resolvedCommand = effectiveCommand,
                            appPackage = TermuxCommandManager.TERMUX_PACKAGE,
                            appName = "Termux",
                            actionType = "TERMUX_GEMINI",
                            targetText = bashCmd,
                            spokenResponse = geminiShell.explanationHindi
                        )

                        _isProcessing.value = false
                        return@launch
                    }
                }

                // STEP 1: LOCAL COMMAND ROUTER (Fast, offline app opening via PackageManager fuzzy matching, weather, reminders & hardware controls)
                val localResult = LocalCommandRouter.tryExecuteLocalCommand(getApplication(), voiceAssistant, effectiveCommand)

                if (localResult.isHandledLocally) {
                    postToastAndLog("⚡ Executed LOCALLY (Offline): ${localResult.actionType} -> ${localResult.message}")

                    if (localResult.spokenResponseHindi.isNotBlank()) {
                        launch(Dispatchers.Main) {
                            voiceAssistant.speak(localResult.spokenResponseHindi)
                        }
                    }

                    _lastDecision.value = MaxAiDecision(
                        action = localResult.actionType,
                        spokenResponseHindi = localResult.spokenResponseHindi,
                        reasoning = "Local Command Router handled offline: ${localResult.message}"
                    )

                    repository.logAction(
                        userCommand = commandText,
                        targetApp = "System Local",
                        aiReasoning = "Offline Local Router",
                        actionType = localResult.actionType,
                        actionDetail = localResult.message,
                        isSuccess = localResult.isSuccess
                    )

                    if (localResult.actionType != "AWAITING_CONFIRMATION" && localResult.actionType != "AWAITING_CHOICE") {
                        com.example.util.ContextManager.recordInteraction(
                            userCommand = commandText,
                            resolvedCommand = effectiveCommand,
                            appPackage = _currentApp.value,
                            appName = com.example.util.ContextManager.getCurrentAppName(),
                            actionType = localResult.actionType,
                            targetText = localResult.message,
                            spokenResponse = localResult.spokenResponseHindi
                        )
                    }

                    _isProcessing.value = false
                    return@launch
                }

                // STEP 2: GENERIC APP CONTROL WITH SHORT-TERM & LONG-TERM MEMORY INJECTION INTO GEMINI AI
                postToastAndLog("🌐 Processing complex command with Memory Context...")
                val service = MaxAccessibilityService.instance
                val isServiceActive = MaxAccessibilityService.isServiceRunning

                if (!isServiceActive) {
                    val warningHindi = "Max accessibility service off hai. Kripya setting me jaakar Max service ko chalu karein."
                    postToastAndLog("⚠️ Accessibility Service Disabled! Prompting user to re-enable...", true)

                    launch(Dispatchers.Main) {
                        voiceAssistant.speak(warningHindi)
                        openAccessibilitySettings(getApplication())
                    }
                }

                val dump = service?.getScreenStateDump() ?: ScreenStateDump(
                    packageName = _currentApp.value,
                    rootNodesFormatted = "Accessibility service inactive. Analyzing command context.",
                    totalClickables = 0
                )
                _screenDump.value = dump

                // Build Short-term Interaction History (Last 8 interactions)
                val recentLogsList = repository.getRecentLogs(8)
                val recentLogsContext = recentLogsList.reversed().joinToString("\n") { log ->
                    val timeStr = timeFormat.format(Date(log.timestamp))
                    "[$timeStr] User: \"${log.userCommand}\" | App: ${log.targetApp} | Action: ${log.actionType} (${log.actionDetail}) | Success: ${log.isSuccess}"
                }

                // Build Long-term User Memories & Preferences
                val memoryList = repository.getAllMemoriesSync()
                val userMemoryContext = memoryList.joinToString("\n") { mem ->
                    "- ${mem.memoryKey} [${mem.category}]: ${mem.memoryValue}"
                }

                // Classify Intent: HARDWARE/TASK COMMAND vs CONVERSATION/QUESTION
                val classifiedIntent = com.example.util.CommandIntentClassifier.classify(effectiveCommand)

                // Build Current Activity Context
                val lastDec = _lastDecision.value
                val intentInstruction = if (classifiedIntent == com.example.util.CommandIntent.CONVERSATION_QUESTION) {
                    "- INTENT TYPE: CONVERSATION / QUESTION. User is seeking information or chitchat. Action MUST be 'NONE'. Do NOT click buttons or open apps. Answer naturally in warm Hindi."
                } else {
                    "- INTENT TYPE: HARDWARE / TASK COMMAND. User wants to perform an action on screen/device."
                }

                val recentContextSummary = com.example.util.ContextManager.getRecentInteractionsSummary()
                val currentAppName = com.example.util.ContextManager.getCurrentAppName()

                val activityContext = """
                    - Currently Active App: ${_currentApp.value} ($currentAppName)
                    - Previously Active App: ${_previousApp.value}
                    - Last Action Executed: ${lastDec?.action ?: "NONE"} (Target: ${lastDec?.targetText ?: lastDec?.textToType ?: "N/A"})
                    - Last Spoken Response: "${lastDec?.spokenResponseHindi ?: "None"}"
                    - Recent Short-term Context Interactions:
                    $recentContextSummary
                    $intentInstruction
                """.trimIndent()

                // Parallel filler timer to prevent awkward network silence for complex Gemini AI queries
                var firstChunkReceived = false
                val fillerJob = launch(Dispatchers.Main) {
                    delay(600)
                    if (!firstChunkReceived) {
                        if (classifiedIntent == com.example.util.CommandIntent.HARDWARE_TASK) {
                            voiceAssistant.speak("Theek hai, abhi karta hoon...")
                        } else {
                            voiceAssistant.speak("Soch raha hoon...")
                        }
                    }
                }

                // Ask Gemini AI Streaming Engine with ultra-fast streaming response
                val decision = GeminiClient.analyzeAndDecideStream(
                    userCommand = effectiveCommand,
                    currentAppPackage = dump.packageName,
                    screenTreeText = dump.rootNodesFormatted,
                    recentLogsContext = recentLogsContext,
                    userMemoryContext = userMemoryContext,
                    activityContext = activityContext,
                    screenshotBitmap = null,
                    onFirstChunkReceived = {
                        firstChunkReceived = true
                        fillerJob.cancel()
                    },
                    onSpokenSentenceChunk = { chunk ->
                        firstChunkReceived = true
                        fillerJob.cancel()
                        launch(Dispatchers.Main) {
                            voiceAssistant.speak(chunk)
                        }
                    }
                )

                fillerJob.cancel()
                _lastDecision.value = decision
                postToastAndLog("💡 Gemini Action: [${decision.action}] target: \"${decision.targetText}\"")

                // Save memory if requested by Gemini auto-extraction
                if (decision.saveMemoryKey.isNotBlank() && decision.saveMemoryValue.isNotBlank()) {
                    repository.saveMemory(decision.saveMemoryKey, decision.saveMemoryValue, "auto_extracted_preference")
                    postToastAndLog("🧠 Auto-saved Memory: ${decision.saveMemoryKey} -> ${decision.saveMemoryValue}")
                }

                // Execute physical gesture/action
                var actionSuccess = false
                var failureDetail = ""

                when (decision.action.uppercase()) {
                    "OPEN_APP" -> {
                        val appQuery = decision.packageName.ifBlank { commandText }
                        postToastAndLog("🚀 Opening app dynamically: $appQuery")

                        val launchRes = LocalCommandRouter.matchAndLaunchAnyInstalledApp(getApplication(), appQuery)
                        actionSuccess = launchRes.isSuccess
                        failureDetail = launchRes.message

                        if (actionSuccess && decision.textToType.isNotBlank()) {
                            delay(2000)
                            service?.let { s ->
                                postToastAndLog("🔍 Searching for \"${decision.textToType}\"...")
                                val clickSearch = s.findAndClick("Search") || s.findAndClick("Search icon") || s.findAndClick("खोजें")
                                delay(600)
                                if (clickSearch) {
                                    s.typeTextIntoActiveField(decision.textToType)
                                } else {
                                    s.tapAtCoordinates(900f, 150f)
                                    delay(500)
                                    s.typeTextIntoActiveField(decision.textToType)
                                }
                            }
                        }
                    }

                    "CLICK" -> {
                        val target = decision.targetText
                        if (target.isNotBlank()) {
                            postToastAndLog("👉 Attempting click on element: \"$target\"")
                            if (service != null) {
                                actionSuccess = service.findAndClick(target)
                                if (!actionSuccess) {
                                    postToastAndLog("⚠️ Click failed. Trying center tap fallback...")
                                    service.tapAtCoordinates(540f, 1000f)
                                    actionSuccess = true
                                    failureDetail = "Fallback center tap"
                                }
                            } else {
                                failureDetail = "Accessibility Service OFF"
                                postToastAndLog("❌ Click FAILED: Accessibility Service is OFF")
                            }
                        }
                    }

                    "TYPE_TEXT" -> {
                        val query = decision.textToType
                        if (query.isNotBlank()) {
                            postToastAndLog("⌨️ Typing text: \"$query\"")
                            if (service != null) {
                                service.findAndClick("Search")
                                delay(600)
                                actionSuccess = service.typeTextIntoActiveField(query)
                            } else {
                                failureDetail = "Accessibility Service OFF"
                                postToastAndLog("❌ Type FAILED: Accessibility Service is OFF")
                            }
                        }
                    }

                    "SWIPE" -> {
                        postToastAndLog("👇 Executing swipe gesture [${decision.swipeDirection}]")
                        if (service != null) {
                            when (decision.swipeDirection.uppercase()) {
                                "UP" -> service.performSwipe(500f, 1500f, 500f, 400f)
                                "DOWN" -> service.performSwipe(500f, 400f, 500f, 1500f)
                                else -> service.performSwipe(500f, 1200f, 500f, 500f)
                            }
                            actionSuccess = true
                        } else {
                            failureDetail = "Accessibility Service OFF"
                            postToastAndLog("❌ Swipe FAILED: Accessibility Service is OFF")
                        }
                    }

                    else -> {
                        actionSuccess = true
                    }
                }

                repository.logAction(
                    userCommand = commandText,
                    targetApp = dump.packageName,
                    aiReasoning = decision.reasoning,
                    actionType = decision.action,
                    actionDetail = if (actionSuccess) (decision.targetText.ifBlank { decision.textToType }) else "FAILED: $failureDetail",
                    isSuccess = actionSuccess
                )

                com.example.util.ContextManager.recordInteraction(
                    userCommand = commandText,
                    resolvedCommand = effectiveCommand,
                    appPackage = dump.packageName,
                    appName = com.example.util.ContextManager.getCurrentAppName(),
                    actionType = decision.action,
                    targetText = if (actionSuccess) (decision.targetText.ifBlank { decision.textToType }) else "FAILED: $failureDetail",
                    spokenResponse = decision.spokenResponseHindi
                )

                delay(1000)
                refreshScreenDump()

            } catch (e: Exception) {
                Log.e(TAG, "Error processing command", e)
                postToastAndLog("❌ Error: ${e.localizedMessage}")
                launch(Dispatchers.Main) {
                    voiceAssistant.speak("Maaf kijiye, command execute karne me error aaya. Kripya dobara try karein.")
                }
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun startListening() {
        voiceAssistant.startListening()
    }

    fun stopListening() {
        voiceAssistant.stopListening()
    }

    val isSmartListeningSleeping: StateFlow<Boolean> = com.example.util.BatteryOptimizationManager.isSmartListeningSleeping
    val inactivityTimeoutSeconds: StateFlow<Int> = com.example.util.BatteryOptimizationManager.inactivityTimeoutSeconds
    val activeWakeLockCount: StateFlow<Int> = com.example.util.BatteryOptimizationManager.activeWakeLockCount
    val batteryOptimizationStatus: StateFlow<String> = com.example.util.BatteryOptimizationManager.batteryOptimizationStatus

    // Security & PIN Lock State
    val isPinLockEnabled: StateFlow<Boolean> = com.example.util.SecurityManager.isPinLockEnabled
    val isSettingsUnlocked: StateFlow<Boolean> = com.example.util.SecurityManager.isSettingsUnlocked
    val failedPinAttempts: StateFlow<Int> = com.example.util.SecurityManager.failedAttempts
    val trustedContactNumber: StateFlow<String> = com.example.util.SecurityManager.trustedContactNumber

    fun verifySettingsPin(pin: String): Boolean {
        val isValid = com.example.util.SecurityManager.verifyPin(getApplication(), pin)
        if (isValid) {
            postToastAndLog("✅ PIN Verified! Settings unlocked.")
        } else {
            postToastAndLog("❌ Galat PIN! Dhyaan se daalein.")
        }
        return isValid
    }

    fun setSettingsPin(newPin: String) {
        com.example.util.SecurityManager.setPin(getApplication(), newPin)
        postToastAndLog("🔒 Naya security PIN set ho gaya.")
    }

    fun togglePinLock(enable: Boolean) {
        com.example.util.SecurityManager.togglePinLock(getApplication(), enable)
        postToastAndLog("PIN Lock is now ${if (enable) "ENABLED" else "DISABLED"}")
    }

    fun setTrustedContact(phone: String) {
        com.example.util.SecurityManager.setTrustedContact(getApplication(), phone)
        postToastAndLog("📞 Trusted contact updated: $phone")
    }

    fun lockSettings() {
        com.example.util.SecurityManager.lockSettings()
    }

    fun setInactivityTimeout(seconds: Int) {
        com.example.util.BatteryOptimizationManager.setInactivityTimeout(seconds)
        postToastAndLog("Smart listening timeout set to ${seconds}s")
    }

    fun optimizePowerNow() {
        com.example.util.BatteryOptimizationManager.forceSleepListening()
        com.example.util.BatteryOptimizationManager.releaseAllWakeLocks()
        voiceAssistant.stopListening()
        voiceAssistant.stopSpeaking()
        postToastAndLog("⚡ Max power optimized: microphone put to sleep, active locks cleared.")
    }

    fun toggleOverlayService(context: Context) {
        if (!Settings.canDrawOverlays(context)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(intent)
            return
        }

        val intent = Intent(context, MaxOverlayService::class.java)
        if (MaxOverlayService.isOverlayRunning) {
            context.stopService(intent)
            postToastAndLog("Max Overlay Service stopped")
        } else {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            postToastAndLog("Max Overlay Floating Widget started!")
        }
        checkServiceStatus()
    }

    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openNotificationListenerSettings(context: Context) {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun toggleAutoReplyState() {
        MaxNotificationListenerService.isAutoReplyEnabled = !MaxNotificationListenerService.isAutoReplyEnabled
        _isAutoReplyActive.value = MaxNotificationListenerService.isAutoReplyEnabled
        postToastAndLog("WhatsApp Auto-Reply is now ${if (_isAutoReplyActive.value) "ENABLED" else "DISABLED"}")
    }

    fun saveUserPreference(key: String, value: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveMemory(key, value, "user_preference")
        }
    }

    fun deleteMemory(key: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteMemory(key)
        }
    }

    fun deleteReminder(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteReminder(id)
        }
    }

    fun clearLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearLogs()
        }
    }

    private fun isTermuxTriggerPhrase(cmd: String): Boolean {
        val lower = cmd.lowercase()
        val terms = listOf(
            "termux", "terminal", "bash", "shell command", "linux command",
            "script run", "script chalao", "command run", "command chalao",
            "curl ", "wget ", "git ", "pip ", "npm ", "pkg install", "apt install",
            "grep ", "find ", "ssh ", "tar ", "zip "
        )
        return terms.any { lower.contains(it) }
    }

    fun recordTermuxOutput(command: String, result: String, source: String) {
        postToastAndLog("TERMUX_COMMAND_SOURCE: $source")
        postToastAndLog("TERMUX_COMMAND_EXECUTED: $command")
        postToastAndLog("TERMUX_OUTPUT: ${result.take(120)}")
        viewModelScope.launch(Dispatchers.IO) {
            repository.logAction(
                userCommand = "Termux: $command",
                targetApp = "Termux",
                aiReasoning = "Output received via $source",
                actionType = "TERMUX_OUTPUT",
                actionDetail = result.take(250),
                isSuccess = true
            )
        }
    }

    fun executeManualTermuxCommand(rawText: String) {
        processVoiceCommand(rawText)
    }

    fun openTermuxApp() {
        val context = getApplication<Application>()
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(TermuxCommandManager.TERMUX_PACKAGE)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } else {
                val storeIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://f-droid.org/packages/com.termux/")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(storeIntent)
            }
        } catch (e: Exception) {
            postToastAndLog("Termux open karne me error: ${e.message}")
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (activeInstance == this) {
            activeInstance = null
        }
        voiceAssistant.destroy()
    }
}
