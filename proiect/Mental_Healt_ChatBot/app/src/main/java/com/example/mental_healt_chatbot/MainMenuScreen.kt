package com.example.mental_healt_chatbot

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainMenuScreen(
    onOpenChat: () -> Unit,
    onOpenChart: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenPinSettings: () -> Unit,
    onOpenReminders: () -> Unit,
    onOpenTrustedContact: () -> Unit,
    onLogout: () -> Unit
) {
    // banner-ul de "lipseste contact de incredere" - se uita la starea globala incarcata la startup
    val hasContact by SafetyState.hasTrustedContact
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        topBar = {
            // top bar curat - doar titlu si deconectare
            CenterAlignedTopAppBar(
                title = {
                    Text("Mind Buddy", fontWeight = FontWeight.SemiBold)
                },
                actions = {
                    TextButton(onClick = onLogout) {
                        Text("Deconectare")
                    }
                }
            )
        }
    ) { innerPadding ->
        // structura: butoanele + titlul sunt pinned sus
        // doar cardurile mari (Chat / Grafic) sunt in LazyColumn-ul scrollabil
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // randul de scurtaturi colorate - pinned
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickAction(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.History,
                    label = "Istoric",
                    background = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = onOpenHistory
                )
                QuickAction(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Notifications,
                    label = "Reminders",
                    background = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    onClick = onOpenReminders
                )
                QuickAction(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Lock,
                    label = "PIN",
                    background = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = onOpenPinSettings
                )
                QuickAction(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Favorite,
                    label = "Contact",
                    background = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    onClick = onOpenTrustedContact
                )
            }

            // banner-ul apare DOAR cand backend-ul a raspuns explicit cu false
            // (Boolean? - null = inca nu stie, asteapta; false = confirmat ca lipseste)
            if (hasContact == false) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "Trebuie să adaugi un contact de încredere",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            "Pentru siguranța ta nu poți folosi chat-ul până nu desemnezi o persoană de încredere.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = onOpenTrustedContact,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Setează contactul acum") }
                    }
                }
            }

            // titlu "Alege o optiune" - tot pinned
            Text(
                "Alege o opțiune",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            // doar zona cu cardurile mari scrolleaza - weight(1f) ia tot ce-a mai ramas
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Image(
                                painter = painterResource(id = R.drawable.chat_preview),
                                contentDescription = "Previzualizare chat",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp),
                                contentScale = ContentScale.Crop
                            )
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Conversație nouă",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Vorbește cu companionul tău AI despre stres, emoții, muncă sau gânduri zilnice.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = onOpenChat,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Text("Începe conversație")
                                }
                            }
                        }
                    }
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Image(
                                painter = painterResource(id = R.drawable.chart_preview),
                                contentDescription = "Previzualizare grafic emoții",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp),
                                contentScale = ContentScale.Crop
                            )
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Grafic Emoții",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Vezi tendințele tale emoționale și urmărește cum evoluează starea ta în timp.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OutlinedButton(
                                    onClick = onOpenChart,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Text("Deschide Graficul")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickAction(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    background: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(86.dp),
        shape = RoundedCornerShape(18.dp),
        color = background,
        tonalElevation = 2.dp,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = contentColor
            )
        }
    }
}
