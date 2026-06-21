package com.adarsh.hellomom

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.lifecycleScope
import com.adarsh.hellomom.core.FcmTokenManager
import com.adarsh.hellomom.core.utils.VoiceAssistant
import com.adarsh.hellomom.data.local.PreferenceManager
import com.adarsh.hellomom.navigation.NavGraph
import com.adarsh.hellomom.presentation.permission.PermissionGate
import com.adarsh.hellomom.presentation.update.UpdateChecker
import com.adarsh.hellomom.ui.theme.HelloMomTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var preferenceManager: PreferenceManager

    @Inject
    lateinit var voiceAssistant: VoiceAssistant

    @Inject
    lateinit var firebaseMessaging: FirebaseMessaging

    @Inject
    lateinit var fcmTokenManager: FcmTokenManager

    @Inject
    lateinit var firebaseAuth: FirebaseAuth

    // Saves the FCM token to the user's doc whenever a user is signed in.
    private val authListener = FirebaseAuth.AuthStateListener { auth ->
        if (auth.currentUser != null) {
            lifecycleScope.launch { fcmTokenManager.syncToken() }
        }
    }

    private lateinit var navController: NavHostController

    fun startVoiceInput(onResult: (String) -> Unit) {
        voiceAssistant.startListening(onResult)
    }

    override fun onDestroy() {
        firebaseAuth.removeAuthStateListener(authListener)
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        applyLocale()

        // Keep each user's FCM token saved in their Firestore doc (added/updated on change).
        firebaseAuth.addAuthStateListener(authListener)

        firebaseMessaging.token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                Log.d("FCM_TOKEN", "************************************************")
                Log.d("FCM_TOKEN", "Current FCM Token: $token")
                Log.d("FCM_TOKEN", "************************************************")
            } else {
                Log.w("FCM_TOKEN", "Fetching FCM registration token failed", task.exception)
            }
        }
        
        enableEdgeToEdge()
        setContent {
            HelloMomTheme {
                PermissionGate()
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    navController = rememberNavController()
                    NavGraph(navController = navController)

                    // Auto-check for an app update on launch (force/optional dialog + in-app install).
                    UpdateChecker()

                    LaunchedEffect(intent) {
                        handleIntent(intent)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val navigateTo = intent?.getStringExtra("navigate_to")
        if (navigateTo == "remind_later") {
            val reminderId = intent.getIntExtra("reminder_id", -1)
            if (reminderId != -1) {
                navController.navigate("remind_later/$reminderId")
            }
        }

        val data = intent?.data ?: return
        val path = data.path ?: return

        Log.d("DEEP_LINK", "Path: $path")

        if (path.contains("/invite/")) {
            val code = path.substringAfterLast("/")
            if (code.isNotEmpty()) {
                Log.d("DEEP_LINK", "Navigating with code: $code")
                navController.navigate("invite/$code") {
                    launchSingleTop = true
                }
            }
        }
    }

    private fun applyLocale() {
        val language = preferenceManager.selectedLanguage
        val locale = when (language) {
            "Hindi" -> Locale("hi")
            "Gujarati" -> Locale("gu")
            "Marathi" -> Locale("mr")
            else -> Locale.ENGLISH
        }
        Locale.setDefault(locale)
        val config = resources.configuration
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.displayMetrics)
    }
}
