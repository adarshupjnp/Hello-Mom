package com.adarsh.hellomom.presentation.auth

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.adarsh.hellomom.presentation.permission.PermissionGate
import kotlinx.coroutines.flow.collectLatest
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

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
    
    val context = LocalContext.current
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result.values.any { it }
        viewModel.sendIntent(RegisterIntent.OnLocationPermissionResult(granted))
    }

    LaunchedEffect(key1 = true) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is RegisterEffect.RequestLocation -> {
                    val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    
                    if (hasFine || hasCoarse) {
                        viewModel.sendIntent(RegisterIntent.OnLocationPermissionResult(true))
                    } else {
                        locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    }
                }
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
                    snackbarHostState.showSnackbar(
                        message = effect.message,
                        duration = SnackbarDuration.Short
                    )
                }
                is RegisterEffect.ShowSuccess -> {
                    snackbarHostState.showSnackbar(
                        message = effect.message,
                        duration = SnackbarDuration.Long,
                        withDismissAction = true
                    )
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { 
                SnackbarHost(snackbarHostState) { data ->
                    val isSuccess = state.showSuccessAnimation
                    Snackbar(
                        snackbarData = data,
                        containerColor = if (isSuccess) Color(0xFF4CAF50) else MaterialTheme.colorScheme.errorContainer,
                        contentColor = if (isSuccess) Color.White else MaterialTheme.colorScheme.onErrorContainer,
                        shape = MaterialTheme.shapes.medium
                    )
                } 
            },
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

                Spacer(modifier = Modifier.height(16.dp))

                if (!state.isOwnerCandidate) {
                    var expanded by remember { mutableStateOf(false) }
                    val roles = listOf("Husband", "Brother", "Sister", "Caretaker", "Other")

                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = state.familyRole,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Relationship to Owner") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                        )

                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            roles.forEach { role ->
                                DropdownMenuItem(
                                    text = { Text(role) },
                                    onClick = {
                                        viewModel.sendIntent(RegisterIntent.OnFamilyRoleChanged(role))
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                    
                    if (state.familyRole.isBlank()) {
                        Text(
                            text = "Please select your relationship",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 16.dp, top = 4.dp).align(Alignment.Start)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

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

        // ---- MODERN SUCCESS ANIMATION OVERLAY ----
        if (state.showSuccessAnimation) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                AnimatedVisibility(
                    visible = true,
                    enter = scaleIn(animationSpec = tween(500)) + fadeIn()
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            modifier = Modifier.size(120.dp),
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = Color(0xFF4CAF50),
                            shadowElevation = 8.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(80.dp),
                                    tint = Color.White
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Text(
                            text = "Registration Successful!",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
