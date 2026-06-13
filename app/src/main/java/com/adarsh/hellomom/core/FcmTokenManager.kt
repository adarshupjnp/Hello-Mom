package com.adarsh.hellomom.core

import com.adarsh.hellomom.core.utils.SyncLogger
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists each signed-in user's FCM registration token to their own Firestore
 * document (`users/{uid}`) under the field [FIELD] ("FCM Token"), so it can be copied
 * from the Firebase console to send targeted push notifications.
 *
 * The token is written only when it is missing or differs from the stored value, and is
 * refreshed whenever FCM rotates it (see MyFirebaseMessagingService.onNewToken).
 */
@Singleton
class FcmTokenManager @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val messaging: FirebaseMessaging
) {
    /** Fetch the current token for the signed-in user and store it if missing/changed. */
    suspend fun syncToken() {
        val uid = auth.currentUser?.uid ?: return
        val token = runCatching { messaging.token.await() }.getOrNull()
        if (token.isNullOrBlank()) return
        saveIfChanged(uid, token)
    }

    /** Persist a freshly rotated token (called from onNewToken). */
    suspend fun updateToken(token: String) {
        val uid = auth.currentUser?.uid ?: return
        if (token.isBlank()) return
        saveIfChanged(uid, token)
    }

    private suspend fun saveIfChanged(uid: String, token: String) {
        runCatching {
            val docRef = firestore.collection("users").document(uid)
            val existing = docRef.get().await().getString(FIELD)
            if (existing != token) {
                // merge() so we never clobber the rest of the user profile.
                docRef.set(mapOf(FIELD to token), SetOptions.merge()).await()
                SyncLogger.firebaseWrite("UPDATE FCM token", "users/$uid", "token changed/added")
            }
        }.onFailure { SyncLogger.warn("Failed to save FCM token for uid=$uid", it) }
    }

    companion object {
        /** Firestore field title, kept human-readable for easy copy from the console. */
        const val FIELD = "FCM Token"
    }
}
