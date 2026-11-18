package com.example.ui.screens.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.UserRole
import com.example.ui.screens.student.defaultTextFieldColors
import com.example.ui.theme.*
import com.example.viewmodel.AuthUiState

@Composable
fun WelcomeScreen(
    onLoginAsStudent: () -> Unit,
    onLoginAsTutor: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SlatyDarkBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.weight(1f))

        Image(
            painter = painterResource(id = R.drawable.slaty_login_logo),
            contentDescription = "Slaty Logo",
            modifier = Modifier
                .width(260.dp)
                .height(80.dp)
        )

        Spacer(modifier = Modifier.height(30.dp))

        Text(
            text = "Welcome to Slaty",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Your unified e-learning platform connecting students and certified tutors for live sessions, CBT tests, and academic mastery.",
            color = Color(0xFFAAAAAA),
            fontSize = 14.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(36.dp))

        Button(
            onClick = onLoginAsStudent,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("welcome_login_student_button"),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SlatyPrimary)
        ) {
            Icon(Icons.Default.School, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Continue as Student",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        OutlinedButton(
            onClick = onLoginAsTutor,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("welcome_login_tutor_button"),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            border = BorderStroke(2.dp, SlatyMint)
        ) {
            Icon(Icons.Default.Person, contentDescription = null, tint = SlatyMint)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Continue as Tutor",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "By continuing, you agree to Slaty's",
                color = Color(0xFF777777),
                fontSize = 12.sp
            )
            Row(modifier = Modifier.padding(top = 2.dp)) {
                Text(
                    text = "Terms of Service",
                    color = SlatyMint,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = " and ",
                    color = Color(0xFF777777),
                    fontSize = 12.sp
                )
                Text(
                    text = "Privacy Policy",
                    color = SlatyMint,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * Modern Jetpack Compose Authentication Screen supporting Login, Sign Up,
 * Password Reset Dialog, Google Sign-in with Credential Manager, and Firebase Auth.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    role: UserRole,
    uiState: AuthUiState = AuthUiState.Idle,
    lockoutRemainingSeconds: Long = 0L,
    onBack: () -> Unit,
    onLogin: (email: String, pass: String, role: UserRole) -> Unit,
    onRegister: (email: String, pass: String, name: String, role: UserRole) -> Unit,
    onGoogleSignIn: (role: UserRole) -> Unit = {},
    onForgotPassword: (email: String, onResult: (Boolean, String) -> Unit) -> Unit = { _, _ -> },
    onQuickDemoLogin: ((email: String, role: UserRole) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var isSignUpMode by remember { mutableStateOf(false) }
    var currentRole by remember { mutableStateOf(role) }

    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    var showForgotDialog by remember { mutableStateOf(false) }
    var resetEmail by remember { mutableStateOf("") }
    var resetFeedbackMessage by remember { mutableStateOf<String?>(null) }
    var isResetSending by remember { mutableStateOf(false) }

    var validationError by remember { mutableStateOf<String?>(null) }

    val focusManager = LocalFocusManager.current
    val isLoading = uiState is AuthUiState.Loading

    // Forgot Password Dialog
    if (showForgotDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isResetSending) {
                    showForgotDialog = false
                    resetFeedbackMessage = null
                }
            },
            containerColor = SlatyDarkSurface,
            title = {
                Text(
                    text = "Reset Password",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Enter your registered email address to receive a secure Firebase password reset link.",
                        color = SlatyTextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    OutlinedTextField(
                        value = resetEmail,
                        onValueChange = { resetEmail = it },
                        label = { Text("Email Address") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = SlatyMint) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_forgot_email_input"),
                        colors = defaultTextFieldColors()
                    )

                    resetFeedbackMessage?.let { msg ->
                        Text(
                            text = msg,
                            color = if (msg.contains("sent", ignoreCase = true)) SlatyMint else SlatyError,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isResetSending = true
                        resetFeedbackMessage = null
                        onForgotPassword(resetEmail) { success, msg ->
                            isResetSending = false
                            resetFeedbackMessage = msg
                            if (success) {
                                // Keep dialog briefly to show success
                            }
                        }
                    },
                    enabled = !isResetSending && resetEmail.isNotBlank(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SlatyPrimary),
                    modifier = Modifier.testTag("auth_send_reset_button")
                ) {
                    if (isResetSending) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Send Link", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showForgotDialog = false
                        resetFeedbackMessage = null
                    },
                    enabled = !isResetSending
                ) {
                    Text("Close", color = SlatyTextSecondary)
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isSignUpMode) "Create Account" else "Sign In",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("auth_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SlatyDarkBackground)
            )
        },
        containerColor = SlatyDarkBackground
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Slaty Logo Branding
            Image(
                painter = painterResource(id = R.drawable.slaty_login_logo),
                contentDescription = "Slaty",
                modifier = Modifier
                    .width(180.dp)
                    .height(52.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Role Switcher Selector Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E1B2C), RoundedCornerShape(14.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Student Tab
                val isStudent = currentRole == UserRole.STUDENT
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isStudent) SlatyPrimary else Color.Transparent)
                        .clickable {
                            currentRole = UserRole.STUDENT
                            validationError = null
                        }
                        .padding(vertical = 10.dp)
                        .testTag("auth_role_student_toggle"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.School,
                            contentDescription = null,
                            tint = if (isStudent) Color.White else SlatyTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Student",
                            color = if (isStudent) Color.White else SlatyTextSecondary,
                            fontSize = 14.sp,
                            fontWeight = if (isStudent) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }

                // Tutor Tab
                val isTutor = currentRole == UserRole.TUTOR
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isTutor) Color(0xFF2E6F5E) else Color.Transparent)
                        .clickable {
                            if (isSignUpMode) {
                                validationError = "Tutor registration is by institutional accreditation only. Only student accounts can register directly. Verified tutors please sign in."
                            } else {
                                currentRole = UserRole.TUTOR
                                validationError = null
                            }
                        }
                        .padding(vertical = 10.dp)
                        .testTag("auth_role_tutor_toggle"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isSignUpMode) Icons.Default.Lock else Icons.Default.Person,
                            contentDescription = null,
                            tint = if (isTutor) SlatyMint else SlatyTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isSignUpMode) "Tutor (Login Only)" else "Tutor",
                            color = if (isTutor) Color.White else SlatyTextSecondary,
                            fontSize = 13.sp,
                            fontWeight = if (isTutor) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Mode Selector Pill (Sign In vs Sign Up)
            TabRow(
                selectedTabIndex = if (isSignUpMode) 1 else 0,
                containerColor = Color.Transparent,
                divider = {},
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[if (isSignUpMode) 1 else 0]),
                        color = SlatyMint,
                        height = 3.dp
                    )
                }
            ) {
                Tab(
                    selected = !isSignUpMode,
                    onClick = {
                        isSignUpMode = false
                        validationError = null
                    },
                    text = {
                        Text(
                            text = "Sign In",
                            color = if (!isSignUpMode) Color.White else SlatyTextSecondary,
                            fontWeight = if (!isSignUpMode) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 15.sp
                        )
                    },
                    modifier = Modifier.testTag("auth_tab_sign_in")
                )
                Tab(
                    selected = isSignUpMode,
                    onClick = {
                        isSignUpMode = true
                        currentRole = UserRole.STUDENT
                        validationError = null
                    },
                    text = {
                        Text(
                            text = "Create Account (Students)",
                            color = if (isSignUpMode) Color.White else SlatyTextSecondary,
                            fontWeight = if (isSignUpMode) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp
                        )
                    },
                    modifier = Modifier.testTag("auth_tab_sign_up")
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Informative Banner for Student Registration Only
            if (isSignUpMode) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF14242B),
                    border = BorderStroke(1.dp, SlatyMint.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = SlatyMint,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Only students can register an account on Slaty. Verified tutor accounts are provisioned via academic accreditation.",
                            color = Color(0xFFD4EBE6),
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Active Lockout Countdown Banner
            AnimatedVisibility(
                visible = lockoutRemainingSeconds > 0 && !isSignUpMode,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                val lockoutMinutes = lockoutRemainingSeconds / 60
                val lockoutSecs = lockoutRemainingSeconds % 60
                val timeFormatted = String.format("%02d:%02d", lockoutMinutes, lockoutSecs)

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0x33FFA000),
                    border = BorderStroke(1.5.dp, Color(0xFFFFA000)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                        .testTag("auth_lockout_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Lockout Countdown",
                            tint = Color(0xFFFFA000),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Security Lockout Active",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Too many failed password attempts. Password retry available in $timeFormatted.",
                                color = Color(0xFFFFECB3),
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            // Error / Notice Feedback Banner (Reactive to Firebase Auth state)
            val errorMessage = validationError ?: (uiState as? AuthUiState.Error)?.message
            AnimatedVisibility(
                visible = errorMessage != null && (lockoutRemainingSeconds <= 0 || isSignUpMode),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                errorMessage?.let { msg ->
                    val isDuplicateEmail = msg.contains("already exists", ignoreCase = true) || msg.contains("already in use", ignoreCase = true)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0x33FF5252),
                        border = BorderStroke(1.5.dp, SlatyError),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp)
                            .testTag("auth_error_notice")
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Alert",
                                    tint = SlatyError,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = if (isDuplicateEmail) "Email Address Notice" else "Notice",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = msg,
                                color = Color(0xFFFFCDD2),
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                            if (isDuplicateEmail && isSignUpMode) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        isSignUpMode = false
                                        validationError = null
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = SlatyMint),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("switch_to_signin_from_notice")
                                ) {
                                    Text("Switch to Sign In", color = Color(0xFF0D0A1C), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Sign Up: Full Name
            if (isSignUpMode) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        validationError = null
                    },
                    label = { Text("Full Name") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = SlatyMint) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_name_input"),
                    colors = defaultTextFieldColors()
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Username or Email Address
            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it
                    validationError = null
                },
                label = { Text(if (isSignUpMode) "Email Address" else "Username or Email Address") },
                leadingIcon = {
                    Icon(
                        imageVector = if (isSignUpMode) Icons.Default.Email else Icons.Default.AccountCircle,
                        contentDescription = null,
                        tint = SlatyMint
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (isSignUpMode) KeyboardType.Email else KeyboardType.Text,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("auth_email_input"),
                colors = defaultTextFieldColors()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Password
            val isLockedOut = lockoutRemainingSeconds > 0L && !isSignUpMode
            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    validationError = null
                },
                enabled = !isLockedOut,
                label = { Text("Password") },
                supportingText = {
                    if (isLockedOut) {
                        Text(
                            text = "Password entry temporarily suspended by security policy",
                            color = Color(0xFFFFA000),
                            fontSize = 11.sp
                        )
                    }
                },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = if (isLockedOut) Color.Gray else SlatyMint) },
                trailingIcon = {
                    IconButton(
                        onClick = { passwordVisible = !passwordVisible },
                        enabled = !isLockedOut
                    ) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                            tint = if (isLockedOut) Color.DarkGray else SlatyTextSecondary
                        )
                    }
                },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = if (isSignUpMode) ImeAction.Next else ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) },
                    onDone = { focusManager.clearFocus() }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("auth_password_input"),
                colors = defaultTextFieldColors()
            )

            // Sign Up: Confirm Password
            if (isSignUpMode) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        validationError = null
                    },
                    label = { Text("Confirm Password") },
                    leadingIcon = { Icon(Icons.Default.LockReset, contentDescription = null, tint = SlatyMint) },
                    trailingIcon = {
                        IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                            Icon(
                                imageVector = if (confirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (confirmPasswordVisible) "Hide password" else "Show password",
                                tint = SlatyTextSecondary
                            )
                        }
                    },
                    singleLine = true,
                    visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_confirm_password_input"),
                    colors = defaultTextFieldColors()
                )
            }

            // Forgot Password (in Sign In mode)
            if (!isSignUpMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            resetEmail = email.trim()
                            showForgotDialog = true
                        },
                        modifier = Modifier.testTag("auth_forgot_password_button")
                    ) {
                        Text(
                            text = "Forgot password?",
                            color = SlatyMint,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(16.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Primary Action Button (Sign In / Register)
            val isButtonEnabled = !isLoading && !isLockedOut
            Button(
                onClick = {
                    if (isLockedOut) return@Button
                    focusManager.clearFocus()
                    val trimmedInput = email.trim()
                    val trimmedPass = password.trim()

                    if (trimmedInput.isBlank()) {
                        validationError = if (isSignUpMode) "Please enter your email address." else "Please enter your username or email address."
                        return@Button
                    }
                    if (isSignUpMode && !android.util.Patterns.EMAIL_ADDRESS.matcher(trimmedInput).matches()) {
                        validationError = "Please enter a valid email format (e.g. name@domain.com)."
                        return@Button
                    }
                    if (trimmedPass.length < 6) {
                        validationError = "Password must be at least 6 characters long."
                        return@Button
                    }

                    if (isSignUpMode) {
                        if (currentRole != UserRole.STUDENT) {
                            validationError = "Only students can register an account on Slaty. Verified tutors please sign in."
                            return@Button
                        }
                        val trimmedName = name.trim()
                        if (trimmedName.isBlank()) {
                            validationError = "Please enter your full name."
                            return@Button
                        }
                        if (trimmedPass != confirmPassword.trim()) {
                            validationError = "Passwords do not match."
                            return@Button
                        }
                        onRegister(trimmedInput, trimmedPass, trimmedName, UserRole.STUDENT)
                    } else {
                        onLogin(trimmedInput, trimmedPass, currentRole)
                    }
                },
                enabled = isButtonEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("auth_submit_login_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isLockedOut) Color(0xFF3B3550) else if (currentRole == UserRole.TUTOR) Color(0xFF21806A) else SlatyPrimary,
                    disabledContainerColor = if (isLockedOut) Color(0xFF2E2940) else SlatyPrimary.copy(alpha = 0.5f),
                    disabledContentColor = Color.LightGray
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.5.dp
                    )
                } else if (isLockedOut) {
                    val mins = lockoutRemainingSeconds / 60
                    val secs = lockoutRemainingSeconds % 60
                    Text(
                        text = "Locked (Wait ${String.format("%02d:%02d", mins, secs)})",
                        color = Color(0xFFFFCC80),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                } else {
                    Text(
                        text = if (isSignUpMode) "Create ${currentRole.displayName} Account" else "Sign In as ${currentRole.displayName}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * Backward compatibility overload for existing call sites.
 */
@Composable
fun LoginScreen(
    role: UserRole,
    onBack: () -> Unit,
    onLoginSuccess: (email: String, role: UserRole) -> Unit,
    onRegisterSuccess: (email: String, pass: String, name: String, role: UserRole) -> Unit,
    modifier: Modifier = Modifier
) {
    LoginScreen(
        role = role,
        uiState = AuthUiState.Idle,
        onBack = onBack,
        onLogin = { email, _, selectedRole -> onLoginSuccess(email, selectedRole) },
        onRegister = { email, pass, name, selectedRole -> onRegisterSuccess(email, pass, name, selectedRole) },
        onQuickDemoLogin = { email, selectedRole -> onLoginSuccess(email, selectedRole) },
        modifier = modifier
    )
}
