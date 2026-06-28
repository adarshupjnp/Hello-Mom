package com.adarsh.hellomom.data.local

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferenceManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("hello_mom_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_LANGUAGE = "selected_language"
        private const val KEY_VOICE_ENABLED = "voice_enabled"
        private const val KEY_VOICE_WELCOME_SHOWN = "voice_welcome_shown"
        private const val KEY_LAST_VOICE_WELCOME_DATE = "last_voice_welcome_date"
    }

    // Default language is Hindi: the voice assistant (and TTS) speak Hindi until the user
    // explicitly picks another language on the Login or Profile screen.
    var selectedLanguage: String
        get() = prefs.getString(KEY_LANGUAGE, "Hindi") ?: "Hindi"
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value).apply()

    var isVoiceEnabled: Boolean
        get() = prefs.getBoolean(KEY_VOICE_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_VOICE_ENABLED, value).apply()

    // True once the long voice welcome has been spoken (first app open after registration). On every
    // later launch the assistant gives the short greeting instead. Reset by [clear] on logout, so a
    // newly registered user hears the full welcome again.
    var hasShownVoiceWelcome: Boolean
        get() = prefs.getBoolean(KEY_VOICE_WELCOME_SHOWN, false)
        set(value) = prefs.edit().putBoolean(KEY_VOICE_WELCOME_SHOWN, value).apply()

    var lastVoiceWelcomeDate: String
        get() = prefs.getString(KEY_LAST_VOICE_WELCOME_DATE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_VOICE_WELCOME_DATE, value).apply()

    fun clear() {
        prefs.edit().clear().apply()
    }
}
