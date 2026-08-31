package com.example.mental_healt_chatbot

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.navigation.compose.*
import com.example.mental_healt_chatbot.ui.theme.Mental_Healt_ChatBotTheme

// MainActivity extinde FragmentActivity ca sa putem folosi BiometricPrompt
class MainActivity : FragmentActivity() {

    // observator de proces - cand toata aplicatia intra in background marcam ca trebuie reblocata
    private val processObserver = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) {
            // app a iesit din foreground -> la revenire cerem PIN
            AppLockState.lock()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // la cold start (proces nou) cerem PIN daca e setat - altfel oricine cu telefonul poate intra
        if (PinManager(this).isPinSet()) {
            AppLockState.lock()
        }

        // re-aplicam reminderele daca userul a setat o frecventa anterior - WorkManager persista,
        // dar daca user-a fortat stop la app sau reinstalat, e mai sigur sa reinregistram
        val freq = RemindersPrefs(this).getFrequency()
        if (freq != ReminderFrequency.OFF) {
            RemindersScheduler.apply(this, freq)
        }

        ProcessLifecycleOwner.get().lifecycle.addObserver(processObserver)

        setContent {
            Mental_Healt_ChatBotTheme {
                AppRoot(activity = this)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ProcessLifecycleOwner.get().lifecycle.removeObserver(processObserver)
    }
}

@Composable
fun AppRoot(activity: FragmentActivity) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val session = remember { SessionManager(context) }
    val pinManager = remember { PinManager(context) }
    val api = remember { RetrofitClient.api(context) }

    var loggedIn by remember { mutableStateOf(session.isTokenValid()) }

    if (!loggedIn) {
        LoginScreen(
            onLoggedIn = {
                AppLockState.unlock()
                loggedIn = true
            },
            session = session,
            api = api
        )
        return
    }

    // stage-ul se calculeaza direct din state - asa orice schimbare a needsUnlock,
    // pinSet sau resetMode trigger-eaza recompozitie automat (fara LaunchedEffect)
    val locked by AppLockState.needsUnlock
    var resetMode by remember { mutableStateOf(false) }
    var pinSet by remember { mutableStateOf(pinManager.isPinSet()) }
    val safetyState by SafetyState.state

    // la pornire (cand e deja logat si deblocat cu PIN) intreaba backend-ul
    // ce stare safety avem - daca e "crisis" intra pe ecranul blocat
    LaunchedEffect(loggedIn, locked, pinSet) {
        if (loggedIn && !locked && pinSet) {
            try {
                // verifica si device-ul (chiar daca user nou pe acelasi device blocat)
                val device = DeviceManager(context)
                val dc = api.deviceCheck(DeviceCheckRequest(device.deviceId()))
                if (dc.device_locked) {
                    SafetyState.state.value = "crisis"
                    return@LaunchedEffect
                }

                val s = api.getSafetyStatus()
                SafetyState.apply(s)
                // daca este in stare ingrijoratoare, fortam reminders din ora in ora
                // (suprascrie temporar setarea userului - cand revine la ok, restaureaza)
                if (s.forced_hourly) {
                    RemindersScheduler.apply(context, ReminderFrequency.HOURLY)
                }
            } catch (_: Exception) { /* fara conexiune - lasa state-ul implicit */ }
        }
    }

    when {
        // criza are prioritate fata de orice altceva (chiar peste PIN/reset/etc.)
        loggedIn && safetyState == "crisis" -> {
            CrisisLockedScreen(
                api = api,
                onUnlocked = { SafetyState.state.value = "ok" },
                onAuthExpired = {
                    session.clear()
                    AppLockState.lock()
                    loggedIn = false
                }
            )
        }
        resetMode -> {
            PinResetScreen(
                api = api,
                pinManager = pinManager,
                onDone = {
                    // PIN-ul vechi a fost sters local -> reintram pe SET
                    pinSet = false
                    resetMode = false
                },
                onCancel = { resetMode = false },
                onAuthExpired = {
                    session.clear()
                    AppLockState.lock()
                    resetMode = false
                    loggedIn = false
                }
            )
        }
        !pinSet -> {
            PinScreen(
                mode = PinMode.SET,
                pinManager = pinManager,
                activity = activity,
                onSuccess = { newPin ->
                    pinManager.setPin(newPin)
                    pinSet = true
                    AppLockState.unlock()
                }
            )
        }
        locked -> {
            PinScreen(
                mode = PinMode.VERIFY,
                pinManager = pinManager,
                activity = activity,
                onSuccess = { AppLockState.unlock() },
                onForgotPin = { resetMode = true }
            )
        }
        else -> {
            MainNavGraph(
                api = api,
                pinManager = pinManager,
                activity = activity,
                onPinChanged = { pinSet = pinManager.isPinSet() },
                onLogout = {
                    session.clear()
                    AppLockState.lock()
                    loggedIn = false
                },
                onAuthExpired = {
                    session.clear()
                    AppLockState.lock()
                    loggedIn = false
                }
            )
        }
    }
}

@Composable
fun MainNavGraph(
    api: ApiService,
    pinManager: PinManager,
    activity: FragmentActivity,
    onPinChanged: () -> Unit,
    onLogout: () -> Unit,
    onAuthExpired: () -> Unit
) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "menu") {

        composable("menu") {
            MainMenuScreen(
                onOpenChat = {
                    // creaza conversatia la primul mesaj
                    navController.navigate("chat/new")
                },
                onOpenChart = { navController.navigate("chart") },
                onOpenHistory = { navController.navigate("history") },
                onOpenPinSettings = { navController.navigate("pin-settings") },
                onOpenReminders = { navController.navigate("reminders") },
                onOpenTrustedContact = { navController.navigate("trusted-contact") },
                onLogout = onLogout
            )
        }

        composable("trusted-contact") {
            TrustedContactScreen(
                api = api,
                onBack = { navController.popBackStack() },
                onAuthExpired = onAuthExpired
            )
        }

        composable("reminders") {
            RemindersSettingsScreen(onBack = { navController.popBackStack() })
        }

        composable("history") {
            ConversationHistoryScreen(
                api = api,
                onBack = { navController.popBackStack() },
                onSelectConversation = { conversationId ->
                    navController.navigate("chat/$conversationId")
                },
                onAuthExpired = onAuthExpired
            )
        }

        composable("chat/{conversationId}") { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getString("conversationId") ?: return@composable
            ChatScreen(
                api = api,
                conversationId = conversationId,
                onBack = { navController.popBackStack() },
                onAuthExpired = onAuthExpired,
                onOpenTrustedContact = {
                    navController.navigate("trusted-contact") {
                        popUpTo("menu") // sa nu ramana in stiva chat-ul blocat
                    }
                }
            )
        }

        composable("chart") {
            EmotionChartScreen(
                api = api,
                onAuthExpired = onAuthExpired,
                onBack = { navController.popBackStack() }
            )
        }

        composable("pin-settings") {
            PinSettingsScreen(
                pinManager = pinManager,
                activity = activity,
                onPinChanged = onPinChanged,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
