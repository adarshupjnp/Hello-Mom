package com.adarsh.hellomom.presentation.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.adarsh.hellomom.R
import com.adarsh.hellomom.navigation.Screen
import com.adarsh.hellomom.presentation.components.LoadingButton
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    navController: NavController,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    var showForgotPasswordDialog by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(key1 = true) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is LoginEffect.NavigateToHome -> {
                    // Clear the whole auth back stack so back from Home exits the app,
                    // never returning to Login while the user is signed in.
                    navController.navigate(Screen.Home.route) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
                is LoginEffect.NavigateToRegister -> {
                    // launchSingleTop avoids stacking duplicate Register entries when
                    // bouncing between Login and Register.
                    navController.navigate(Screen.Register.route) {
                        launchSingleTop = true
                    }
                }
                is LoginEffect.NavigateToProfileCreation -> {
                    // Onboarding step after sign-up: clear the auth stack so back exits
                    // the app rather than going back to Login.
                    navController.navigate(Screen.ProfileCreation.route) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
                is LoginEffect.NavigateToForgotPassword -> {
                    showForgotPasswordDialog = true
                }
                LoginEffect.LaunchGoogleSignIn -> {
                    val googleIdOption = GetGoogleIdOption.Builder()
                        .setFilterByAuthorizedAccounts(false)
                        .setServerClientId("885566453620-ektgil92t0qskvd8s48eeqvogloounq0.apps.googleusercontent.com")
                        .setAutoSelectEnabled(false)
                        .build()

                    val request = GetCredentialRequest.Builder()
                        .addCredentialOption(googleIdOption)
                        .build()

                    scope.launch {
                        try {
                            val result = credentialManager.getCredential(
                                request = request,
                                context = context
                            )
                            val googleIdTokenCredential = com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.createFrom(result.credential.data)
                            viewModel.sendIntent(LoginIntent.OnGoogleSignInSuccess(googleIdTokenCredential.idToken))
                        } catch (e: GetCredentialException) {
                            viewModel.sendIntent(LoginIntent.OnGoogleSignInError(e.message ?: "Google Sign-In failed"))
                        } catch (e: Exception) {
                            viewModel.sendIntent(LoginIntent.OnGoogleSignInError(e.message ?: "An unknown error occurred"))
                        }
                    }
                }
                is LoginEffect.ShowError -> {
                    snackbarHostState.showSnackbar(effect.message)
                }
                is LoginEffect.ShowMessage -> {
                    snackbarHostState.showSnackbar(effect.message)
                }
            }
        }
    }

    if (showForgotPasswordDialog) {
        var email by remember { mutableStateOf(state.email) }
        AlertDialog(
            onDismissRequest = { showForgotPasswordDialog = false },
            title = { Text("Reset Password") },
            text = {
                Column {
                    Text("Enter your email address to receive a password reset link.")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.sendIntent(LoginIntent.OnResetPassword(email))
                    showForgotPasswordDialog = false
                }) {
                    Text("Send Link")
                }
            },
            dismissButton = {
                TextButton(onClick = { showForgotPasswordDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (state.requiresWhatsAppNumber) {
        var whatsAppNumber by remember { mutableStateOf("") }
        val isValid = state.isWhatsAppNumberValid(whatsAppNumber)
        AlertDialog(
            // Mandatory: cannot be dismissed by tapping outside or pressing back.
            onDismissRequest = { },
            properties = androidx.compose.ui.window.DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            ),
            icon = { Icon(Icons.Default.Phone, contentDescription = null) },
            title = { Text("Add your WhatsApp number") },
            text = {
                Column {
                    Text(
                        "Enter the mobile number linked to your WhatsApp. " +
                            "Your family uses it to send invites and stay in sync, " +
                            "so please make sure it's an active WhatsApp number."
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = whatsAppNumber,
                        onValueChange = {
                            whatsAppNumber = it.filter { ch -> ch.isDigit() }
                                .take(LoginState.WHATSAPP_NUMBER_LENGTH)
                        },
                        label = { Text("WhatsApp Number") },
                        placeholder = { Text("10-digit mobile number") },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        isError = whatsAppNumber.isNotEmpty() && !isValid,
                        supportingText = if (whatsAppNumber.isNotEmpty() && !isValid) {
                            { Text("Enter a valid ${LoginState.WHATSAPP_NUMBER_LENGTH}-digit number") }
                        } else null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = isValid && !state.isLoading,
                    onClick = { viewModel.sendIntent(LoginIntent.OnWhatsAppNumberSubmitted(whatsAppNumber)) }
                ) {
                    Text("Continue")
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Hello Mom!",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Your caring pregnancy assistant",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.secondary
            )

            Spacer(modifier = Modifier.height(48.dp))

            val showEmailError = state.email.isNotBlank() && !state.isEmailValid
            OutlinedTextField(
                value = state.email,
                onValueChange = { viewModel.sendIntent(LoginIntent.OnEmailChanged(it)) },
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

            OutlinedTextField(
                value = state.password,
                onValueChange = { viewModel.sendIntent(LoginIntent.OnPasswordChanged(it)) },
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
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = { viewModel.sendIntent(LoginIntent.OnForgotPasswordClicked) },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Forgot Password?")
            }

            Spacer(modifier = Modifier.height(24.dp))

            LoadingButton(
                text = "Login",
                isLoading = state.isLoading,
                enabled = state.isLoginEnabled,
                onClick = { viewModel.sendIntent(LoginIntent.OnLoginClicked) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = { viewModel.sendIntent(LoginIntent.OnGoogleSignInClicked) },
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_google),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text("Sign in with Google")
            }

            Spacer(modifier = Modifier.height(32.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Don't have an account?")
                TextButton(onClick = { viewModel.sendIntent(LoginIntent.OnRegisterClicked) }) {
                    Text("Register", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = "Powered by Adarsh Dwivedi",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}
