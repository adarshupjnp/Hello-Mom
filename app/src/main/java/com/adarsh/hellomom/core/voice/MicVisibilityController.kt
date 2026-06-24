package com.adarsh.hellomom.core.voice

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-wide switch for the floating voice mic's visibility.
 *
 * The mic overlay is hosted at the Activity scope (in MainActivity), while screens that open a
 * full-screen AI web chat live inside the NavHost — so a screen's `VoiceAssistantViewModel` obtained
 * via `hiltViewModel()` is a DIFFERENT instance than the overlay's. This @Singleton is the bridge:
 * screens flip it, every VoiceAssistantViewModel instance mirrors it into its state, and the overlay
 * (reading its own instance's state) hides the mic accordingly.
 */
@Singleton
class MicVisibilityController @Inject constructor() {
    // Starts hidden: the mic appears only after a screen explicitly shows it (the dashboard does so
    // once loading finishes). This guarantees the welcome greeting — which fires when the mic first
    // becomes visible — never starts over the loading shimmer.
    private val _visible = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = _visible.asStateFlow()

    fun setVisible(visible: Boolean) {
        _visible.value = visible
    }
}
