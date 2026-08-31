package com.example.mental_healt_chatbot

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class ChatMessage(
    val id: String,
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    api: ApiService,
    conversationId: String,  // poate fi "new" pentru conversatie noua
    onBack: () -> Unit,
    onAuthExpired: () -> Unit,
    onOpenTrustedContact: () -> Unit = {}
) {
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var input by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var initialLoading by remember { mutableStateOf(true) }
    var privateMode by remember { mutableStateOf(false) }
    // daca backend-ul a refuzat conversatia pentru ca lipseste contactul de incredere,
    // afisam un banner si dezactivam input-ul pana il seteaza
    var needsTrustedContact by remember { mutableStateOf(false) }

    // ID-ul real al conversatiei din DB - null daca e "new" si nu s-a trimis inca niciun mesaj
    // asa nu cream o conversatie goala in DB daca userul intra si iese fara sa scrie nimic
    var realConversationId by remember {
        mutableStateOf(if (conversationId == "new") null else conversationId)
    }

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // incarca mesajele la deschidere - daca e "new" nu face niciun request catre DB
    LaunchedEffect(conversationId) {
        if (conversationId == "new") {
            messages.add(
                ChatMessage(
                    id = "welcome",
                    text = "Salut! Sunt companionul tău AI. Cum te simți astăzi?",
                    isUser = false
                )
            )
            initialLoading = false
            return@LaunchedEffect
        }

        // conversatie existenta din istoric - incarca mesajele din DB
        try {
            val existingMessages = api.getMessages(conversationId)
            messages.clear()

            if (existingMessages.isEmpty()) {
                messages.add(
                    ChatMessage(
                        id = "welcome",
                        text = "Salut! Sunt companionul tău AI. Cum te simți astăzi?",
                        isUser = false
                    )
                )
            } else {
                existingMessages.forEach { msg ->
                    messages.add(
                        ChatMessage(
                            id = msg.id,
                            text = msg.content,
                            isUser = msg.role == "user"
                        )
                    )
                }
            }
        } catch (e: retrofit2.HttpException) {
            if (e.code() == 401) onAuthExpired()
        } catch (e: Exception) {
            messages.add(
                ChatMessage(
                    id = "error",
                    text = "Nu s-au putut încărca mesajele anterioare.",
                    isUser = false
                )
            )
        } finally {
            initialLoading = false
        }
    }

    // scroll la ultimul mesaj
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            delay(50)
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    val topBarColor = if (privateMode)
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
    else
        MaterialTheme.colorScheme.surface

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = topBarColor
                ),
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Mind Buddy",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            if (privateMode)
                                "Mod privat • conversația nu se salvează"
                            else
                                "Chat de suport • nu înlocuiește un specialist",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (privateMode)
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Înapoi")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            privateMode = !privateMode
                            val notifText = if (!privateMode)
                                "🔓 Mod normal activat. Conversația se salvează."
                            else
                                "🔒 Mod privat activat. Mesajele nu se vor salva."
                            messages.add(
                                ChatMessage(
                                    id = "system_${System.currentTimeMillis()}",
                                    text = notifText,
                                    isUser = false
                                )
                            )
                        }
                    ) {
                        Icon(
                            imageVector = if (privateMode) Icons.Filled.Lock else Icons.Filled.LockOpen,
                            contentDescription = if (privateMode) "Mod privat activ" else "Mod normal activ",
                            tint = if (privateMode)
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .imePadding()
        ) {
            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    if (initialLoading) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(items = messages, key = { it.id }) { msg ->
                                MessageBubble(msg)
                            }
                        }
                    }

                    if (isLoading) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                    }

                    // banner de blocare cand nu exista contact de incredere setat
                    if (needsTrustedContact) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.large
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    "Contact de încredere lipsă",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    "Pentru siguranța ta, trebuie să desemnezi o persoană de încredere (părinte, prieten apropiat, terapeut) înainte să poți folosi chat-ul.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(Modifier.height(8.dp))
                                Button(
                                    onClick = onOpenTrustedContact,
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Setează contactul de încredere") }
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.extraLarge,
                        tonalElevation = 2.dp,
                        shadowElevation = 2.dp,
                        color = MaterialTheme.colorScheme.surface
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
                                placeholder = {
                                    Text(if (privateMode) "Mesaj privat…" else "Scrie un mesaj…")
                                },
                                singleLine = true,
                                shape = MaterialTheme.shapes.large,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = if (privateMode)
                                        MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                                    else
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                                )
                            )

                            Spacer(Modifier.width(10.dp))

                            FilledIconButton(
                                onClick = {
                                    val text = input.trim()
                                    if (text.isNotEmpty() && !isLoading) {
                                        val userMsgId = "user_${System.currentTimeMillis()}"
                                        messages.add(ChatMessage(id = userMsgId, text = text, isUser = true))
                                        input = ""
                                        isLoading = true

                                        scope.launch {
                                            try {
                                                if (privateMode) {
                                                    // mod privat - trimitem tot istoricul la fiecare request
                                                    // DeepSeek are context complet fara sa salvam nimic in DB
                                                    val history = messages
                                                        .filter {
                                                            !it.id.startsWith("system_") &&
                                                                    !it.id.startsWith("error") &&
                                                                    it.id != "welcome"
                                                        }
                                                        .dropLast(1) // mesajul curent e trimis separat
                                                        .map { msg ->
                                                            PrivateChatMessage(
                                                                role = if (msg.isUser) "user" else "assistant",
                                                                content = msg.text
                                                            )
                                                        }

                                                    val response = api.sendPrivateMessage(
                                                        body = PrivateChatRequest(
                                                            message = text,
                                                            history = history
                                                        )
                                                    )
                                                    messages.add(
                                                        ChatMessage(
                                                            id = "assistant_${System.currentTimeMillis()}",
                                                            text = response.reply,
                                                            isUser = false
                                                        )
                                                    )
                                                } else {
                                                    // mod normal - cream conversatia in DB la primul mesaj
                                                    // daca nu exista deja (conversationId era "new")
                                                    if (realConversationId == null) {
                                                        try {
                                                            val conv = api.createConversation()
                                                            realConversationId = conv.id
                                                        } catch (e: retrofit2.HttpException) {
                                                            if (e.code() == 401) {
                                                                onAuthExpired()
                                                                return@launch
                                                            }
                                                            messages.add(
                                                                ChatMessage(
                                                                    id = "error_${System.currentTimeMillis()}",
                                                                    text = "Nu s-a putut crea conversația. Încearcă din nou.",
                                                                    isUser = false
                                                                )
                                                            )
                                                            isLoading = false
                                                            return@launch
                                                        }
                                                    }

                                                    val response = api.sendMessage(
                                                        conversationId = realConversationId!!,
                                                        body = SendMessageRequest(message = text)
                                                    )
                                                    messages.add(
                                                        ChatMessage(
                                                            id = "assistant_${System.currentTimeMillis()}",
                                                            text = response.reply,
                                                            isUser = false
                                                        )
                                                    )
                                                    // sincronizam SafetyState - poate s-a schimbat starea (ok -> concerning -> crisis)
                                                    response.safety?.let { SafetyState.apply(it) }
                                                }
                                            } catch (e: retrofit2.HttpException) {
                                                if (e.code() == 401) {
                                                    messages.add(
                                                        ChatMessage(
                                                            id = "error_${System.currentTimeMillis()}",
                                                            text = "Sesiunea a expirat. Te rog autentifică-te din nou.",
                                                            isUser = false
                                                        )
                                                    )
                                                    onAuthExpired()
                                                } else if (e.code() == 423) {
                                                    // criza - serverul a blocat chat-ul; treci pe ecranul de criza
                                                    SafetyState.state.value = "crisis"
                                                    onBack() // iesim din chat - AppRoot va vedea crisis si afiseaza CrisisLockedScreen
                                                } else if (e.code() == 428) {
                                                    // lipsa contact de incredere - blocheaza input-ul si afiseaza banner
                                                    needsTrustedContact = true
                                                    // mesajul user-ului tocmai trimis ramane in lista, dar marcheaza ce s-a intamplat
                                                    messages.add(
                                                        ChatMessage(
                                                            id = "system_${System.currentTimeMillis()}",
                                                            text = "⚠ Trebuie să adaugi un contact de încredere înainte să poți conversa. Apasă butonul de mai jos.",
                                                            isUser = false
                                                        )
                                                    )
                                                } else {
                                                    val body = e.response()?.errorBody()?.string()
                                                    messages.add(
                                                        ChatMessage(
                                                            id = "error_${System.currentTimeMillis()}",
                                                            text = "Eroare server: HTTP ${e.code()} ${body ?: ""}",
                                                            isUser = false
                                                        )
                                                    )
                                                }
                                            } catch (e: Exception) {
                                                messages.add(
                                                    ChatMessage(
                                                        id = "error_${System.currentTimeMillis()}",
                                                        text = "Nu pot contacta serverul. Verifică conexiunea.",
                                                        isUser = false
                                                    )
                                                )
                                            } finally {
                                                isLoading = false
                                            }
                                        }
                                    }
                                },
                                enabled = !isLoading && !initialLoading && !needsTrustedContact,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = if (privateMode)
                                        MaterialTheme.colorScheme.error
                                    else
                                        MaterialTheme.colorScheme.primary
                                ),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Filled.Send, contentDescription = "Trimite")
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
fun MessageBubble(msg: ChatMessage) {
    val isUser = msg.isUser
    val isSystem = msg.id.startsWith("system_")
    val align = when {
        isSystem -> Alignment.Center
        isUser -> Alignment.CenterEnd
        else -> Alignment.CenterStart
    }

    val bubbleColor = when {
        isSystem -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
        isUser -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
    }

    val textColor = when {
        isSystem -> MaterialTheme.colorScheme.onTertiaryContainer
        isUser -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    val shape = when {
        isSystem -> RoundedCornerShape(12.dp)
        isUser -> RoundedCornerShape(
            topStart = 18.dp, topEnd = 18.dp,
            bottomStart = 18.dp, bottomEnd = 6.dp
        )
        else -> RoundedCornerShape(
            topStart = 18.dp, topEnd = 18.dp,
            bottomStart = 6.dp, bottomEnd = 18.dp
        )
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
                    .widthIn(max = if (isSystem) 300.dp else 340.dp),
                style = if (isSystem)
                    MaterialTheme.typography.labelMedium
                else
                    MaterialTheme.typography.bodyMedium
            )
        }
    }
}