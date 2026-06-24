package com.adarsh.hellomom.presentation.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.adarsh.hellomom.navigation.Screen
import com.adarsh.hellomom.presentation.components.LoadingButton
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    navController: NavController,
    viewModel: RegisterViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(key1 = true) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is RegisterEffect.NavigateToProfileCreation -> {
                    // Clear the entire auth stack so pressing back from ProfileCreation exits the app
                    navController.navigate(Screen.ProfileCreation.route) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
                is RegisterEffect.NavigateToHome -> {
                    // Clear the entire auth stack so pressing back from Home exits the app
                    navController.navigate(Screen.Home.route) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
                is RegisterEffect.NavigateToLogin -> {
                    // Go back to the existing Login entry instead of pushing a new one,
                    // so the back stack never accumulates duplicate Login/Register screens.
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                        launchSingleTop = true
                    }
                }
                is RegisterEffect.ShowError -> {
                    snackbarHostState.showSnackbar(effect.message)
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Create Account") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.sendIntent(RegisterIntent.OnLoginClicked) }) {
                        // Icon could be Back arrow
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val showFullNameError = state.fullName.isNotBlank() && !state.isFullNameValid
            OutlinedTextField(
                value = state.fullName,
                onValueChange = {
                    // Single line, capped length: drop newlines and trim to the max.
                    val sanitized = it.replace("\n", "").take(RegisterState.FULL_NAME_MAX_LENGTH)
                    viewModel.sendIntent(RegisterIntent.OnFullNameChanged(sanitized))
                },
                label = { Text("Full Name") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                isError = showFullNameError,
                supportingText = if (showFullNameError) {
                    { Text("Name must be ${RegisterState.FULL_NAME_MIN_LENGTH}-${RegisterState.FULL_NAME_MAX_LENGTH} characters") }
                } else null,
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            val showEmailError = state.email.isNotBlank() && !state.isEmailValid
            OutlinedTextField(
                value = state.email,
                onValueChange = {
                    viewModel.sendIntent(RegisterIntent.OnEmailChanged(it.replace("\n", "").trim()))
                },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                isError = showEmailError,
                supportingText = if (showEmailError) {
                    { Text("Enter a valid email address") }
                } else null,
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            val showMobileError = state.mobile.isNotBlank() && !state.isMobileValid
            OutlinedTextField(
                value = state.mobile,
                onValueChange = {
                    // Digits only, capped at the expected length.
                    val digits = it.filter { ch -> ch.isDigit() }.take(RegisterState.MOBILE_LENGTH)
                    viewModel.sendIntent(RegisterIntent.OnMobileChanged(digits))
                },
                label = { Text("Mobile Number") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                isError = showMobileError,
                supportingText = if (showMobileError) {
                    { Text("Enter a valid ${RegisterState.MOBILE_LENGTH}-digit mobile number") }
                } else null,
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            val showPasswordError = state.password.isNotEmpty() && !state.isPasswordValid
            OutlinedTextField(
                value = state.password,
                onValueChange = {
                    val sanitized = it.replace("\n", "").take(RegisterState.PASSWORD_MAX_LENGTH)
                    viewModel.sendIntent(RegisterIntent.OnPasswordChanged(sanitized))
                },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password"
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                isError = showPasswordError,
                supportingText = if (showPasswordError) {
                    { Text("Min ${RegisterState.PASSWORD_MIN_LENGTH} chars with upper, lower case and a number") }
                } else null,
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            val showConfirmError = state.confirmPassword.isNotEmpty() && !state.doPasswordsMatch
            OutlinedTextField(
                value = state.confirmPassword,
                onValueChange = {
                    val sanitized = it.replace("\n", "").take(RegisterState.PASSWORD_MAX_LENGTH)
                    viewModel.sendIntent(RegisterIntent.OnConfirmPasswordChanged(sanitized))
                },
                label = { Text("Confirm Password") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                        Icon(
                            imageVector = if (confirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (confirmPasswordVisible) "Hide password" else "Show password"
                        )
                    }
                },
                visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                isError = showConfirmError,
                supportingText = if (showConfirmError) {
                    { Text("Passwords do not match") }
                } else null,
                singleLine = true
            )

            Spacer(modifier = Modifier.height(32.dp))

            LoadingButton(
                text = "Register",
                isLoading = state.isLoading,
                enabled = state.isRegisterEnabled,
                onClick = { viewModel.sendIntent(RegisterIntent.OnRegisterClicked) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Already have an account?",
                    color = Color.White
                )
                TextButton(onClick = { viewModel.sendIntent(RegisterIntent.OnLoginClicked) }) {
                    Text("Login", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
