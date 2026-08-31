package com.example.mental_healt_chatbot

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity

// trei stari posibile in setarile PIN-ului
private enum class SettingsStep { MENU, VERIFY_OLD, SET_NEW }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinSettingsScreen(
    pinManager: PinManager,
    activity: FragmentActivity?,
    onPinChanged: () -> Unit = {},
    onBack: () -> Unit
) {
    var step by remember { mutableStateOf(SettingsStep.MENU) }
    var bioEnabled by remember { mutableStateOf(pinManager.isBiometricEnabled()) }
    var info by remember { mutableStateOf<String?>(null) }

    val canUseBio = activity != null && BiometricHelper.canUseBiometric(activity)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Setări PIN") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (step == SettingsStep.MENU) onBack()
                        else step = SettingsStep.MENU
                    }) {
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
            when (step) {
                SettingsStep.MENU -> {
                    Text(
                        "PIN-ul protejează conversațiile tale. Doar tu îl știi - nu este trimis pe server.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Schimbare PIN",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Vei introduce PIN-ul actual, apoi unul nou.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = { step = SettingsStep.VERIFY_OLD },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Schimbă PIN-ul") }
                        }
                    }

                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Deblocare cu amprenta",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        if (canUseBio)
                                            "Folosește senzorul telefonului ca alternativă la PIN."
                                        else
                                            "Indisponibil pe acest dispozitiv.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = bioEnabled,
                                    enabled = canUseBio,
                                    onCheckedChange = {
                                        bioEnabled = it
                                        pinManager.setBiometricEnabled(it)
                                        info = if (it) "Biometric activat." else "Biometric dezactivat."
                                    }
                                )
                            }
                        }
                    }

                    if (info != null) {
                        Text(
                            info!!,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                SettingsStep.VERIFY_OLD -> {
                    PinScreen(
                        mode = PinMode.VERIFY,
                        pinManager = pinManager,
                        activity = activity,
                        title = "PIN-ul actual",
                        subtitle = "Confirmă-l ca să poți seta unul nou",
                        onSuccess = { step = SettingsStep.SET_NEW }
                    )
                }

                SettingsStep.SET_NEW -> {
                    PinScreen(
                        mode = PinMode.SET,
                        pinManager = pinManager,
                        activity = activity,
                        title = "PIN nou",
                        subtitle = "Va înlocui PIN-ul vechi",
                        onSuccess = { newPin ->
                            pinManager.setPin(newPin)
                            onPinChanged()
                            info = "PIN modificat cu succes."
                            step = SettingsStep.MENU
                        }
                    )
                }
            }
        }
    }
}
