package com.example.mental_healt_chatbot

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { RemindersPrefs(context) }

    var selected by remember { mutableStateOf(prefs.getFrequency()) }
    var info by remember { mutableStateOf<String?>(null) }

    // pe Android 13+ trebuie cerut runtime POST_NOTIFICATIONS
    var hasNotifPermission by remember { mutableStateOf(checkNotifPermission(context)) }
    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifPermission = granted
        if (!granted) info = "Fără permisiune nu putem trimite notificări."
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Reminders") },
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
                "Primești mesaje scurte de check-in doar între 08:00 și 22:00. Mesajele variază — nu sunt aceleași în fiecare zi.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // pe Android 13+ daca nu avem permisiune, aratam un buton de cerere
            if (!hasNotifPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Permisiune necesară",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Pentru a primi reminders trebuie să permiți notificările.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Permite notificările") }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(8.dp)) {
                    ReminderFrequency.values().forEach { freq ->
                        FrequencyRow(
                            label = freq.label,
                            selected = selected == freq,
                            onClick = {
                                selected = freq
                                prefs.setFrequency(freq)
                                RemindersScheduler.apply(context, freq)
                                info = if (freq == ReminderFrequency.OFF)
                                    "Reminder-ele au fost dezactivate."
                                else
                                    "Salvat. Vei primi notificări ${freq.label.lowercase()}."
                            }
                        )
                    }
                }
            }

            // doar pentru testare - trimite o notificare imediat ca sa vada ca merge
            OutlinedButton(
                onClick = {
                    if (!hasNotifPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        return@OutlinedButton
                    }
                    sendTestNotification(context)
                    info = "Notificare de test trimisă."
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = MaterialTheme.shapes.large
            ) { Text("Trimite o notificare de test") }

            if (info != null) {
                Text(info!!,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun FrequencyRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun checkNotifPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(
        context, Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED
}

// trimite o notificare exact ca worker-ul, dar pe loc - ca user-ul sa vada ca merge
private fun sendTestNotification(context: Context) {
    val prefs = RemindersPrefs(context)
    val (idx, msg) = ReminderMessages.pickMessage(prefs.getLastMessageIndex())
    prefs.setLastMessageIndex(idx)

    // construim aceeasi notificare ca worker-ul - reutilizam canalul si ID-ul
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
        nm.getNotificationChannel(ReminderWorker.CHANNEL_ID) == null) {
        val ch = android.app.NotificationChannel(
            ReminderWorker.CHANNEL_ID,
            "Reminders MindBuddy",
            android.app.NotificationManager.IMPORTANCE_DEFAULT
        )
        nm.createNotificationChannel(ch)
    }

    val intent = android.content.Intent(context, MainActivity::class.java).apply {
        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val pi = android.app.PendingIntent.getActivity(
        context, 0, intent,
        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
    )

    val notif = androidx.core.app.NotificationCompat.Builder(context, ReminderWorker.CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(ReminderMessages.pickTitle())
        .setContentText(msg)
        .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(msg))
        .setAutoCancel(true)
        .setContentIntent(pi)
        .build()

    androidx.core.app.NotificationManagerCompat.from(context)
        .notify(ReminderWorker.NOTIF_ID, notif)
}
