package com.adarsh.hellomom.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Reusable in-app AI assistant launcher (the same experience as the dashboard's "AI" button),
 * extracted so any screen — e.g. Baby Progress — can offer it without duplicating the web-view
 * hosting or chooser. Pair [AiProviderChooserDialog] (pick a provider) with [AiAssistantOverlay]
 * (full-screen chat) and an "AI" FAB; see BabyProgressScreen for the wiring.
 */
enum class AiAssistantProvider(
    val label: String,
    private val baseUrl: String,
    private val queryParam: String?
) {
    PERPLEXITY("Perplexity", "https://www.perplexity.ai/search", "q"),
    CHATGPT("ChatGPT", "https://chatgpt.com/", "q"),
    COPILOT("Copilot", "https://copilot.microsoft.com/", "q"),
    GEMINI("Gemini", "https://gemini.google.com/app", null);

    /** URL to open, with the auto-search prompt appended when the provider supports it. */
    fun urlForPrompt(prompt: String): String {
        if (queryParam == null || prompt.isBlank()) return baseUrl
        val encoded = java.net.URLEncoder.encode(prompt, "UTF-8")
        val separator = if (baseUrl.contains("?")) "&" else "?"
        return "$baseUrl$separator$queryParam=$encoded"
    }
}

@Composable
fun AiProviderChooserDialog(
    onSelect: (AiAssistantProvider) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Open AI Assistant") },
        text = {
            Column {
                Text(
                    text = "Choose an assistant to chat with:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                AiAssistantProvider.values().forEach { provider ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelect(provider) }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.SmartToy, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = provider.label,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/**
 * Full-screen AI chat overlay: a top bar with a back/close action above the [AiWebView] for the
 * chosen [provider]. Render it as a top-level layer (e.g. inside a Box over a Scaffold) while a
 * provider is active. [AiWebView] already walks page history on Back before calling [onClose].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantOverlay(
    provider: AiAssistantProvider,
    prompt: String,
    onClose: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(provider.label, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close")
                    }
                }
            )
            AiWebView(
                url = provider.urlForPrompt(prompt),
                onClose = onClose,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
