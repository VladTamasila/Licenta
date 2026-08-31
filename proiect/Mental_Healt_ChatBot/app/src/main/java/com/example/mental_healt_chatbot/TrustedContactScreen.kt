package com.example.mental_healt_chatbot

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONObject
import retrofit2.HttpException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrustedContactScreen(
    api: ApiService,
    onBack: () -> Unit,
    onAuthExpired: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var currentContact by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var initialLoading by remember { mutableStateOf(true) }
    var info by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

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

    // la deschiderea ecranului citim contactul curent din backend ca sa-l afisam direct
    LaunchedEffect(Unit) {
        try {
            val s = api.getSafetyStatus()
            SafetyState.apply(s)
            currentContact = s.trusted_contact_email
            if (!currentContact.isNullOrBlank()) email = currentContact!!
        } catch (e: Exception) {
            if (e is HttpException && e.code() == 401) onAuthExpired()
            else error = "Nu am putut citi contactul curent: ${parseError(e)}"
        } finally {
            initialLoading = false
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Contact de încredere") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Înapoi")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Setează un email - părinte, prieten apropiat sau terapeut. " +
                "Dacă ajungi vreodată într-o situație dificilă, persoana asta te poate ajuta să deblochezi aplicația.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // card cu contactul curent - vizibil mereu, chiar si in timpul incarcarii
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        "Contact curent",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = when {
                            initialLoading        -> "Se încarcă…"
                            currentContact.isNullOrBlank() -> "Nu ai niciun contact setat."
                            else                  -> currentContact!!
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(if (currentContact.isNullOrBlank()) "Email persoană de încredere" else "Email nou (înlocuiește)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )

            Button(
                onClick = {
                    loading = true
                    error = null
                    info = null
                    scope.launch {
                        try {
                            api.setTrustedContact(TrustedContactRequest(email.trim()))
                            // re-citim din backend ca sa avem starea reala, nu doar presupusa
                            try {
                                val s = api.getSafetyStatus()
                                SafetyState.apply(s)
                                currentContact = s.trusted_contact_email
                                info = "Contact salvat: ${s.trusted_contact_email ?: email.trim()}."
                            } catch (_: Exception) {
                                // daca status-ul cade, tot setam local ca sa nu fie regresie
                                SafetyState.hasTrustedContact.value = true
                                SafetyState.trustedContactEmail.value = email.trim()
                                currentContact = email.trim()
                                info = "Contact salvat (nu am putut reconfirma cu serverul)."
                            }
                        } catch (e: Exception) {
                            if (e is HttpException && e.code() == 401) onAuthExpired()
                            else error = parseError(e)
                        } finally {
                            loading = false
                        }
                    }
                },
                enabled = !loading && email.contains("@") && email != currentContact,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Text(when {
                    loading                              -> "Se salvează…"
                    currentContact.isNullOrBlank()       -> "Salvează contactul"
                    else                                 -> "Actualizează contactul"
                })
            }

            if (info != null) {
                Text(info!!, color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium)
            }
            if (error != null) {
                Text(error!!, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
