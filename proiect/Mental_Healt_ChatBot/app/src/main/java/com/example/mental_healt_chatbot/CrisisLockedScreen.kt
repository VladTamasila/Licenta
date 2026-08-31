package com.example.mental_healt_chatbot

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONObject
import retrofit2.HttpException

// ecranul de criza - peste tot, nu se poate iesi din el cu butoane normale
// 24h cooldown obligatoriu - apoi PHQ-2 / contact de incredere
@Composable
fun CrisisLockedScreen(
    api: ApiService,
    onUnlocked: () -> Unit,
    onAuthExpired: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var step by remember { mutableStateOf(CrisisStep.MAIN) }
    var hoursLeft by remember { mutableStateOf<Int?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun parseError(e: Exception): String = when (e) {
        is HttpException -> {
            val body = e.response()?.errorBody()?.string()
            when {
                body.isNullOrBlank() -> e.message()
                body.trim().startsWith("{") -> {
                    try {
                        val o = JSONObject(body)
                        if (o.has("hours_left")) hoursLeft = o.optInt("hours_left")
                        o.optString("error", body)
                    } catch (_: Exception) { body }
                }
                else -> body
            }
        }
        else -> e.message ?: "Eroare necunoscută"
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("⚠", style = MaterialTheme.typography.displayMedium)
            Text(
                "Conversațiile sunt suspendate temporar",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                "Aplicația a observat că treci printr-o perioadă grea. " +
                "Te rugăm să cauți ajutor de la un specialist sau de la o persoană de încredere.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )

            Spacer(Modifier.height(8.dp))

            // numere SOS - tap-ul deschide aplicatia de telefon
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Numere de urgență",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                    SosNumberRow("TelVerde Antisuicid", "0800 801 200") {
                        dial(context, "0800801200")
                    }
                    SosNumberRow("Urgențe", "112") { dial(context, "112") }
                    SosNumberRow("Telefonul Copilului", "116 111") { dial(context, "116111") }
                    SosNumberRow("Depresie & Anxietate", "0374 456 420") {
                        dial(context, "0374456420")
                    }
                }
            }

            when (step) {
                CrisisStep.MAIN -> {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "După 24 de ore de la blocare poți face un test scurt sau cere validare de la persoana ta de încredere.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Button(
                        onClick = { step = CrisisStep.PHQ2 },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = MaterialTheme.shapes.large
                    ) { Text("Fac testul scurt (2 întrebări)") }

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                try {
                                    api.requestCrisisUnlock()
                                    info = "Email trimis către contactul tău de încredere."
                                    error = null
                                } catch (e: Exception) {
                                    if (e is HttpException && e.code() == 401) onAuthExpired()
                                    else error = parseError(e)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = MaterialTheme.shapes.large
                    ) { Text("Trimite cerere către contactul de încredere") }
                }
                CrisisStep.PHQ2 -> {
                    Phq2Form(
                        onSubmit = { q1, q2 ->
                            scope.launch {
                                try {
                                    api.submitPhq2(Phq2Request(q1, q2))
                                    info = "Test trecut. Aplicația a fost deblocată."
                                    onUnlocked()
                                } catch (e: Exception) {
                                    if (e is HttpException && e.code() == 401) onAuthExpired()
                                    else error = parseError(e)
                                }
                            }
                        },
                        onCancel = { step = CrisisStep.MAIN }
                    )
                }
            }

            if (info != null) {
                Text(info!!, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer)
            }
            if (error != null) {
                Text(error!!, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
                if (hoursLeft != null) {
                    Text("Mai sunt ${hoursLeft} ore.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
    }
}

private enum class CrisisStep { MAIN, PHQ2 }

@Composable
private fun SosNumberRow(name: String, number: String, onCall: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Column {
            Text(name, style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold)
            Text(number, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onCall) { Text("Sună") }
    }
}

@Composable
private fun Phq2Form(
    onSubmit: (Int, Int) -> Unit,
    onCancel: () -> Unit
) {
    var q1 by remember { mutableStateOf<Int?>(null) }
    var q2 by remember { mutableStateOf<Int?>(null) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Test scurt", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold)
            Text("Răspunde la cele 2 întrebări gândindu-te la ultimele 2 săptămâni.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            Phq2Question(
                text = "Ai avut puțin interes sau plăcere să faci lucruri?",
                selected = q1,
                onSelect = { q1 = it }
            )
            Phq2Question(
                text = "Te-ai simțit deprimat, demoralizat sau fără speranță?",
                selected = q2,
                onSelect = { q2 = it }
            )

            Button(
                onClick = { if (q1 != null && q2 != null) onSubmit(q1!!, q2!!) },
                enabled = q1 != null && q2 != null,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = MaterialTheme.shapes.large
            ) { Text("Trimite") }

            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth().height(44.dp)
            ) { Text("Înapoi") }
        }
    }
}

@Composable
private fun Phq2Question(
    text: String,
    selected: Int?,
    onSelect: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
        val opts = listOf(
            0 to "Deloc",
            1 to "Câteva zile",
            2 to "Mai mult de jumătate din zile",
            3 to "Aproape în fiecare zi"
        )
        opts.forEach { (v, label) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                RadioButton(selected = selected == v, onClick = { onSelect(v) })
                Text(label, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun dial(context: android.content.Context, number: String) {
    val intent = Intent(Intent.ACTION_DIAL).apply { data = Uri.parse("tel:$number") }
    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
    runCatching { context.startActivity(intent) }
}
