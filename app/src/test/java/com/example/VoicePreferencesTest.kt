package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.util.MaxVoiceOption
import com.example.util.VoiceAssistantManager
import com.example.util.VoiceGender
import com.example.util.VoicePreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VoicePreferencesTest {

    @Test
    fun `default voice options contains male and female voices`() {
        val options = VoiceAssistantManager.getDefaultVoiceOptions()
        assertTrue(options.size >= 3)

        val hasMale = options.any { it.gender == VoiceGender.MALE }
        val hasFemale = options.any { it.gender == VoiceGender.FEMALE }

        assertTrue("Should have at least one male voice option", hasMale)
        assertTrue("Should have at least one female voice option", hasFemale)
    }

    @Test
    fun `save and retrieve selected voice id from preferences`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // Default value check
        val defaultVoiceId = VoicePreferences.getSelectedVoiceId(context)
        assertEquals("voice_male_hindi", defaultVoiceId)

        // Save female voice
        VoicePreferences.saveSelectedVoiceId(context, "voice_female_hindi")
        assertEquals("voice_female_hindi", VoicePreferences.getSelectedVoiceId(context))

        // Save another voice
        VoicePreferences.saveSelectedVoiceId(context, "voice_assistant_hinglish")
        assertEquals("voice_assistant_hinglish", VoicePreferences.getSelectedVoiceId(context))
    }

    @Test
    fun `pitch and rate are fine tuned for natural sound`() {
        val options = VoiceAssistantManager.getDefaultVoiceOptions()
        for (opt in options) {
            // Natural pitch should be around 0.85 to 1.25, not extreme
            assertTrue("Pitch should be in natural range for ${opt.title}", opt.pitch in 0.85f..1.25f)
            // Conversational speech rate should be around 0.90 to 1.10
            assertTrue("Speech rate should be in natural conversational range for ${opt.title}", opt.speechRate in 0.90f..1.10f)
        }
    }
}
