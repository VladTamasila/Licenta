package com.example.mental_healt_chatbot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.mental_healt_chatbot.ui.theme.Mental_Healt_ChatBotTheme
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.rememberLazyListState


data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            Mental_Healt_ChatBotTheme {
                AppRoot()
            }
        }
    }
}

@Composable
fun AppRoot() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val session = remember { SessionManager(context) }

    // Retrofit API care știe să pună Authorization + X-App-Secret via interceptor
    val api = remember { RetrofitClient.api(context) }

    var loggedIn by remember { mutableStateOf(session.isTokenValid()) }

    if (!loggedIn) {
        LoginScreen(
            onLoggedIn = { loggedIn = true },
            session = session,
            api = api
        )
    } else {
        ChatScreen(
            api = api,
            onLogout = {
                session.clear()
                loggedIn = false
            },
            onAuthExpired = {
                session.clear()
                loggedIn = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    api: ApiService,
    onLogout: () -> Unit,
    onAuthExpired: () -> Unit
) {
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var input by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }

    // Pentru auto-scroll la ultimul mesaj
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        if (messages.isEmpty()) {
            messages.add(
                ChatMessage(
                    text = "Hey! I am your AI companion. How do you feel today?",
                    isUser = false
                )
            )
        }
    }

    // Când apare un mesaj nou, du-te la “bottom”
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            // reverseLayout=true, index 0 e ultimul mesaj
            listState.animateScrollToItem(0)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Mental Health Companion", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Supportive chat • not a therapist",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onLogout) { Text("Logout") }
                }
            )
        }
    ) { innerPadding ->

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp)
            ) {
                Spacer(Modifier.height(8.dp))

                // zona mesaje
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    reverseLayout = true
                ) {
                    items(items = messages.asReversed(), key = { it.timestamp }) { msg ->
                        MessageBubble(msg)
                    }
                }

                // loader discret
                if (isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                }

                // input bar (card)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Write a message…") },
                            singleLine = true
                        )

                        Spacer(Modifier.width(10.dp))

                        FilledIconButton(
                            onClick = {
                                val text = input.trim()
                                if (text.isNotEmpty() && !isLoading) {
                                    messages.add(ChatMessage(text = text, isUser = true))
                                    input = ""
                                    isLoading = true

                                    scope.launch {
                                        try {
                                            val response = api.sendMessage(ChatRequest(text))
                                            messages.add(ChatMessage(text = response.reply, isUser = false))
                                        } catch (e: retrofit2.HttpException) {
                                            if (e.code() == 401) {
                                                messages.add(
                                                    ChatMessage(
                                                        text = "Session expired. Please login again.",
                                                        isUser = false
                                                    )
                                                )
                                                onAuthExpired()
                                            } else {
                                                val body = e.response()?.errorBody()?.string()
                                                messages.add(
                                                    ChatMessage(
                                                        text = "Server error: HTTP ${e.code()} ${body ?: ""}",
                                                        isUser = false
                                                    )
                                                )
                                            }
                                        } catch (e: Exception) {
                                            messages.add(
                                                ChatMessage(
                                                    text = "I can't contact server. Please check backend.",
                                                    isUser = false
                                                )
                                            )
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                }
                            },
                            enabled = !isLoading
                        ) {
                            Icon(Icons.Filled.Send, contentDescription = "Send")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(msg: ChatMessage) {
    val isUser = msg.isUser
    val align = if (isUser) Alignment.CenterEnd else Alignment.CenterStart

    val bubbleColor =
        if (isUser) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant

    val textColor =
        if (isUser) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant

    // user bubble mai “ascuțită” într-un colț, bot bubble invers (arată mai chat-like)
    val shape = if (isUser) {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 6.dp)
    } else {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 6.dp, bottomEnd = 18.dp)
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = align
    ) {
        Surface(
            color = bubbleColor,
            shape = shape,
            tonalElevation = 1.dp,
            shadowElevation = 1.dp
        ) {
            Text(
                text = msg.text,
                color = textColor,
                modifier = Modifier
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .widthIn(max = 320.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewApp() {
    Mental_Healt_ChatBotTheme {
        AppRoot()
    }
}
