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
import org.json.JSONObject
import retrofit2.HttpException

enum class AuthMode { LOGIN, REGISTER, FORGOT_PASSWORD }

@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    session: SessionManager,
    api: ApiService
) {
    var mode by remember { mutableStateOf(AuthMode.LOGIN) }

    var identifier by remember { mutableStateOf("") }
    var username   by remember { mutableStateOf("") }
    var email      by remember { mutableStateOf("") }
    var password   by remember { mutableStateOf("") }

    var error       by remember { mutableStateOf<String?>(null) }
    var isSuccess   by remember { mutableStateOf(false) }
    var loading     by remember { mutableStateOf(false) }

    var showResend  by remember { mutableStateOf(false) }
    var resendEmail by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()

    fun switchMode(newMode: AuthMode) {
        mode       = newMode
        error      = null
        isSuccess  = false
        showResend = false
        password   = ""
        when (newMode) {
            AuthMode.LOGIN           -> { username = ""; email = "" }
            AuthMode.REGISTER        -> { identifier = "" }
            AuthMode.FORGOT_PASSWORD -> { username = ""; email = ""; password = "" }
        }
    }

    fun parseError(e: Exception): String {
        return when (e) {
            is HttpException -> {
                val body = e.response()?.errorBody()?.string()
                when {
                    body.isNullOrBlank() -> e.message()
                    body.trim().startsWith("{") -> {
                        try { JSONObject(body).optString("error", body) }
                        catch (_: Exception) { body }
                    }
                    else -> body
                }
            }
            else -> e.message ?: "Eroare necunoscută"
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp),
                shape = MaterialTheme.shapes.extraLarge,
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "🧠 MindBuddy",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = when (mode) {
                            AuthMode.LOGIN           -> "Autentifică-te pentru a continua"
                            AuthMode.REGISTER        -> "Creează un cont nou"
                            AuthMode.FORGOT_PASSWORD -> "Resetează parola"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(4.dp))

                    when (mode) {
                        AuthMode.LOGIN -> {
                            OutlinedTextField(
                                value = identifier,
                                onValueChange = { identifier = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("Nume utilizator sau Email") }
                            )
                        }
                        AuthMode.REGISTER -> {
                            OutlinedTextField(
                                value = username,
                                onValueChange = { username = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("Nume utilizator") }
                            )
                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("Email") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                            )
                        }
                        AuthMode.FORGOT_PASSWORD -> {
                            OutlinedTextField(
                                value = identifier,
                                onValueChange = { identifier = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("Email") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                            )
                        }
                    }

                    if (mode != AuthMode.FORGOT_PASSWORD) {
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Parolă") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                        )
                    }

                    if (error != null) {
                        Text(
                            text = error!!,
                            color = if (isSuccess)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    if (showResend) {
                        OutlinedButton(
                            onClick = {
                                loading = true
                                scope.launch {
                                    try {
                                        api.resendVerification(
                                            ResendVerificationRequest(email = resendEmail)
                                        )
                                        error = "Email de verificare retrimis. Verifică inbox-ul."
                                        isSuccess = true
                                        showResend = false
                                    } catch (e: Exception) {
                                        error = parseError(e)
                                        isSuccess = false
                                    } finally {
                                        loading = false
                                    }
                                }
                            },
                            enabled = !loading,
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Text("📧 Retrimite emailul de verificare")
                        }
                    }

                    Button(
                        onClick = {
                            loading = true
                            error = null
                            isSuccess = false
                            showResend = false

                            scope.launch {
                                try {
                                    when (mode) {
                                        AuthMode.LOGIN -> {
                                            val resp = api.login(
                                                LoginRequest(
                                                    identifier = identifier.trim(),
                                                    password = password
                                                )
                                            )
                                            session.saveToken(resp.token)
                                            onLoggedIn()
                                        }
                                        AuthMode.REGISTER -> {
                                            api.register(
                                                RegisterRequest(
                                                    username = username.trim(),
                                                    email = email.trim(),
                                                    password = password
                                                )
                                            )
                                            resendEmail = email.trim()
                                            error = "Cont creat! Verifică emailul pentru a activa contul."
                                            isSuccess = true
                                            switchMode(AuthMode.LOGIN)
                                            identifier = resendEmail
                                        }
                                        AuthMode.FORGOT_PASSWORD -> {
                                            api.forgotPassword(
                                                ForgotPasswordRequest(email = identifier.trim())
                                            )
                                            error = "Dacă acest email există, un link de resetare a fost trimis."
                                            isSuccess = true
                                            switchMode(AuthMode.LOGIN)
                                        }
                                    }
                                } catch (e: Exception) {
                                    val msg = parseError(e)
                                    error = msg
                                    isSuccess = false

                                    if (msg.contains("verify", ignoreCase = true) ||
                                        msg.contains("verific", ignoreCase = true)) {
                                        resendEmail = identifier.trim()
                                        showResend = true
                                    }
                                } finally {
                                    loading = false
                                }
                            }
                        },
                        enabled = !loading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Text(
                            when {
                                loading && mode == AuthMode.LOGIN           -> "Se autentifică…"
                                loading && mode == AuthMode.REGISTER        -> "Se creează contul…"
                                loading && mode == AuthMode.FORGOT_PASSWORD -> "Se trimite…"
                                mode == AuthMode.LOGIN                      -> "Autentificare"
                                mode == AuthMode.REGISTER                   -> "Înregistrare"
                                mode == AuthMode.FORGOT_PASSWORD            -> "Trimite link de resetare"
                                else                                        -> "Continuă"
                            }
                        )
                    }

                    when (mode) {
                        AuthMode.LOGIN -> {
                            OutlinedButton(
                                onClick = { switchMode(AuthMode.REGISTER) },
                                enabled = !loading,
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = MaterialTheme.shapes.large
                            ) { Text("Creează cont") }

                            TextButton(
                                onClick = { switchMode(AuthMode.FORGOT_PASSWORD) },
                                enabled = !loading
                            ) { Text("Ai uitat parola?") }
                        }
                        AuthMode.REGISTER, AuthMode.FORGOT_PASSWORD -> {
                            OutlinedButton(
                                onClick = { switchMode(AuthMode.LOGIN) },
                                enabled = !loading,
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = MaterialTheme.shapes.large
                            ) { Text("Înapoi la autentificare") }
                        }
                    }

                    Text(
                        text = "Această aplicație oferă suport general și nu înlocuiește un specialist.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
