package com.adarsh.hellomom.presentation.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.adarsh.hellomom.navigation.Screen
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(key1 = true) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is SettingsEffect.NavigateToLogin -> {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0)
                    }
                }
                is SettingsEffect.ShowMessage -> {
                    snackbarHostState.showSnackbar(effect.message)
                }
                SettingsEffect.RestartApp -> {
                    val intent = navController.context.packageManager.getLaunchIntentForPackage(navController.context.packageName)
                    intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    navController.context.startActivity(intent)
                    (navController.context as? android.app.Activity)?.finish()
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { TopAppBar(title = { Text("Settings") }) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Voice Reminders")
                Switch(
                    checked = state.isVoiceEnabled,
                    onCheckedChange = { viewModel.sendIntent(SettingsIntent.OnVoiceToggle(it)) }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text("Language")
            val languages = listOf("English", "Hindi", "Gujarati", "Marathi")
            languages.forEach { lang ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = state.pendingLanguage == lang,
                        onClick = { viewModel.sendIntent(SettingsIntent.OnLanguageSelected(lang)) }
                    )
                    Text(text = lang)
                }
            }

            if (state.pendingLanguage != state.selectedLanguage) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.sendIntent(SettingsIntent.OnApplyLanguage) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Apply & Restart")
                }
                Text(
                    text = "*App will restart to apply language changes.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            if (state.isSyncingBeforeLogout) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Syncing data before logout...")
                    LinearProgressIndicator(
                        progress = { state.syncProgress },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    )
                }
            }

            Button(
                onClick = { viewModel.sendIntent(SettingsIntent.OnLogoutClicked) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                enabled = !state.isSyncingBeforeLogout
            ) {
                Text("Logout")
            }
        }
    }
}
