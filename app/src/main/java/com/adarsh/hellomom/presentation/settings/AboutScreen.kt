package com.adarsh.hellomom.presentation.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.adarsh.hellomom.presentation.components.AppFooter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About Hello Mom", fontWeight = FontWeight.Bold) },
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
                .verticalScroll(rememberScrollState())
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "🤱 Hello Mom",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "💖 Your Smart Pregnancy Companion",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.SemiBold
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Hello Mom is a modern pregnancy care application designed to support mothers throughout their pregnancy journey. From tracking your baby's development to managing health records, reminders, nutrition, and appointments, everything is available in one secure and easy-to-use place.",
                    style = MaterialTheme.typography.bodyLarge,
                    lineHeight = 24.sp
                )
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp))
                
                Text(
                    text = "✨ Features",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                FeatureItem("👶 Pregnancy Tracking", "Track pregnancy week-by-week with real-time progress, trimester information, baby growth insights, and personalized recommendations.")
                FeatureItem("💊 Medicine Management", "Manage medicines, supplements, and vitamins with smart reminders and dosage tracking.")
                FeatureItem("📅 Appointments", "Schedule and manage doctor visits, scans, and checkups without missing important dates.")
                FeatureItem("🏥 Medical Records", "Store and organize medical reports, prescriptions, test results, and health documents securely.")
                FeatureItem("💧 Health Monitoring", "Track daily water intake, sleep hours, weight changes, and physical activity throughout your pregnancy.")
                FeatureItem("🔔 Smart Reminders", "Receive notifications for medicines, appointments, hydration, exercise, and important pregnancy milestones.")
                FeatureItem("💰 Expense Tracker", "Monitor pregnancy-related expenses and generate detailed reports whenever needed.")
                FeatureItem("📄 PDF Export", "Export appointments, reports, medicines, nutrition data, and expenses as beautifully formatted PDF reports.")

                HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp))
                
                Text(
                    text = "🌟 Why Hello Mom?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                val benefits = listOf(
                    "Simple & Easy to Use",
                    "Secure Local Data Storage",
                    "Modern Material 3 Design",
                    "Pregnancy Week Calculator",
                    "Baby Development Insights",
                    "Personalized Health Tracking",
                    "Multi-language Support",
                    "Offline-Friendly Experience"
                )
                
                benefits.forEach { benefit ->
                    Row(
                        modifier = Modifier.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("✅ ", fontSize = 16.sp)
                        Text(text = benefit, style = MaterialTheme.typography.bodyLarge)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp))
                
                SectionHeader("🛡️ Privacy First")
                Text(
                    text = "Your health data belongs to you. Hello Mom prioritizes privacy and securely stores your information while providing a safe pregnancy tracking experience.",
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(modifier = Modifier.height(24.dp))
                
                SectionHeader("❤️ Made with Care")
                Text(
                    text = "Developed to make pregnancy tracking easier, smarter, and more enjoyable for every mother.",
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(modifier = Modifier.height(24.dp))
                
                Text(text = "🚀 Version", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(text = "v1.0.0", style = MaterialTheme.typography.bodyLarge)

                Spacer(modifier = Modifier.height(16.dp))
                
                Text(text = "💙 Thank You", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Thank you for choosing Hello Mom. We are honored to be part of your beautiful motherhood journey.",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            AppFooter()
        }
    }
}

@Composable
fun FeatureItem(title: String, description: String) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Text(text = title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text(text = description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}
