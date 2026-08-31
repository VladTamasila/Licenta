package com.example.mental_healt_chatbot

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationHistoryScreen(
    api: ApiService,
    onBack: () -> Unit,
    onSelectConversation: (String) -> Unit,
    onAuthExpired: () -> Unit
) {
    var conversations by remember { mutableStateOf<List<ConversationDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember { mutableStateOf<ConversationDto?>(null) }

    val scope = rememberCoroutineScope()
    val ro = Locale("ro", "RO")
    val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", ro)

    fun loadConversations() {
        scope.launch {
            isLoading = true
            error = null
            try {
                conversations = api.getConversations()
            } catch (e: retrofit2.HttpException) {
                if (e.code() == 401) onAuthExpired()
                else error = "Eroare server: ${e.code()}"
            } catch (e: Exception) {
                error = "Nu se poate conecta la server"
            } finally {
                isLoading = false
            }
        }
    }

    fun deleteConversation(conv: ConversationDto) {
        scope.launch {
            try {
                api.deleteConversation(conv.id)
                conversations = conversations.filter { it.id != conv.id }
            } catch (e: Exception) {
                error = "Nu s-a putut șterge conversația"
            }
            showDeleteDialog = null
        }
    }

    LaunchedEffect(Unit) { loadConversations() }

    // Dialog confirmare ștergere
    if (showDeleteDialog != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Șterge conversația?") },
            text = { Text("Această acțiune este ireversibilă. Toate mesajele vor fi șterse.") },
            confirmButton = {
                TextButton(
                    onClick = { showDeleteDialog?.let { deleteConversation(it) } },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Șterge") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) { Text("Anulează") }
            }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Istoric conversații", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Înapoi")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                error != null -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            error!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { loadConversations() }) {
                            Text("Încearcă din nou")
                        }
                    }
                }
                conversations.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Nu ai nicio conversație încă",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Începe o conversație nouă din meniul principal",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(conversations, key = { it.id }) { conv ->
                            ConversationCard(
                                conversation = conv,
                                formatter = formatter,
                                onClick = { onSelectConversation(conv.id) },
                                onDelete = { showDeleteDialog = conv }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationCard(
    conversation: ConversationDto,
    formatter: DateTimeFormatter,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dateText = try {
        val instant = Instant.parse(conversation.updated_at ?: conversation.created_at)
        instant.atZone(ZoneId.systemDefault()).format(formatter)
    } catch (e: Exception) {
        conversation.created_at
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = conversation.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = dateText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Șterge",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
