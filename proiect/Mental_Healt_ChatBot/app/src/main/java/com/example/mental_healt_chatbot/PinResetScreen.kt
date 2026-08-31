package com.example.mental_healt_chatbot

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONObject
import retrofit2.HttpException
import java.net.SocketTimeoutException

// flow de reset PIN: trimite cod pe email -> verifica cod + parola -> sterge PIN-ul vechi local
// dupa asta MainActivity vede ca isPinSet() == false si trece pe ecranul de setare PIN nou
@Composable
fun PinResetScreen(
    api: ApiService,
    pinManager: PinManager,
    onDone: () -> Unit,
    onCancel: () -> Unit,
    onAuthExpired: () -> Unit
) {
    var code     by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading  by remember { mutableStateOf(false) }
    var info     by remember { mutableStateOf<String?>(null) }
    var error    by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    fun parseError(e: Exception): String = when (e) {
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

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "Resetare PIN",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Apasă \"Trimite cod\" - îți va veni un cod de 6 cifre pe email. Apoi introdu codul plus parola contului.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // butonul de cerere cod ramane mereu vizibil; dupa timeout codul tot ajunge pe email
            OutlinedButton(
                onClick = {
                    loading = true
                    error = null
                    info = null
                    scope.launch {
                        try {
                            api.requestPinResetCode()
                            info = "Cod trimis pe email."
                        } catch (e: Exception) {
                            // daca a dat timeout, codul probabil tot a fost trimis -
                            // serverul are gmail+ngrok care raspunde lent
                            if (e is SocketTimeoutException ||
                                (e.message?.contains("timeout", true) == true)) {
                                info = "Răspuns lent. Verifică emailul - codul s-ar putea să fi fost trimis oricum."
                            } else if (e is HttpException && e.code() == 401) {
                                onAuthExpired()
                            } else {
                                error = parseError(e)
                            }
                        } finally {
                            loading = false
                        }
                    }
                },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Text(if (loading) "Se trimite…" else "Trimite cod pe email")
            }

            // formularul e mereu vizibil - daca ai primit codul, il poti baga aici fara sa
            // depinzi de raspunsul serverului (care poate da timeout)
            OutlinedTextField(
                value = code,
                onValueChange = { v ->
                    code = v.filter { c -> c.isDigit() }.take(6)
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Cod din email (6 cifre)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Parola contului") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )

            Button(
                onClick = {
                    loading = true
                    error = null
                    scope.launch {
                        try {
                            api.verifyPinResetCode(
                                PinResetVerifyRequest(code = code.trim(), password = password)
                            )
                            // valid - stergem PIN-ul local si lasam ecranul de setare sa preia
                            pinManager.clearPin()
                            pinManager.setBiometricEnabled(false)
                            onDone()
                        } catch (e: Exception) {
                            if (e is HttpException && e.code() == 401) onAuthExpired()
                            else error = parseError(e)
                        } finally {
                            loading = false
                        }
                    }
                },
                enabled = !loading && code.length == 6 && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Text(if (loading) "Se verifică…" else "Confirmă și Resetează PIN-ul")
            }

            if (info != null) {
                Text(info!!, color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall)
            }
            if (error != null) {
                Text(error!!, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.weight(1f))

            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = MaterialTheme.shapes.large
            ) { Text("Înapoi") }
        }
    }
}
