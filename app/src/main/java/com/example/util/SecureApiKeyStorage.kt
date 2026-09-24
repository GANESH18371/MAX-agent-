package com.example.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.BuildConfig

/**
 * Secure local storage for Gemini AI Engine Key using Android Jetpack Security (EncryptedSharedPreferences)
 * with MasterKey AES256_GCM hardware-backed keystore encryption.
 */
object SecureApiKeyStorage {

    private const val TAG = "SecureApiKeyStorage"
    private const val SECURE_PREFS_NAME = "max_encrypted_api_prefs"
    private const val FALLBACK_PREFS_NAME = "max_api_prefs_fallback"
    private const val KEY_GEMINI_API_KEY = "gemini_api_key"

    @Volatile
    var appContext: Context? = null

    @Volatile
    private var cachedKey: String? = null

    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
        // Warm up cache
        getApiKey(context)
    }

    private fun getEncryptedPrefs(context: Context): SharedPreferences {
        val targetContext = context.applicationContext ?: context
        return try {
            val masterKey = MasterKey.Builder(targetContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                targetContext,
                SECURE_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "EncryptedSharedPreferences init failed, falling back to private prefs", e)
            targetContext.getSharedPreferences(FALLBACK_PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    /**
     * Retrieves the active API key.
     * Priority:
     * 1. User saved custom key in EncryptedSharedPreferences
     * 2. BuildConfig.GEMINI_API_KEY (from .env / AI Studio Secrets)
     * 3. Blank string if not configured
     */
    fun getApiKey(context: Context? = null): String {
        cachedKey?.let { if (it.isNotBlank()) return it }

        val ctx = context?.applicationContext ?: appContext
        if (ctx != null) {
            try {
                val prefs = getEncryptedPrefs(ctx)
                val savedKey = prefs.getString(KEY_GEMINI_API_KEY, null)?.trim()
                if (!savedKey.isNullOrBlank()) {
                    cachedKey = savedKey
                    return savedKey
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading encrypted prefs", e)
            }
        }

        // Fallback to BuildConfig if provided at build time
        val buildKey = BuildConfig.GEMINI_API_KEY.trim()
        if (buildKey.isNotBlank() && buildKey != "MY_GEMINI_API_KEY") {
            return buildKey
        }

        return ""
    }

    /**
     * Checks if user has explicitly saved a custom key in secure storage.
     */
    fun hasCustomKey(context: Context? = null): Boolean {
        val ctx = context?.applicationContext ?: appContext ?: return false
        return try {
            val prefs = getEncryptedPrefs(ctx)
            !prefs.getString(KEY_GEMINI_API_KEY, null).isNullOrBlank()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Returns the raw custom key stored in encrypted prefs, or empty string.
     */
    fun getSavedCustomKey(context: Context? = null): String {
        val ctx = context?.applicationContext ?: appContext ?: return ""
        return try {
            val prefs = getEncryptedPrefs(ctx)
            prefs.getString(KEY_GEMINI_API_KEY, "")?.trim() ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Saves new API key into EncryptedSharedPreferences.
     */
    fun saveApiKey(context: Context, apiKey: String): Boolean {
        return try {
            val ctx = context.applicationContext ?: context
            val cleanKey = apiKey.trim()
            val prefs = getEncryptedPrefs(ctx)
            prefs.edit().putString(KEY_GEMINI_API_KEY, cleanKey).commit()
            cachedKey = cleanKey
            Log.d(TAG, "Gemini API key saved securely in EncryptedSharedPreferences")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save API key", e)
            false
        }
    }

    /**
     * Removes the custom key from EncryptedSharedPreferences.
     */
    fun removeCustomApiKey(context: Context): Boolean {
        return try {
            val ctx = context.applicationContext ?: context
            val prefs = getEncryptedPrefs(ctx)
            prefs.edit().remove(KEY_GEMINI_API_KEY).commit()
            cachedKey = null
            Log.d(TAG, "Custom Gemini API key cleared from secure storage")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove API key", e)
            false
        }
    }

    /**
     * Checks if any valid Gemini API key is configured (either custom or build-time).
     */
    fun isConfigured(context: Context? = null): Boolean {
        return getApiKey(context).isNotBlank()
    }
}
