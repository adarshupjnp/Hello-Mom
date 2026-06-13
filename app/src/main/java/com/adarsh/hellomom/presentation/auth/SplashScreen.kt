package com.adarsh.hellomom.presentation.auth

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.adarsh.hellomom.R
import com.adarsh.hellomom.navigation.Screen
import com.airbnb.lottie.compose.*
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(navController: NavController) {
    val disclaimerText = "Pregnancy is a critical journey. Every step matters. Always consult with your doctor before taking any medicine or changing your routine. We are here to help you track your progress, but your health provider is your primary guide."
    var displayedText by remember { mutableStateOf("") }
    val auth = FirebaseAuth.getInstance()

    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.pink_baby))
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever
    )

    LaunchedEffect(Unit) {
        // Typing animation
        disclaimerText.forEachIndexed { index, _ ->
            displayedText = disclaimerText.substring(0, index + 1)
            delay(30) // Typing speed
        }
        delay(2000) // Hold after typing
        
        // Check login state and navigate.
        // popUpTo(0) clears the ENTIRE back stack (including Splash) so that whichever
        // destination we land on becomes the only entry. This guarantees that pressing
        // system/swipe back from Home exits the app and can never reveal the Splash/Login.
        if (auth.currentUser != null) {
            navController.navigate(Screen.Home.route) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        } else {
            navController.navigate(Screen.Login.route) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Attractive App Icon / Lottie Animation
        LottieAnimation(
            composition = composition,
            progress = { progress },
            modifier = Modifier.size(250.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Hello Mom",
            fontSize = 40.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Animated Disclaimer Text
        Text(
            text = displayedText,
            fontSize = 16.sp,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.secondary,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )
    }
}
