package com.example.mental_healt_chatbot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity

const val PIN_LENGTH = 4

// modul ecranului de PIN
enum class PinMode {
    VERIFY,           // doar introduce PIN-ul existent (la deblocare)
    SET,              // primul pas la setare/schimbare PIN: introdu unul nou
    CONFIRM_NEW       // pasul 2: confirma PIN-ul nou (intern, nu se foloseste din afara)
}

@Composable
fun PinScreen(
    mode: PinMode,
    pinManager: PinManager,
    activity: FragmentActivity? = null,
    title: String? = null,
    subtitle: String? = null,
    onSuccess: (String) -> Unit,    // pentru SET primesti PIN-ul nou; pentru VERIFY primesti ""
    onForgotPin: (() -> Unit)? = null
) {
    var pin       by remember { mutableStateOf("") }
    var firstPin  by remember { mutableStateOf<String?>(null) } // pentru flow-ul SET → CONFIRM_NEW
    var step      by remember { mutableStateOf(mode) }
    var error     by remember { mutableStateOf<String?>(null) }

    // pe CONFIRM_NEW fortam textul intern (altfel ramane "lipit" cel pasat din afara)
    val titleText = when (step) {
        PinMode.CONFIRM_NEW -> "Confirmă PIN-ul"
        PinMode.SET         -> title ?: "Setează un PIN nou"
        PinMode.VERIFY      -> title ?: "Introdu PIN-ul"
    }
    val subtitleText = when (step) {
        PinMode.CONFIRM_NEW -> "Reintrodu aceleași 4 cifre"
        PinMode.SET         -> subtitle ?: "4 cifre — va fi cerut la fiecare revenire"
        PinMode.VERIFY      -> subtitle ?: "4 cifre pentru a debloca aplicația"
    }

    // cand userul ajunge la 4 cifre, validam in functie de mod
    LaunchedEffect(pin) {
        if (pin.length == PIN_LENGTH) {
            when (step) {
                PinMode.VERIFY -> {
                    if (pinManager.verifyPin(pin)) {
                        onSuccess("")
                    } else {
                        error = "PIN incorect"
                        pin = ""
                    }
                }
                PinMode.SET -> {
                    firstPin = pin
                    pin = ""
                    error = null
                    step = PinMode.CONFIRM_NEW
                }
                PinMode.CONFIRM_NEW -> {
                    if (pin == firstPin) {
                        onSuccess(pin)
                    } else {
                        error = "PIN-urile nu coincid. Încearcă din nou."
                        firstPin = null
                        pin = ""
                        step = PinMode.SET
                    }
                }
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(Modifier.height(24.dp))

            Text("🔒", fontSize = 56.sp)

            Text(
                text = titleText,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = subtitleText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // bulinele care arata cate cifre s-au introdus
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(vertical = 12.dp)
            ) {
                repeat(PIN_LENGTH) { i ->
                    val filled = i < pin.length
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(
                                if (filled) MaterialTheme.colorScheme.primary
                                else        MaterialTheme.colorScheme.surfaceVariant
                            )
                    )
                }
            }

            if (error != null) {
                Text(
                    text = error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(Modifier.weight(1f))

            // numpad
            NumPad(
                onDigit = { d ->
                    if (pin.length < PIN_LENGTH) pin += d
                    error = null
                },
                onBackspace = {
                    if (pin.isNotEmpty()) pin = pin.dropLast(1)
                    error = null
                },
                showBiometric = step == PinMode.VERIFY
                        && pinManager.isBiometricEnabled()
                        && activity != null
                        && BiometricHelper.canUseBiometric(activity),
                onBiometric = {
                    if (activity != null) {
                        BiometricHelper.prompt(
                            activity = activity,
                            onSuccess = { onSuccess("") }
                        )
                    }
                }
            )

            if (step == PinMode.VERIFY && onForgotPin != null) {
                TextButton(onClick = onForgotPin) {
                    Text("Ai uitat PIN-ul?")
                }
            }
        }
    }
}

@Composable
private fun NumPad(
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    showBiometric: Boolean,
    onBiometric: () -> Unit
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9")
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { d -> NumKey(d) { onDigit(d) } }
            }
        }
        // ultimul rand: biometric (sau gol) | 0 | backspace
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (showBiometric) {
                IconKey(Icons.Filled.Fingerprint, "Amprentă", onBiometric)
            } else {
                Spacer(Modifier.size(72.dp))
            }
            NumKey("0") { onDigit("0") }
            IconKey(Icons.Filled.Backspace, "Șterge", onBackspace)
        }
    }
}

@Composable
private fun NumKey(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(72.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun IconKey(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(72.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = desc)
        }
    }
}
