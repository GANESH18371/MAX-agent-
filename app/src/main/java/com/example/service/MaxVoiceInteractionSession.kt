package com.example.service

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.voice.VoiceInteractionSession
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.ui.MaxViewModel
import com.example.util.ConfirmationManager
import com.example.util.ContextManager
import com.example.util.LocalCommandRouter
import com.example.util.VoiceAssistantManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MaxVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {

    companion object {
        private const val TAG = "MaxVoiceSession"
    }

    private val sessionScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var voiceAssistant: VoiceAssistantManager? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private var statusTextView: TextView? = null
    private var transcriptTextView: TextView? = null
    private var responseTextView: TextView? = null
    private var micButton: FrameLayout? = null
    private var pulseAnimator: ObjectAnimator? = null

    override fun onCreate() {
        super.onCreate()
        voiceAssistant = VoiceAssistantManager(context).apply {
            onSpeechResultListener = { command ->
                handleCapturedVoiceCommand(command)
            }
        }

        // Observe voice amplitude for mic pulse effect
        sessionScope.launch {
            voiceAssistant?.speechAmplitude?.collectLatest { amplitude ->
                val scale = 1.0f + (amplitude * 0.4f)
                micButton?.scaleX = scale
                micButton?.scaleY = scale
            }
        }

        sessionScope.launch {
            voiceAssistant?.transcript?.collectLatest { text ->
                if (text.isNotBlank()) {
                    transcriptTextView?.text = "\"$text\""
                    transcriptTextView?.visibility = View.VISIBLE
                }
            }
        }
    }

    override fun onCreateContentView(): View {
        val rootLayout = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#88000000")) // Scrim
            setOnClickListener {
                hide()
            }
        }

        val bottomSheet = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val params = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM
            }
            layoutParams = params
            setPadding(48, 36, 48, 48)

            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0F172A"))
                cornerRadii = floatArrayOf(48f, 48f, 48f, 48f, 0f, 0f, 0f, 0f)
                setStroke(3, Color.parseColor("#6366F1"))
            }
            elevation = 32f
            isClickable = true
        }

        // 1. Header Bar: Title + Close Icon
        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 24
            }
        }

        val titleView = TextView(context).apply {
            text = "⚡ Max Digital Assistant"
            setTextColor(Color.parseColor("#818CF8"))
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val closeButton = TextView(context).apply {
            text = "✕"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 18f
            setPadding(16, 8, 16, 8)
            setOnClickListener { hide() }
        }

        headerRow.addView(titleView)
        headerRow.addView(closeButton)
        bottomSheet.addView(headerRow)

        // 2. Status Text
        statusTextView = TextView(context).apply {
            text = "Suno... Abhi bolo"
            setTextColor(Color.WHITE)
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 16
            }
        }
        bottomSheet.addView(statusTextView)

        // 3. Pulsing Mic Center View
        val micContainer = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 20
            }
        }

        micButton = FrameLayout(context).apply {
            val sizePx = 140
            layoutParams = FrameLayout.LayoutParams(sizePx, sizePx).apply {
                gravity = Gravity.CENTER
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#4F46E5"))
                setStroke(4, Color.parseColor("#A5B4FC"))
            }
            elevation = 12f

            val icon = ImageView(context).apply {
                setImageResource(android.R.drawable.ic_btn_speak_now)
                setColorFilter(Color.WHITE)
                layoutParams = FrameLayout.LayoutParams(70, 70).apply {
                    gravity = Gravity.CENTER
                }
            }
            addView(icon)

            setOnClickListener {
                statusTextView?.text = "Suno... Abhi bolo"
                transcriptTextView?.visibility = View.GONE
                responseTextView?.visibility = View.GONE
                voiceAssistant?.startListening()
            }
        }
        micContainer.addView(micButton)
        bottomSheet.addView(micContainer)

        // 4. Live Transcript View
        transcriptTextView = TextView(context).apply {
            text = ""
            setTextColor(Color.parseColor("#E0E7FF"))
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            gravity = Gravity.CENTER_HORIZONTAL
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 12
            }
        }
        bottomSheet.addView(transcriptTextView)

        // 5. Response View
        responseTextView = TextView(context).apply {
            text = ""
            setTextColor(Color.parseColor("#34D399")) // Emerald
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_HORIZONTAL
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 20
            }
        }
        bottomSheet.addView(responseTextView)

        // 6. Quick Action Suggestion Chips
        val chipScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val chipContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val suggestions = listOf(
            "YouTube kholo",
            "Flashlight on karo",
            "WiFi toggle",
            "Volume badhao",
            "DND on karo",
            "WhatsApp kholo"
        )

        for (query in suggestions) {
            val chip = TextView(context).apply {
                text = query
                setTextColor(Color.parseColor("#CBD5E1"))
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(28, 14, 28, 14)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#1E293B"))
                    cornerRadius = 32f
                    setStroke(2, Color.parseColor("#334155"))
                }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    rightMargin = 16
                }
                setOnClickListener {
                    handleCapturedVoiceCommand(query)
                }
            }
            chipContainer.addView(chip)
        }

        chipScroll.addView(chipContainer)
        bottomSheet.addView(chipScroll)

        rootLayout.addView(bottomSheet)
        return rootLayout
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        Log.d(TAG, "MaxVoiceInteractionSession onShow: Digital Assistant Activated via Home Long-Press / Gesture!")

        statusTextView?.text = "Suno... Abhi bolo"
        transcriptTextView?.visibility = View.GONE
        responseTextView?.visibility = View.GONE

        // Start pulse animation
        micButton?.let { btn ->
            pulseAnimator?.cancel()
            pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(
                btn,
                PropertyValuesHolder.ofFloat("scaleX", 1.0f, 1.15f, 1.0f),
                PropertyValuesHolder.ofFloat("scaleY", 1.0f, 1.15f, 1.0f)
            ).apply {
                duration = 1200
                repeatCount = ObjectAnimator.INFINITE
                start()
            }
        }

        // Start listening immediately
        voiceAssistant?.startListening()
    }

    override fun onHide() {
        super.onHide()
        Log.d(TAG, "MaxVoiceInteractionSession onHide")
        pulseAnimator?.cancel()
        voiceAssistant?.stopListening()
        voiceAssistant?.stopSpeaking()
    }

    override fun onDestroy() {
        super.onDestroy()
        sessionScope.cancel()
        voiceAssistant?.destroy()
    }

    private fun handleCapturedVoiceCommand(rawCommand: String) {
        if (rawCommand.isBlank()) return
        Log.d(TAG, "handleCapturedVoiceCommand: \"$rawCommand\"")

        pulseAnimator?.cancel()
        micButton?.scaleX = 1.0f
        micButton?.scaleY = 1.0f

        statusTextView?.text = "Process kar raha hoon..."
        transcriptTextView?.text = "\"$rawCommand\""
        transcriptTextView?.visibility = View.VISIBLE

        sessionScope.launch(Dispatchers.IO) {
            // Check if MaxViewModel activeInstance is available
            val activeVm = MaxViewModel.activeInstance
            if (activeVm != null) {
                // Route through active ViewModel
                activeVm.processVoiceCommand(rawCommand)
                mainHandler.post {
                    responseTextView?.text = "Executing with Max..."
                    responseTextView?.visibility = View.VISIBLE
                    mainHandler.postDelayed({ hide() }, 1800)
                }
                return@launch
            }

            // Standalone Digital Assistant Execution (When app is backgrounded)
            // STEP -2: Confirmation State Handling
            if (ConfirmationManager.isAwaitingConfirmation) {
                val res = ConfirmationManager.handleConfirmationResponse(context, rawCommand)
                if (res.isHandled) {
                    mainHandler.post {
                        responseTextView?.text = res.spokenResponseHindi
                        responseTextView?.visibility = View.VISIBLE
                        voiceAssistant?.speak(res.spokenResponseHindi) {
                            mainHandler.postDelayed({ hide() }, 1200)
                        }
                    }
                    return@launch
                }
            }

            // STEP -1: Context Awareness
            val contextResolution = ContextManager.resolveCommandContext(rawCommand)
            val effectiveCommand = contextResolution.resolvedCommand

            // STEP 1: Local Command Router
            val localResult = LocalCommandRouter.tryExecuteLocalCommand(context, voiceAssistant!!, effectiveCommand)
            if (localResult.isHandledLocally) {
                mainHandler.post {
                    responseTextView?.text = localResult.spokenResponseHindi
                    responseTextView?.visibility = View.VISIBLE
                    voiceAssistant?.speak(localResult.spokenResponseHindi) {
                        if (localResult.actionType != "AWAITING_CONFIRMATION" && localResult.actionType != "AWAITING_CHOICE") {
                            mainHandler.postDelayed({ hide() }, 1500)
                        } else {
                            // Re-trigger listening for yes/no confirmation response
                            mainHandler.postDelayed({
                                statusTextView?.text = "Haan ya nahi bole..."
                                voiceAssistant?.startListening()
                            }, 500)
                        }
                    }
                }
            } else {
                // Complex query fallback
                mainHandler.post {
                    val fallbackMsg = "Command prapt hui: \"$effectiveCommand\""
                    responseTextView?.text = fallbackMsg
                    responseTextView?.visibility = View.VISIBLE
                    voiceAssistant?.speak(fallbackMsg) {
                        mainHandler.postDelayed({ hide() }, 1500)
                    }
                }
            }
        }
    }
}
