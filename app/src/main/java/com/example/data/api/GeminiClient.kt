package com.example.data.api

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import android.util.LruCache
import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

object GeminiClient {

    private const val TAG = "GeminiClient"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    // Single Central Model-Name Constant for Gemini API
    const val GEMINI_MODEL = "gemini-3.8-flash"
    const val GEMINI_FALLBACK_MODEL = "gemini-2.5-flash"

    // Fast In-Memory Cache for identical screen + command queries
    private val decisionCache = LruCache<String, MaxAiDecision>(50)

    fun getEffectiveApiKey(): String {
        return com.example.util.SecureApiKeyStorage.getApiKey()
    }

    suspend fun validateApiKey(keyToValidate: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val cleanKey = keyToValidate.trim()
        if (cleanKey.isBlank()) {
            return@withContext Pair(false, "API key empty nahi ho sakti.")
        }
        if (cleanKey.length < 20) {
            return@withContext Pair(false, "API key ka format invalid lag raha hai (bahut short hai).")
        }

        try {
            val testRequest = GeminiRequest(
                contents = listOf(
                    GeminiContent(
                        parts = listOf(GeminiPart(text = "Hi"))
                    )
                ),
                generationConfig = GeminiGenerationConfig(
                    responseMimeType = "text/plain",
                    temperature = 0.1f
                )
            )
            val response = try {
                apiService.generateContent(GEMINI_MODEL, cleanKey, testRequest)
            } catch (e: retrofit2.HttpException) {
                if (e.code() == 404) {
                    Log.w(TAG, "Model $GEMINI_MODEL not found during validation, falling back to $GEMINI_FALLBACK_MODEL. Error: ${e.message()}")
                    apiService.generateContent(GEMINI_FALLBACK_MODEL, cleanKey, testRequest)
                } else throw e
            }
            val candidate = response.candidates?.firstOrNull()
            if (candidate != null) {
                Pair(true, "API Key verified successfully with $GEMINI_MODEL! AI Engine active hai. ✅")
            } else {
                Pair(true, "API Key valid hai ($GEMINI_MODEL). ✅")
            }
        } catch (e: retrofit2.HttpException) {
            val code = e.code()
            val errorBody = try { e.response()?.errorBody()?.string() ?: "" } catch (_: Exception) { "" }
            Log.e(TAG, "Validation failed HTTP $code ($GEMINI_MODEL): $errorBody")
            when (code) {
                400 -> Pair(false, "Invalid API Key (HTTP 400): Google AI Studio ne key reject kar di. Kripya sahi key copy karein.")
                403 -> Pair(false, "Access Denied (HTTP 403): Is API key ke paas Gemini access permission nahi hai.")
                404 -> Pair(false, "Model Not Found (HTTP 404): Model '$GEMINI_MODEL' exist nahi karta ya endpoint invalid hai: $errorBody")
                429 -> Pair(false, "Quota Exceeded (HTTP 429): Free tier quota limit reach ho chuki hai.")
                else -> Pair(false, "Validation failed (HTTP $code): ${e.message()}")
            }
        } catch (e: java.net.UnknownHostException) {
            Pair(false, "Internet connection nahi mil raha. Kripya internet check karein.")
        } catch (e: Exception) {
            Log.e(TAG, "Validation error", e)
            Pair(false, "Error: ${e.localizedMessage ?: "Network error"}")
        }
    }

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val apiService: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApiService::class.java)
    }

    /**
     * Ultra-fast scaled and compressed base64 image (max 480px, 50% quality)
     */
    private fun Bitmap.toCompressedBase64(): String {
        val maxDim = 480
        val w = this.width
        val h = this.height
        val scaledBitmap = if (w > maxDim || h > maxDim) {
            val aspect = w.toFloat() / h.toFloat()
            val targetW = if (w >= h) maxDim else (maxDim * aspect).toInt()
            val targetH = if (h > w) maxDim else (maxDim / aspect).toInt()
            Bitmap.createScaledBitmap(this, Math.max(1, targetW), Math.max(1, targetH), true)
        } else {
            this
        }

        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 50, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    private fun Bitmap.toBase64(): String {
        return toCompressedBase64()
    }

    suspend fun analyzeAndDecideStream(
        userCommand: String,
        currentAppPackage: String,
        screenTreeText: String,
        recentLogsContext: String = "",
        userMemoryContext: String = "",
        activityContext: String = "",
        screenshotBitmap: Bitmap? = null,
        onFirstChunkReceived: () -> Unit = {},
        onSpokenSentenceChunk: (String) -> Unit = {}
    ): MaxAiDecision = withContext(Dispatchers.IO) {
        val apiKey = getEffectiveApiKey()
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext genericFallbackReasoning(userCommand, currentAppPackage, screenTreeText)
        }

        // Cache lookup for fast repeated actions
        val cacheKey = "$userCommand|$currentAppPackage|${screenTreeText.take(80)}"
        decisionCache.get(cacheKey)?.let { cached ->
            onFirstChunkReceived()
            if (cached.spokenResponseHindi.isNotBlank()) {
                onSpokenSentenceChunk(cached.spokenResponseHindi)
            }
            return@withContext cached
        }

        val systemPrompt = """
            Aap 'Max' hain — Ganesh Sahani dwara banaya gaya ek smart, warm, friendly aur human-like autonomous AI voice assistant.
            
            IDENTITY & CREATOR RULES:
            - Aapke owner aur creator Ganesh Sahani hain.
            - Agar user pooche "tumhe kisne banaya", "tumhara owner kaun hai", "tum kaun ho", "who made you", "who created you":
              Always answer naturally with warm Hindi: "Mujhe Ganesh Sahani ne banaya hai, main unka personal AI assistant Max hoon."
              
            TONE: Friendly, warm, natural Hindi (like a close human assistant/friend, not robotic instructions).
            
            ORDINAL SELECTION & VERIFICATION RULES ("pehla / doosra / teesra"):
            - Screen tree dump includes a dedicated section: 'VISUAL SCREEN ITEMS (TOP TO BOTTOM ORDER)'.
            - When user specifies ordinal positions like 'pehla/first/no 1', 'doosra/second/no 2', 'teesra/third/no 3', map 'pehla' to Visual Item #1, 'doosra' to Visual Item #2, etc.
            - Set 'targetText' to "Visual Item #1" or the exact text/name of that visual item.
            - VERIFICATION IN RESPONSE: In 'spokenResponseHindi', ALWAYS state the selected item's text or name so the user gets instant voice confirmation (e.g. "[Item Name] wali chat khol raha hoon", "Pehli video [Title] play kar raha hoon").
            
            IMPORTANT: Output must be JSON. Put "spokenResponseHindi" as the FIRST key in JSON.
            
            Output JSON format:
            {
              "spokenResponseHindi": "User ko natural Hindi me warm jawab batao",
              "action": "CLICK | TYPE_TEXT | SWIPE | OPEN_APP | NONE",
              "targetText": "Text or ID to click",
              "textToType": "Query to type",
              "packageName": "Package if opening app",
              "swipeDirection": "UP | DOWN | LEFT | RIGHT",
              "saveMemoryKey": "",
              "saveMemoryValue": "",
              "reasoning": "Short reason"
            }
        """.trimIndent()

        val parts = mutableListOf<GeminiPart>()

        val userPromptBuilder = StringBuilder()
        userPromptBuilder.append("USER COMMAND: \"$userCommand\"\n")

        if (recentLogsContext.isNotBlank()) {
            userPromptBuilder.append("HISTORY: $recentLogsContext\n")
        }
        if (userMemoryContext.isNotBlank()) {
            userPromptBuilder.append("MEMORY: $userMemoryContext\n")
        }
        if (activityContext.isNotBlank()) {
            userPromptBuilder.append("ACTIVITY: $activityContext\n")
        }

        userPromptBuilder.append("ACTIVE APP ($currentAppPackage) TREE:\n${screenTreeText.take(1500)}\n")

        parts.add(GeminiPart(text = userPromptBuilder.toString()))

        screenshotBitmap?.let { bmp ->
            parts.add(
                GeminiPart(
                    inlineData = GeminiInlineData(
                        mimeType = "image/jpeg",
                        data = bmp.toCompressedBase64()
                    )
                )
            )
        }

        val request = GeminiRequest(
            contents = listOf(GeminiContent(parts = parts)),
            systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemPrompt))),
            generationConfig = GeminiGenerationConfig(responseMimeType = "application/json", temperature = 0.2f)
        )

        val accumulatedJson = StringBuilder()
        var firstChunkReported = false
        var spokenSentenceEmitted = false

        try {
            val responseBody = try {
                apiService.generateContentStream(GEMINI_MODEL, apiKey, request)
            } catch (e: retrofit2.HttpException) {
                if (e.code() == 404) {
                    Log.w(TAG, "Model '$GEMINI_MODEL' not found during stream, falling back to '$GEMINI_FALLBACK_MODEL'. Error: ${e.message()}")
                    apiService.generateContentStream(GEMINI_FALLBACK_MODEL, apiKey, request)
                } else throw e
            }
            responseBody.byteStream().bufferedReader().use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val rawLine = line?.trim() ?: continue
                    if (!rawLine.startsWith("data:")) continue
                    val jsonPayload = rawLine.removePrefix("data:").trim()
                    if (jsonPayload.isEmpty() || jsonPayload == "[DONE]") continue

                    if (!firstChunkReported) {
                        firstChunkReported = true
                        onFirstChunkReceived()
                    }

                    try {
                        val jsonObject = JSONObject(jsonPayload)
                        val candidates = jsonObject.optJSONArray("candidates")
                        val firstCand = candidates?.optJSONObject(0)
                        val content = firstCand?.optJSONObject("content")
                        val partsArr = content?.optJSONArray("parts")
                        val chunkText = partsArr?.optJSONObject(0)?.optString("text") ?: ""

                        if (chunkText.isNotEmpty()) {
                            accumulatedJson.append(chunkText)

                            // Real-time sentence extraction for Jarvis-like instant TTS
                            if (!spokenSentenceEmitted && accumulatedJson.contains("\"spokenResponseHindi\"")) {
                                val currentStr = accumulatedJson.toString()
                                val matchResult = Regex("\"spokenResponseHindi\"\\s*:\\s*\"([^\"]*)\"").find(currentStr)
                                if (matchResult != null) {
                                    val hindiText = matchResult.groupValues[1]
                                    if (hindiText.isNotBlank()) {
                                        onSpokenSentenceChunk(hindiText)
                                        spokenSentenceEmitted = true
                                    }
                                } else {
                                    // Partial extraction if sentence end punctuation found
                                    val partialMatch = Regex("\"spokenResponseHindi\"\\s*:\\s*\"([^\"]+)").find(currentStr)
                                    if (partialMatch != null) {
                                        val partialText = partialMatch.groupValues[1]
                                        if (partialText.contains("।") || partialText.contains(".") || partialText.contains("!")) {
                                            val cleanSentence = partialText.replace("\\n", " ").trim()
                                            if (cleanSentence.length >= 6) {
                                                onSpokenSentenceChunk(cleanSentence)
                                                spokenSentenceEmitted = true
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // Continue reading chunks
                    }
                }
            }

            val finalJsonStr = accumulatedJson.toString()
            val adapter = moshi.adapter(MaxAiDecision::class.java)
            val decision = adapter.fromJson(finalJsonStr) ?: genericFallbackReasoning(userCommand, currentAppPackage, screenTreeText)

            decisionCache.put(cacheKey, decision)
            return@withContext decision

        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext analyzeAndDecide(userCommand, currentAppPackage, screenTreeText, recentLogsContext, userMemoryContext, activityContext, screenshotBitmap)
        }
    }

    suspend fun analyzeAndDecide(
        userCommand: String,
        currentAppPackage: String,
        screenTreeText: String,
        recentLogsContext: String = "",
        userMemoryContext: String = "",
        activityContext: String = "",
        screenshotBitmap: Bitmap? = null
    ): MaxAiDecision {
        val apiKey = getEffectiveApiKey()
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return genericFallbackReasoning(userCommand, currentAppPackage, screenTreeText)
        }

        val systemPrompt = """
            Aap 'Max' hain — Ganesh Sahani dwara banaya gaya ek smart, warm, friendly aur human-like autonomous AI voice assistant.
            
            IDENTITY & CREATOR RULES:
            - Aapke owner aur creator Ganesh Sahani hain.
            - Agar user pooche "tumhe kisne banaya", "tumhara owner kaun hai", "tum kaun ho", "who made you", "who created you":
              Always answer naturally with warm Hindi: "Mujhe Ganesh Sahani ne banaya hai, main unka personal AI assistant Max hoon."
              
            TONE: Friendly, warm, natural Hindi (like a close human assistant/friend, not robotic instructions).
            
            ORDINAL SELECTION & VERIFICATION RULES ("pehla / doosra / teesra"):
            - Screen tree dump includes a dedicated section: 'VISUAL SCREEN ITEMS (TOP TO BOTTOM ORDER)'.
            - When user specifies ordinal positions like 'pehla/first/no 1', 'doosra/second/no 2', 'teesra/third/no 3', map 'pehla' to Visual Item #1, 'doosra' to Visual Item #2, etc.
            - Set 'targetText' to "Visual Item #1" or the exact text/name of that visual item.
            - VERIFICATION IN RESPONSE: In 'spokenResponseHindi', ALWAYS state the selected item's text or name so the user gets instant voice confirmation (e.g. "[Item Name] wali chat khol raha hoon", "Pehli video [Title] play kar raha hoon").
            
            Aapke paas device ki RECENT MEMORY, INTERACTION HISTORY, USER PREFERENCES aur CURRENT ACTIVITY CONTEXT ka poora access hai.
            
            Aapko milega:
            1. USER COMMAND (Hindi / English)
            2. RECENT CONVERSATION HISTORY (Last interactions with user)
            3. USER MEMORIES & PREFERENCES (User ki pasand, habits, stored facts)
            4. CURRENT ACTIVITY CONTEXT (Active app, previous app, last action performed)
            5. ACCESSIBILITY SCREEN TREE (Abhi screen par visible buttons, text, fields ka tree data)
            
            Instructions:
            - Context Understanding: Agar user pronouns ya indirect phrases bole (jaise "iska volume badhao", "piche jao", "wahi wala fir se chalao", "dobara search karo", "jaisa maine bola tha"):
              Use RECENT CONVERSATION HISTORY aur CURRENT ACTIVITY CONTEXT to understand what "iska" / "wahi wala" / "dobara" refers to.
            - User Memory Auto-Extraction: Agar user apni koi pasand ya aadat bataye (e.g. "mujhe Arijit Singh pasand hai", "mera favorite app Instagram hai"), to "saveMemoryKey" me key (e.g. "favorite_artist") aur "saveMemoryValue" me value (e.g. "Arijit Singh") do.
            
            Aapka output JSON format me hona chahiye:
            1. "spokenResponseHindi": User ko natural, warm, polite, human-like Hindi me jawab do (e.g., "Aapke pasandida artist Arijit Singh ke gaane search kar raha hoon.", "Wapas purani screen par jaa raha hoon.").
            2. "action": MUST be one of:
               - "CLICK": Tap on a specific element found on screen. Set "targetText" to the exact button text, id, or contentDescription.
               - "TYPE_TEXT": Type text/search query into active input field. Set "textToType" to the query string.
               - "SWIPE": Scroll up or down on feeds, lists, or pages. Set "swipeDirection": "UP" or "DOWN".
               - "OPEN_APP": If user asked to open a specific app. Set "packageName" or app name.
               - "NONE": If answering a question or describing screen content.
            3. "targetText": Button text, content description, or ID to click.
            4. "textToType": Query or message to type into input field.
            5. "packageName": App package string if OPEN_APP action is needed.
            6. "swipeDirection": "UP", "DOWN", "LEFT", "RIGHT".
            7. "saveMemoryKey" & "saveMemoryValue": Key/Value to store in Room DB if user shared a preference or fact.
            8. "reasoning": Short explanation referencing current screen and user memory context.
        """.trimIndent()

        val parts = mutableListOf<GeminiPart>()
        
        val userPromptBuilder = StringBuilder()
        userPromptBuilder.append("USER COMMAND (Hindi): \"$userCommand\"\n\n")

        if (recentLogsContext.isNotBlank()) {
            userPromptBuilder.append("--- RECENT INTERACTION HISTORY (Memory) ---\n$recentLogsContext\n\n")
        }

        if (userMemoryContext.isNotBlank()) {
            userPromptBuilder.append("--- USER PREFERENCES & STORED FACTS ---\n$userMemoryContext\n\n")
        }

        if (activityContext.isNotBlank()) {
            userPromptBuilder.append("--- CURRENT ACTIVITY CONTEXT ---\n$activityContext\n\n")
        }

        userPromptBuilder.append("--- ACTIVE APP ($currentAppPackage) ACCESSIBILITY SCREEN TREE ---\n$screenTreeText\n")

        parts.add(GeminiPart(text = userPromptBuilder.toString()))

        screenshotBitmap?.let { bmp ->
            parts.add(
                GeminiPart(
                    inlineData = GeminiInlineData(
                        mimeType = "image/jpeg",
                        data = bmp.toBase64()
                    )
                )
            )
        }

        val request = GeminiRequest(
            contents = listOf(GeminiContent(parts = parts)),
            systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemPrompt))),
            generationConfig = GeminiGenerationConfig(responseMimeType = "application/json", temperature = 0.2f)
        )

        return try {
            val response = try {
                apiService.generateContent(GEMINI_MODEL, apiKey, request)
            } catch (e: retrofit2.HttpException) {
                if (e.code() == 404) {
                    Log.w(TAG, "Model '$GEMINI_MODEL' not found during decision, falling back to '$GEMINI_FALLBACK_MODEL'. Error: ${e.message()}")
                    apiService.generateContent(GEMINI_FALLBACK_MODEL, apiKey, request)
                } else throw e
            }
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (jsonText != null) {
                val adapter = moshi.adapter(MaxAiDecision::class.java)
                adapter.fromJson(jsonText) ?: genericFallbackReasoning(userCommand, currentAppPackage, screenTreeText)
            } else {
                genericFallbackReasoning(userCommand, currentAppPackage, screenTreeText)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            if (e is retrofit2.HttpException && e.code() == 429) {
                return MaxAiDecision(
                    spokenResponseHindi = "Gemini API rate limit exceed ho gayi hai. Thodi der me dubara try karein ya Settings me nayi API key dalein.",
                    action = "NONE",
                    reasoning = "Gemini API rate limit exceeded (HTTP 429)"
                )
            }
            genericFallbackReasoning(userCommand, currentAppPackage, screenTreeText)
        }
    }

    suspend fun analyzeSceneImage(bitmap: Bitmap): String {
        val apiKey = getEffectiveApiKey()
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return "Gemini API key missing hai. Settings me jakar Gemini API key add karein."
        }

        val prompt = "Aap 'Max AI' ho. Iss camera image ko dekho aur user ko Hindi me short me 1-2 sentence me batao ki saamne kya rakha hai ya kya dikh raha hai."

        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(
                        GeminiPart(text = prompt),
                        GeminiPart(
                            inlineData = GeminiInlineData(
                                mimeType = "image/jpeg",
                                data = bitmap.toBase64()
                            )
                        )
                    )
                )
            ),
            generationConfig = GeminiGenerationConfig(temperature = 0.3f)
        )

        return try {
            val response = try {
                apiService.generateContent(GEMINI_MODEL, apiKey, request)
            } catch (e: retrofit2.HttpException) {
                if (e.code() == 404) {
                    apiService.generateContent(GEMINI_FALLBACK_MODEL, apiKey, request)
                } else throw e
            }
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "Mujhe image me clear cheez samajh nahi aayi."
        } catch (e: Exception) {
            "Scene analysis karte samay error aaya: ${e.localizedMessage}"
        }
    }

    /**
     * Completely generic app-agnostic offline fallback reasoning
     */
    fun genericFallbackReasoning(
        userCommand: String,
        currentAppPackage: String,
        screenTreeText: String
    ): MaxAiDecision {
        val lowerCmd = userCommand.lowercase()
        val lowerTree = screenTreeText.lowercase()

        // 1. Skip Ad command
        if (lowerCmd.contains("ad") || lowerCmd.contains("skip") || lowerCmd.contains("विज्ञापन")) {
            val skipKeywords = listOf("skip ad", "skip ads", "skip", "विज्ञापन छोड़ें", "skip_ad_button")
            val matchedKeyword = skipKeywords.firstOrNull { lowerTree.contains(it) } ?: "Skip"
            return MaxAiDecision(
                spokenResponseHindi = "Skip button tap kar raha hoon.",
                action = "CLICK",
                targetText = matchedKeyword,
                reasoning = "Skip button detected on screen."
            )
        }

        // 2. Screen Status Summary ("abhi kya ho raha hai")
        if (lowerCmd.contains("abhi kya") || lowerCmd.contains("kya ho raha hai") || lowerCmd.contains("screen")) {
            val simpleName = currentAppPackage.substringAfterLast(".").replaceFirstChar { it.uppercase() }
            return MaxAiDecision(
                spokenResponseHindi = "Abhi screen par $simpleName app khuli hui hai.",
                action = "NONE",
                reasoning = "Screen summary request."
            )
        }

        // 3. Scroll / Swipe command
        if (lowerCmd.contains("agla") || lowerCmd.contains("scroll") || lowerCmd.contains("next") || lowerCmd.contains("niche")) {
            return MaxAiDecision(
                spokenResponseHindi = "Niche scroll kar raha hoon.",
                action = "SWIPE",
                swipeDirection = "UP",
                reasoning = "Scroll down gesture request."
            )
        }

        // 4. Search command inside current app
        if (lowerCmd.contains("search") || lowerCmd.contains("dhoondho") || lowerCmd.contains("play") || lowerCmd.contains("chalao")) {
            val queryClean = lowerCmd
                .replace("par", "")
                .replace("khol ke", "")
                .replace("play karo", "")
                .replace("chalao", "")
                .replace("search karo", "")
                .replace("dhoondho", "")
                .replace("karo", "")
                .trim()

            if (lowerTree.contains("search") || lowerTree.contains("khoj") || lowerTree.contains(" search_button")) {
                return MaxAiDecision(
                    spokenResponseHindi = "\"$queryClean\" search kar raha hoon.",
                    action = "TYPE_TEXT",
                    targetText = "Search",
                    textToType = queryClean,
                    reasoning = "Search input box found."
                )
            } else {
                return MaxAiDecision(
                    spokenResponseHindi = "\"$queryClean\" search karne ke liye search icon tap kar raha hoon.",
                    action = "CLICK",
                    targetText = "Search",
                    textToType = queryClean,
                    reasoning = "Clicking search icon in current app."
                )
            }
        }

        return MaxAiDecision(
            spokenResponseHindi = "Screen parse karke action le raha hoon.",
            action = "CLICK",
            targetText = "Search",
            reasoning = "Generic app-agnostic action."
        )
    }

    /**
     * Ask Gemini AI for complex/unknown Linux & Termux bash command translation.
     */
    suspend fun askGeminiForTermuxCommand(userVoiceQuery: String): TermuxAiCommandResult? = withContext(Dispatchers.IO) {
        val apiKey = getEffectiveApiKey()
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext null
        }

        val systemPrompt = """
            You are a Linux and Termux bash command generator for Max Voice Assistant on Android.
            The user will give a Hindi or Hinglish voice command asking to do something in the terminal / shell / Termux on Android.
            Your task is to convert this user request into a safe, valid, single-line Bash command for Termux.

            CRITICAL RULES:
            1. Output MUST be strictly JSON in the following schema:
            {
              "command": "the exact bash command to execute",
              "explanationHindi": "Short explanation in Hindi of what this command will do",
              "isDestructive": true or false
            }
            2. Set "isDestructive": true if the command deletes files, removes packages, formats, reboots, or overwrites critical paths (e.g. rm, rmdir, kill -9, mkfs, dd, wipe, >). Otherwise false.
            3. If the user request is dangerous (e.g. formatting Android root or malicious scripts), provide a harmless safe command or explanation.
            4. Output JSON ONLY.
        """.trimIndent()

        try {
            val request = GeminiRequest(
                contents = listOf(
                    GeminiContent(
                        parts = listOf(GeminiPart(text = "USER SHELL REQUEST: \"$userVoiceQuery\""))
                    )
                ),
                systemInstruction = GeminiContent(
                    parts = listOf(GeminiPart(text = systemPrompt))
                ),
                generationConfig = GeminiGenerationConfig(
                    responseMimeType = "application/json",
                    temperature = 0.1f
                )
            )

            val response = try {
                apiService.generateContent(GEMINI_MODEL, apiKey, request)
            } catch (e: retrofit2.HttpException) {
                if (e.code() == 404) {
                    apiService.generateContent(GEMINI_FALLBACK_MODEL, apiKey, request)
                } else throw e
            }
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: return@withContext null

            val jsonObject = JSONObject(jsonText)
            val command = jsonObject.optString("command", "").trim()
            val explanationHindi = jsonObject.optString("explanationHindi", "Linux command execute kar raha hoon.")
            val isDestructive = jsonObject.optBoolean("isDestructive", false)

            if (command.isNotBlank()) {
                TermuxAiCommandResult(
                    command = command,
                    explanationHindi = explanationHindi,
                    isDestructive = isDestructive
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating Termux command from Gemini", e)
            null
        }
    }
}

data class TermuxAiCommandResult(
    val command: String,
    val explanationHindi: String,
    val isDestructive: Boolean
)
