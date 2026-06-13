package com.adarsh.hellomom.service

import android.util.Log
import com.adarsh.hellomom.core.FcmTokenManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM_TOKEN", "************************************************")
        Log.d("FCM_TOKEN", "New FCM Token generated: $token")
        Log.d("FCM_TOKEN", "************************************************")

        // Persist the rotated token to the signed-in user's Firestore doc (merge so the
        // rest of the profile is untouched). Fire-and-forget; runs even with no Activity.
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance()
            .collection("users").document(uid)
            .set(mapOf(FcmTokenManager.FIELD to token), SetOptions.merge())
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        
        Log.d("FCM_TOKEN", "Message received from: ${message.from}")

        // 1. Handle Notification payload (sent when app is backgrounded)
        // 2. Handle Data payload (sent always, but we handle it manually for foreground)
        val title = message.notification?.title ?: message.data["title"] ?: "Hello Mom ❤️"
        val body = message.notification?.body ?: message.data["message"] ?: "Time for your reminder"
        val voiceMsg = message.data["voice_message"] ?: body
        val id = message.data["reminder_id"]?.hashCode() ?: System.currentTimeMillis().toInt()

        val intent = android.content.Intent(this, ReminderService::class.java).apply {
            putExtra("reminder_id", id)
            putExtra("title", title)
            putExtra("message", body)
            putExtra("voice_message", voiceMsg)
        }
        
        startForegroundService(intent)
    }
}
