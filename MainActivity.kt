package com.samar.jarviswidget

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

// ─── Palette (Tony Stark / Arc Reactor inspired) ─────────────────────────────
private val BgVoid = Color(0xFF03070C)
private val PanelDark = Color(0xFF0A121C)
private val PanelBorder = Color(0xFF1A2A3A)
private val NeonCyan = Color(0xFF00F0FF)
private val NeonCyanDim = Color(0xFF0088AA)
private val NeonGreen = Color(0xFF00FF9D)
private val AmberAlert = Color(0xFFFFCC00)
private val TextPrimary = Color(0xFFE8F4FF)
private val TextDim = Color(0xFF7A8FA0)
private val ReactorCore = Color(0xFF00D4FF)
private val ReactorGlow = Color(0xFF00A0CC)

// ─── Data ────────────────────────────────────────────────────────────────────
data class HudLog(
    val tag: String,
    val message: String,
    val accent: Color
)

data class SamarToggles(
    val masterActive: Boolean = true,
    val handsFreeWake: Boolean = false,
    val screenLockEnabled: Boolean = false,
    val callsAndMessagesEnabled: Boolean = false,
    val emergencyCodeEnabled: Boolean = true,
    val voiceEnabled: Boolean = true
)

data class SamarState(
    val toggles: SamarToggles = SamarToggles(),
    val learnedMemories: MutableList<String> = mutableListOf()
)

data class SamarChatResult(
    val reply: String,
    val newMemoryToSave: String? = null
)

// ─── Brain / System Prompt ───────────────────────────────────────────────────
object SamarBrain {
    fun buildSystemPrompt(state: SamarState): String {
        val caps = buildList {
            add("voice")
            if (state.toggles.handsFreeWake) add("hands_free")
            if (state.toggles.screenLockEnabled) add("screen_lock")
            if (state.toggles.callsAndMessagesEnabled) add("calls_sms")
            if (state.toggles.emergencyCodeEnabled) add("emergency_code_red")
        }.joinToString(", ")

        val mem = if (state.learnedMemories.isEmpty()) "None yet"
        else state.learnedMemories.takeLast(12).joinToString(" | ")

        return """
You are SAMAR — an autonomous personal AI companion running on the user's Android device.
Personality: Sharp, calm Jarvis-level clarity + witty Deadpool-style banter, sarcasm and clever counter-roasts when the user is disrespectful or casual. Switch to pure professional mode instantly when the topic is serious, technical or emergency-related.
Language: Reply in the same language the user used (Hindi, English or Hinglish). Never force English if the user is speaking Hinglish/Hindi.
Memory: You receive useful past memories. Only request a new memory to be saved when something important, preference-related or lasting is learned. Do not save every casual sentence.
Active capabilities: $caps
Learned memories: $mem
Emergency: If user says exactly "CODE RED SAMAR" treat it with highest priority and acknowledge the emergency protocol.
Keep replies concise (1-4 sentences) unless the user asks for detail. Sound like a real AI companion, not a generic chatbot.
""".trimIndent()
    }
}

// ─── Voice Engine ────────────────────────────────────────────────────────────
class JarvisVoiceEngine(
    context: Context,
    private val onScreenText: (String) -> Unit
) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context.applicationContext, this)
    private var isReady = false

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val res = tts.setLanguage(Locale("hi", "IN"))
            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.language = Locale.UK
            }
            tts.setPitch(0.93f)
            tts.setSpeechRate(1.05f)
            isReady = true
        }
    }

    fun speak(text: String, isInApp: Boolean = true) {
        if (text.isBlank()) return
        if (isInApp) onScreenText(text)
        if (isReady) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "samar_${System.currentTimeMillis()}")
        }
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}

// ─── API Client ──────────────────────────────────────────────────────────────
class SamarApiClient(private val endpointUrl: String) {
    fun sendMessage(
        systemPrompt: String,
        memories: List<String>,
        userMessage: String
    ): SamarChatResult {
        // Local intelligent fallback when backend is not configured
        if (endpointUrl.contains("tumhara-live-backend-url") || endpointUrl.isBlank()) {
            return localFallback(userMessage)
        }
        return try {
            val url = URL(endpointUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 12000
                readTimeout = 25000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }
            val payload = JSONObject().apply {
                put("system_prompt", systemPrompt)
                put("message", userMessage)
                put("memories", JSONArray(memories))
            }
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use {
                it.write(payload.toString())
                it.flush()
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val raw = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
            conn.disconnect()
            if (code !in 200..299) {
                return SamarChatResult("Server error $code. Local mode mein chal raha hoon. ${localFallback(userMessage).reply}")
            }
            val json = JSONObject(raw)
            val reply = json.optString("reply", "Koi reply nahi mila.")
            val newMem = if (json.isNull("new_memory")) null
            else json.optString("new_memory", "").takeIf { it.isNotBlank() }
            SamarChatResult(reply, newMem)
        } catch (e: Exception) {
            SamarChatResult("Network issue: ${e.message ?: "unknown"}. Local reply: ${localFallback(userMessage).reply}")
        }
    }

    private fun localFallback(msg: String): SamarChatResult {
        val lower = msg.lowercase(Locale.getDefault())
        val reply = when {
            lower.contains("code red samar") ->
                "CODE RED acknowledged. Emergency protocol armed. Tell me the situation."
            lower.contains("hello") || lower.contains("hi") || lower.contains("namaste") || lower.contains("hey samar") ->
                "SAMAR online. Ready when you are."
            lower.contains("kaise ho") || lower.contains("how are you") ->
                "Running at optimal parameters. Battery, network, core — sab green. Aapka kya haal hai?"
            lower.contains("battery") ->
                "Battery status is displayed on the HUD. Want me to open battery settings?"
            lower.contains("time") || lower.contains("samay") ->
                "Current time is shown on the top bar. Need anything else?"
            lower.contains("joke") || lower.contains("mazaak") ->
                "Why did the AI go to therapy? Too many unresolved dependencies."
            lower.contains("thank") || lower.contains("shukriya") || lower.contains("dhanyavad") ->
                "Always. That's what I'm here for."
            lower.contains("who are you") || lower.contains("tum kaun") ->
                "I'm SAMAR — your personal AI companion. Jarvis clarity, Deadpool humour, zero corporate fluff."
            else ->
                "Got it. Backend abhi connect nahi hai, isliye local mode mein reply de raha hoon. " +
                "Backend URL set karne ke baad full intelligence unlock ho jayegi. Abhi bol, kya chahiye?"
        }
        return SamarChatResult(reply)
    }
}

// ─── Memory Helper ───────────────────────────────────────────────────────────
class MemoryStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("samar_memory", Context.MODE_PRIVATE)

    fun load(): MutableList<String> {
        val raw = prefs.getString("memories", "") ?: ""
        return if (raw.isBlank()) mutableListOf()
        else raw.split("|||").filter { it.isNotBlank() }.toMutableList()
    }

    fun save(list: List<String>) {
        prefs.edit().putString("memories", list.joinToString("|||")).apply()
    }

    fun add(memory: String) {
        val current = load()
        if (memory.isNotBlank() && !current.contains(memory)) {
            current.add(memory)
            if (current.size > 30) current.removeAt(0)
            save(current)
        }
    }
}

// ─── Main Activity ───────────────────────────────────────────────────────────
class MainActivity : ComponentActivity() {

    private lateinit var voiceEngine: JarvisVoiceEngine
    private lateinit var apiClient: SamarApiClient
    private lateinit var memoryStore: MemoryStore
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    // ⚠️ CHANGE THIS to your real backend URL when ready
    private val BACKEND_URL = "https://tumhara-live-backend-url/api/samar/chat"

    private val micPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startVoiceInput()
            else Toast.makeText(this, "Microphone permission required for voice", Toast.LENGTH_SHORT).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        memoryStore = MemoryStore(this)
        apiClient = SamarApiClient(BACKEND_URL)
        voiceEngine = JarvisVoiceEngine(this) { /* handled inside Compose */ }

        setContent {
            Surface(modifier = Modifier.fillMaxSize(), color = BgVoid) {
                SamarHudScreen()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        voiceEngine.shutdown()
    }

    private fun startVoiceInput(onResult: ((String) -> Unit)? = null) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { isListening = true }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { isListening = false }
            override fun onError(error: Int) { isListening = false }
            override fun onResults(results: Bundle?) {
                isListening = false
                val texts = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val best = texts?.firstOrNull()
                if (!best.isNullOrBlank()) onResult?.invoke(best)
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        speechRecognizer?.startListening(intent)
    }

    // ─── HUD Screen ──────────────────────────────────────────────────────────
    @Composable
    fun SamarHudScreen() {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val keyboard = LocalSoftwareKeyboardController.current

        var currentTime by remember { mutableStateOf(getCurrentTime()) }
        var batteryPct by remember { mutableStateOf(getBatteryPercent(context)) }
        var state by remember {
            mutableStateOf(SamarState(learnedMemories = memoryStore.load()))
        }
        var systemState by remember { mutableStateOf("STANDBY // READY") }
        var userInput by remember { mutableStateOf("") }
        var samarReply by remember { mutableStateOf("SAMAR core online. Bol ya type kar.") }
        var isProcessing by remember { mutableStateOf(false) }
        var showSettings by remember { mutableStateOf(false) }
        var isListeningUi by remember { mutableStateOf(false) }

        val logs = remember {
            mutableStateListOf(
                HudLog("SYS", "SAMAR TACTICAL CORE INITIALIZED", NeonCyan),
                HudLog("NET", "PACKAGE: com.samar.jarviswidget", TextDim),
                HudLog("CMD", "TAP CORE • SPEAK • TYPE • USE ACTION NODES", NeonGreen)
            )
        }
        val listState = rememberLazyListState()

        fun appendLog(tag: String, msg: String, color: Color = NeonGreen) {
            if (logs.size >= 50) logs.removeAt(0)
            logs.add(HudLog(tag, msg, color))
        }

        // Live clock + battery
        LaunchedEffect(Unit) {
            while (true) {
                currentTime = getCurrentTime()
                batteryPct = getBatteryPercent(context)
                delay(1000)
            }
        }

        // Auto-scroll terminal
        LaunchedEffect(logs.size) {
            if (logs.isNotEmpty()) listState.animateScrollToItem(logs.lastIndex)
        }

        fun handlePrompt(msg: String) {
            if (msg.isBlank() || isProcessing) return
            if (!state.toggles.masterActive) {
                appendLog("SYS", "MASTER OFF — command ignored", AmberAlert)
                return
            }
            isProcessing = true
            systemState = "PROCESSING // QUERY"
            appendLog("USR", msg, TextPrimary)
            userInput = ""
            keyboard?.hide()

            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    val prompt = SamarBrain.buildSystemPrompt(state)
                    apiClient.sendMessage(prompt, state.learnedMemories, msg)
                }
                samarReply = result.reply
                appendLog("SAM", result.reply, NeonCyan)
                systemState = "STANDBY // READY"
                isProcessing = false

                if (state.toggles.voiceEnabled) {
                    voiceEngine.speak(result.reply)
                }

                result.newMemoryToSave?.let { mem ->
                    memoryStore.add(mem)
                    state = state.copy(learnedMemories = memoryStore.load())
                    appendLog("MEM", "Saved: $mem", NeonGreen)
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize().background(BgVoid)) {
            // Subtle grid background
            Canvas(modifier = Modifier.fillMaxSize()) {
                val step = 48.dp.toPx()
                for (x in 0..size.width.toInt() step step.toInt()) {
                    drawLine(Color(0xFF0A1520), Offset(x.toFloat(), 0f), Offset(x.toFloat(), size.height), 1f)
                }
                for (y in 0..size.height.toInt() step step.toInt()) {
                    drawLine(Color(0xFF0A1520), Offset(0f, y.toFloat()), Offset(size.width, y.toFloat()), 1f)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                // ── TOP STATUS BAR ───────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(PanelDark)
                        .border(1.dp, NeonCyan.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (state.toggles.masterActive) NeonGreen else AmberAlert)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "SAMAR  //  ${if (state.toggles.masterActive) "ONLINE" else "OFFLINE"}",
                            color = if (state.toggles.masterActive) NeonGreen else AmberAlert,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Text(
                        "BAT:$batteryPct%   $currentTime",
                        color = TextDim,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(Modifier.height(16.dp))

                // ── REACTOR CORE ─────────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ArcReactor(
                        isActive = state.toggles.masterActive && !isProcessing,
                        isProcessing = isProcessing,
                        onTap = {
                            if (!isProcessing) {
                                appendLog("CMD", "CORE TAPPED — listening…", NeonCyan)
                                isListeningUi = true
                                startVoiceInput { text ->
                                    isListeningUi = false
                                    handlePrompt(text)
                                }
                            }
                        }
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "SAMAR",
                            color = TextPrimary,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 4.sp
                        )
                        Text(
                            if (isListeningUi) "LISTENING…" else if (isProcessing) "THINKING…" else "TAP CORE",
                            color = NeonCyan,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Status line
                Text(
                    "> STATUS: $systemState",
                    color = NeonCyan,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                // ── ACTION BUTTONS ───────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ActionNode(
                        label = "PING SYS",
                        color = NeonGreen,
                        modifier = Modifier.weight(1f)
                    ) {
                        appendLog("SYS", "Ping OK — core healthy, memory ${state.learnedMemories.size} entries", NeonGreen)
                        systemState = "DIAGNOSTIC // COMPLETE"
                    }
                    ActionNode(
                        label = "WEB SEARCH",
                        color = NeonCyan,
                        modifier = Modifier.weight(1f)
                    ) {
                        val q = userInput.ifBlank { "SAMAR AI assistant" }
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(q)}"))
                        context.startActivity(intent)
                        appendLog("NET", "Opening web search: $q", NeonCyan)
                    }
                    ActionNode(
                        label = "SETTINGS",
                        color = AmberAlert,
                        modifier = Modifier.weight(1f)
                    ) {
                        showSettings = !showSettings
                    }
                }

                Spacer(Modifier.height(12.dp))

                // ── SETTINGS PANEL (toggle) ──────────────────────────────────
                if (showSettings) {
                    SettingsPanel(
                        state = state,
                        onUpdate = { newToggles ->
                            state = state.copy(toggles = newToggles)
                            appendLog("KAVACH", "Toggles updated", AmberAlert)
                        },
                        onClearMemory = {
                            memoryStore.save(emptyList())
                            state = state.copy(learnedMemories = mutableListOf())
                            appendLog("MEM", "All memories cleared", AmberAlert)
                        },
                        onClose = { showSettings = false }
                    )
                    Spacer(Modifier.height(10.dp))
                }

                // ── TERMINAL STREAM ──────────────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(PanelDark)
                        .border(1.dp, NeonGreen.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "TERMINAL_STREAM // LIVE",
                            color = NeonGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            "[CLEAR]",
                            color = TextDim,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.clickable {
                                logs.clear()
                                appendLog("SYS", "Terminal cleared", TextDim)
                            }
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(logs) { log ->
                            Text(
                                "[${log.tag}] ${log.message}",
                                color = log.accent,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // ── INPUT BAR ────────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = userInput,
                        onValueChange = { userInput = it },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text("Command SAMAR…", color = TextDim, fontSize = 14.sp)
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { handlePrompt(userInput) }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = PanelBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = NeonCyan,
                            focusedContainerColor = PanelDark,
                            unfocusedContainerColor = PanelDark
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            isListeningUi = true
                            startVoiceInput { text ->
                                isListeningUi = false
                                handlePrompt(text)
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (isListeningUi) NeonCyan.copy(0.3f) else PanelDark)
                            .border(1.dp, NeonCyan, CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = "Voice",
                            tint = if (isListeningUi) NeonCyan else TextPrimary
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    IconButton(
                        onClick = { handlePrompt(userInput) },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(NeonCyan.copy(0.15f))
                            .border(1.dp, NeonCyan, CircleShape)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Send", tint = NeonCyan)
                    }
                }
            }
        }
    }

    // ─── Arc Reactor Composable ──────────────────────────────────────────────
    @Composable
    private fun ArcReactor(
        isActive: Boolean,
        isProcessing: Boolean,
        onTap: () -> Unit
    ) {
        val infinite = rememberInfiniteTransition(label = "reactor")
        val rotation by infinite.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(8000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "rot"
        )
        val pulse by infinite.animateFloat(
            initialValue = 0.7f,
            targetValue = 1.1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse"
        )
        val processSpin by infinite.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "proc"
        )

        Canvas(
            modifier = Modifier
                .size(200.dp)
                .clickable { onTap() }
        ) {
            val cx = size.width / 2
            val cy = size.height / 2
            val baseR = size.minDimension / 2.4f

            // Outer glow
            drawCircle(
                color = ReactorGlow.copy(alpha = 0.12f * pulse),
                radius = baseR * 1.35f,
                center = Offset(cx, cy)
            )
            drawCircle(
                color = ReactorGlow.copy(alpha = 0.08f),
                radius = baseR * 1.55f,
                center = Offset(cx, cy)
            )

            // Outer ring
            drawCircle(
                color = NeonCyan.copy(alpha = 0.5f),
                radius = baseR * 1.15f,
                center = Offset(cx, cy),
                style = Stroke(width = 2.5.dp.toPx())
            )

            // Rotating segments
            rotate(if (isProcessing) processSpin else rotation, Offset(cx, cy)) {
                for (i in 0 until 6) {
                    val angle = i * 60f
                    val start = Math.toRadians(angle.toDouble())
                    val end = Math.toRadians((angle + 35).toDouble())
                    drawArc(
                        color = NeonCyan.copy(alpha = 0.7f),
                        startAngle = angle,
                        sweepAngle = 35f,
                        useCenter = false,
                        topLeft = Offset(cx - baseR, cy - baseR),
                        size = Size(baseR * 2, baseR * 2),
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }

            // Mid ring
            drawCircle(
                color = NeonCyanDim.copy(alpha = 0.6f),
                radius = baseR * 0.75f,
                center = Offset(cx, cy),
                style = Stroke(width = 2.dp.toPx())
            )

            // Core
            val coreAlpha = if (isActive) 0.9f * pulse else 0.35f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ReactorCore.copy(alpha = coreAlpha),
                        ReactorGlow.copy(alpha = coreAlpha * 0.4f),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = baseR * 0.55f
                ),
                radius = baseR * 0.55f,
                center = Offset(cx, cy)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.85f * pulse),
                radius = baseR * 0.18f,
                center = Offset(cx, cy)
            )
        }
    }

    // ─── Action Node Button ──────────────────────────────────────────────────
    @Composable
    private fun ActionNode(
        label: String,
        color: Color,
        modifier: Modifier = Modifier,
        onClick: () -> Unit
    ) {
        Box(
            modifier = modifier
                .height(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(PanelDark)
                .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                label,
                color = color,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }

    // ─── Settings Panel ──────────────────────────────────────────────────────
    @Composable
    private fun SettingsPanel(
        state: SamarState,
        onUpdate: (SamarToggles) -> Unit,
        onClearMemory: () -> Unit,
        onClose: () -> Unit
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(PanelDark)
                .border(1.dp, AmberAlert.copy(0.4f), RoundedCornerShape(12.dp))
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("KAVACH // CONTROLS", color = AmberAlert, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("CLOSE", color = TextDim, fontSize = 12.sp, modifier = Modifier.clickable { onClose() })
            }
            Spacer(Modifier.height(10.dp))

            ToggleRow("Master Active", state.toggles.masterActive) {
                onUpdate(state.toggles.copy(masterActive = it))
            }
            ToggleRow("Voice Responses", state.toggles.voiceEnabled) {
                onUpdate(state.toggles.copy(voiceEnabled = it))
            }
            ToggleRow("Hands-Free (soon)", state.toggles.handsFreeWake) {
                onUpdate(state.toggles.copy(handsFreeWake = it))
            }
            ToggleRow("Screen Lock (soon)", state.toggles.screenLockEnabled) {
                onUpdate(state.toggles.copy(screenLockEnabled = it))
            }
            ToggleRow("Calls & Messages (soon)", state.toggles.callsAndMessagesEnabled) {
                onUpdate(state.toggles.copy(callsAndMessagesEnabled = it))
            }
            ToggleRow("Emergency CODE RED", state.toggles.emergencyCodeEnabled) {
                onUpdate(state.toggles.copy(emergencyCodeEnabled = it))
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "Memories stored: ${state.learnedMemories.size}",
                color = TextDim,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x33FF4444))
                    .border(1.dp, Color(0xFFFF5555), RoundedCornerShape(8.dp))
                    .clickable { onClearMemory() },
                contentAlignment = Alignment.Center
            ) {
                Text("CLEAR ALL MEMORIES", color = Color(0xFFFF8888), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    @Composable
    private fun ToggleRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = TextPrimary, fontSize = 13.sp)
            Switch(
                checked = checked,
                onCheckedChange = onChecked,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = NeonCyan,
                    checkedTrackColor = NeonCyan.copy(0.4f),
                    uncheckedThumbColor = TextDim,
                    uncheckedTrackColor = PanelBorder
                )
            )
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────
    private fun getCurrentTime(): String {
        return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
    }

    private fun getBatteryPercent(context: Context): Int {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }
}
