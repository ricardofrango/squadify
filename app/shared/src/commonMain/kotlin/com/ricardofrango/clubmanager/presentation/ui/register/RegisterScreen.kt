package com.ricardofrango.clubmanager.presentation.ui.register

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ricardofrango.clubmanager.presentation.ui.components.FMTopBar
import com.ricardofrango.clubmanager.presentation.ui.components.PrimaryLoadingButton
import com.ricardofrango.clubmanager.presentation.ui.theme.CarbonGray
import com.ricardofrango.clubmanager.presentation.ui.theme.DeepCarbon
import com.ricardofrango.clubmanager.presentation.ui.theme.PitchGreen
import clubmanager.app.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDashboard: () -> Unit,
    viewModel: RegisterViewModel = koinViewModel()
) {
    val uiState by viewModel.state.collectAsState()

    // Observe Navigation Events
    LaunchedEffect(viewModel.navigationEvent) {
        viewModel.navigationEvent.collect { event ->
            when (event) {
                RegisterNavigationEvent.NavigateBackToLogin -> onNavigateBack()
                RegisterNavigationEvent.NavigateToDashboard -> onNavigateToDashboard()
            }
        }
    }

    // Local UI State for form fields
    var firstName by rememberSaveable { mutableStateOf("") }
    var lastName by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }

    val errorMessage = (uiState as? RegisterUiState.Idle)?.error

    Scaffold(
        topBar = {
            FMTopBar(
                title = stringResource(Res.string.app_name),
                onNavigateBack = onNavigateBack,
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(40.dp))
                
                Text(
                    text = stringResource(Res.string.btn_register),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = CarbonGray
                    ),
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Error Message Handling with Reserved Space
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .padding(bottom = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (errorMessage != null) {
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                OutlinedTextField(
                    value = firstName,
                    onValueChange = { firstName = it },
                    label = { Text(stringResource(Res.string.label_first_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    isError = errorMessage != null,
                    enabled = uiState !is RegisterUiState.Loading,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = DeepCarbon,
                        unfocusedTextColor = DeepCarbon,
                        focusedBorderColor = PitchGreen,
                        unfocusedBorderColor = Color.Gray.copy(alpha = 0.3f),
                        focusedLabelColor = PitchGreen,
                        unfocusedLabelColor = Color.Gray
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = lastName,
                    onValueChange = { lastName = it },
                    label = { Text(stringResource(Res.string.label_last_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    isError = errorMessage != null,
                    enabled = uiState !is RegisterUiState.Loading,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = DeepCarbon,
                        unfocusedTextColor = DeepCarbon,
                        focusedBorderColor = PitchGreen,
                        unfocusedBorderColor = Color.Gray.copy(alpha = 0.3f),
                        focusedLabelColor = PitchGreen,
                        unfocusedLabelColor = Color.Gray
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(Res.string.label_email)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    isError = errorMessage != null,
                    enabled = uiState !is RegisterUiState.Loading,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = DeepCarbon,
                        unfocusedTextColor = DeepCarbon,
                        focusedBorderColor = PitchGreen,
                        unfocusedBorderColor = Color.Gray.copy(alpha = 0.3f),
                        focusedLabelColor = PitchGreen,
                        unfocusedLabelColor = Color.Gray
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text(stringResource(Res.string.label_phone)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    isError = errorMessage != null,
                    enabled = uiState !is RegisterUiState.Loading,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = DeepCarbon,
                        unfocusedTextColor = DeepCarbon,
                        focusedBorderColor = PitchGreen,
                        unfocusedBorderColor = Color.Gray.copy(alpha = 0.3f),
                        focusedLabelColor = PitchGreen,
                        unfocusedLabelColor = Color.Gray
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(Res.string.label_password)) },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    isError = errorMessage != null,
                    enabled = uiState !is RegisterUiState.Loading,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = DeepCarbon,
                        unfocusedTextColor = DeepCarbon,
                        focusedBorderColor = PitchGreen,
                        unfocusedBorderColor = Color.Gray.copy(alpha = 0.3f),
                        focusedLabelColor = PitchGreen,
                        unfocusedLabelColor = Color.Gray
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text(stringResource(Res.string.label_confirm_password)) },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    isError = errorMessage != null,
                    enabled = uiState !is RegisterUiState.Loading,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = DeepCarbon,
                        unfocusedTextColor = DeepCarbon,
                        focusedBorderColor = PitchGreen,
                        unfocusedBorderColor = Color.Gray.copy(alpha = 0.3f),
                        focusedLabelColor = PitchGreen,
                        unfocusedLabelColor = Color.Gray
                    )
                )

                Spacer(modifier = Modifier.height(32.dp))

                PrimaryLoadingButton(
                    text = Res.string.btn_register,
                    submittingText = Res.string.btn_registering,
                    isLoading = uiState is RegisterUiState.Loading,
                    onClick = {
                        viewModel.onRegisterClicked(
                            firstName, lastName, email, phone, password, confirmPassword
                        )
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                TextButton(
                    onClick = onNavigateBack,
                    enabled = uiState !is RegisterUiState.Loading
                ) {
                    Text(
                        text = stringResource(Res.string.msg_already_have_account),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = CarbonGray,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
                
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}
