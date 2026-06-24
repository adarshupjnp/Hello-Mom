package com.adarsh.hellomom.presentation.voice

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.adarsh.hellomom.core.voice.VoicePrefillStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Lets a Compose screen reach the singleton [VoicePrefillStore] without threading it through a
 * ViewModel — so the voice prefill hooks stay a tiny, additive change inside each existing screen.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface VoicePrefillEntryPoint {
    fun voicePrefillStore(): VoicePrefillStore
}

@Composable
fun rememberVoicePrefillStore(): VoicePrefillStore {
    val appContext = LocalContext.current.applicationContext
    return remember {
        EntryPointAccessors.fromApplication(appContext, VoicePrefillEntryPoint::class.java)
            .voicePrefillStore()
    }
}
