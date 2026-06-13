package com.adarsh.hellomom.presentation.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.adarsh.hellomom.presentation.components.AppFooter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(navController: NavController) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy & Policy", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .verticalScroll(scrollState)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Your privacy matters to us. Here is how Hello Mom handles and protects your data. 🔒",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            SupportCard(title = "INFORMATION WE COLLECT", icon = Icons.Default.Policy) {
                Text(
                    text = "We collect the details you provide to personalize your pregnancy journey: " +
                        "your profile (name, email, mobile, due/start date), health entries (water, sleep, " +
                        "weight, steps, symptoms, kicks), reminders, appointments, medicines, journal notes, " +
                        "and documents you upload.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            SupportCard(title = "HOW WE USE YOUR DATA", icon = Icons.Default.DataUsage) {
                Text(
                    text = "Your data is used only to power the app's features — tracking your weekly " +
                        "progress, sending reminders, sharing relevant information with family members you " +
                        "invite, and tailoring health tips. We never sell your data or use it for advertising.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            SupportCard(title = "DATA STORAGE & SECURITY", icon = Icons.Default.Lock) {
                Text(
                    text = "Data is stored on your device and in your private Firebase account, protected by " +
                        "Firebase Authentication. Uploaded documents are kept in secure cloud storage. " +
                        "Access is restricted to you and the family members you explicitly connect.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            SupportCard(title = "YOUR RIGHTS & CONTROL", icon = Icons.Default.VerifiedUser) {
                Text(
                    text = "You can view, edit, or delete your information at any time from within the app. " +
                        "Deleting a document or entry removes it from both your device and the cloud. " +
                        "Logging out keeps your data safe behind your account credentials.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            SupportCard(title = "THIRD-PARTY SERVICES", icon = Icons.Default.Cloud) {
                Text(
                    text = "We use trusted providers — Google Firebase (authentication, database, notifications) " +
                        "and secure cloud storage for documents. These services process data solely to deliver " +
                        "the app's functionality, under their own privacy policies.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            SupportCard(title = "CONTACT US", icon = Icons.Default.Email) {
                Column {
                    Text(
                        text = "Questions about your privacy? Reach out and we'll help.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:adarshdubey122@gmail.com")
                                putExtra(Intent.EXTRA_SUBJECT, "Hello Mom Privacy Query")
                            }
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Email Us")
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            AppFooter()
        }
    }
}
