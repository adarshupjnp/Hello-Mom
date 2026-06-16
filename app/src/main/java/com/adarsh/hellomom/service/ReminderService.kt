package com.adarsh.hellomom.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import com.adarsh.hellomom.MainActivity
import com.adarsh.hellomom.R
import com.adarsh.hellomom.core.utils.sanitizeForSpeech
import com.adarsh.hellomom.data.local.PreferenceManager
import com.adarsh.hellomom.notification.NotificationActionReceiver
import java.util.*

class ReminderService : Service(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var reminderId: Int = -1
    private var title: String? = null
    private var message: String? = null
    private var voiceMessage: String? = null
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this, this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        reminderId = intent?.getIntExtra("reminder_id", -1) ?: -1
        title = intent?.getStringExtra("title")
        message = intent?.getStringExtra("message")
        voiceMessage = intent?.getStringExtra("voice_message") ?: message

        createNotificationChannel()
        val notification = createNotification()
        startForeground(reminderId.coerceAtLeast(1), notification)

        if (isTtsReady) {
            speakMessage()
        }

        return START_NOT_STICKY
    }

    private fun requestAudioFocus(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(playbackAttributes)
                .build()
            return audioManager?.requestAudioFocus(focusRequest!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            return audioManager?.requestAudioFocus(null, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun speakMessage() {
        // Respect the user's "Allow Voice Reminder" preference (defaults ON).
        // The notification still shows; only the spoken TTS is suppressed when off.
        if (!PreferenceManager(applicationContext).isVoiceEnabled) return
        if (tts != null && (title != null || message != null)) {
            if (requestAudioFocus()) {
                // Strip emojis/icons from the title & message so TTS reads only the words
                // (e.g. a "❤️" in a reminder isn't spoken as "dil"). The notification UI
                // still shows the original text with its emojis.
                val fullMessage = sanitizeForSpeech("Reminder. $title. $message")
                tts?.speak(fullMessage, TextToSpeech.QUEUE_FLUSH, null, "reminder_tts")
            }
        }
    }

    private fun createNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )

        // Done Action
        val doneIntent = Intent(this, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_DONE
            putExtra("reminder_id", reminderId)
        }
        val donePendingIntent = PendingIntent.getBroadcast(
            this, reminderId + 1000, doneIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Snooze Options (default 10m)
        val snoozeIntent = Intent(this, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_SNOOZE
            putExtra("reminder_id", reminderId)
            putExtra("snooze_minutes", 10)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            this, reminderId + 2000, snoozeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, "reminder_channel")
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_app_logo)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(android.R.drawable.checkbox_on_background, "Done", donePendingIntent)
            .addAction(android.R.drawable.ic_lock_idle_alarm, "Snooze (10m)", snoozePendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "reminder_channel",
                "Pregnancy Care Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                enableVibration(true)
                setSound(null, null) // Use TTS instead
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // Read reminders in the user's selected app language.
            val locale = localeFromPreference()
            val result = tts?.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.language = Locale.ENGLISH
            }
            isTtsReady = true
            speakMessage()
        }
    }

    private fun localeFromPreference(): Locale =
        when (PreferenceManager(applicationContext).selectedLanguage) {
            "Hindi" -> Locale("hi")
            "Gujarati" -> Locale("gu")
            "Marathi" -> Locale("mr")
            else -> Locale.ENGLISH
        }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequest != null) {
            audioManager?.abandonAudioFocusRequest(focusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager?.abandonAudioFocus(null)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
