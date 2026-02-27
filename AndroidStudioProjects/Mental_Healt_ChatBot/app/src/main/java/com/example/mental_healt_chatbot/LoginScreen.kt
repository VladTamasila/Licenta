package com.example.mental_healt_chatbot

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import retrofit2.HttpException

@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    session: SessionManager,
    api: ApiService
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Background full screen
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
                shape = MaterialTheme.shapes.extraLarge,
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header
                    Text(
                        text = "🧠 Mental Health Bot",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Autentifică-te pentru a continua",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(4.dp))

                    // Username
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Username") }
                    )

                    // Password (masked)
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )

                    // Error
                    if (error != null) {
                        Text(
                            text = error!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    // Login button
                    Button(
                        onClick = {
                            loading = true
                            error = null
                            scope.launch {
                                try {
                                    val resp = api.login(LoginRequest(username.trim(), password))
                                    session.saveToken(resp.token)
                                    onLoggedIn()
                                } catch (e: Exception) {
                                    error = when (e) {
                                        is HttpException -> {
                                            val body = e.response()?.errorBody()?.string()
                                            "Login failed: HTTP ${e.code()} ${body ?: e.message()}"
                                        }
                                        else -> "Login failed: ${e.javaClass.simpleName}: ${e.message}"
                                    }
                                } finally {
                                    loading = false
                                }
                            }
                        },
                        enabled = !loading,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Text(if (loading) "Se autentifică..." else "Login")
                    }

                    // Register button (secondary)
                    OutlinedButton(
                        onClick = {
                            loading = true
                            error = null
                            scope.launch {
                                try {
                                    api.register(RegisterRequest(username.trim(), password))
                                    val resp = api.login(LoginRequest(username.trim(), password))
                                    session.saveToken(resp.token)
                                    onLoggedIn()
                                } catch (e: Exception) {
                                    error = when (e) {
                                        is HttpException -> {
                                            val body = e.response()?.errorBody()?.string()
                                            "Register failed: HTTP ${e.code()} ${body ?: e.message()}"
                                        }
                                        else -> "Register failed: ${e.javaClass.simpleName}: ${e.message}"
                                    }
                                } finally {
                                    loading = false
                                }
                            }
                        },
                        enabled = !loading,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Text("Register")
                    }

                    // Small footer
                    Text(
                        text = "Note: aplicația oferă suport general, nu înlocuiește un specialist.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
