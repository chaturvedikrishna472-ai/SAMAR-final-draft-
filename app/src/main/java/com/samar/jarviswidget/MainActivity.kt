package com.samar.jarviswidget

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Bundle
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

class SamarApiClient(private val endpointUrl: String) {
    fun sendMessage(
        systemPrompt: String,
        memories: List<String>,
        userMessage: String
    ): SamarChatResult {
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

class MainActivity : ComponentActivity() {

    private lateinit var voiceEngine: JarvisVoiceEngine
    private lateinit var apiClient: SamarApiClient
    private lateinit var memoryStore: MemoryStore
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var pendingVoiceCallback: ((String) -> Unit)? = null

    private val BACKEND_URL = "https://tumhara-live-backend-url/api/samar/chat"

    private val micPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startVoiceInput(pendingVoiceCallback)
            else Toast.makeText(this, "Microphone permission required for voice", Toast.LENGTH_SHORT).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        memoryStore = MemoryStore(this)
        apiClient = SamarApiClient(BACKEND_URL)
        voiceEngine = JarvisVoiceEngine(this) { }

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
        pendingVoiceCallback = onResult
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

        LaunchedEffect(Unit) {
            while (true) {
                currentTime = getCurrentTime()
                batteryPct = getBatteryPercent(context)
                delay(1000)
            }
        }

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
            Canvas(modifier = Modifier.fillMaxSize()) {
                val step = 48.dp.toPx()
                val gridColor = NeonCyan.copy(alpha = 0.04f)
                for (x in 0..size.width.toInt() step step.toInt()) {
                    drawLine(
                        color = gridColor,
                        start = Offset(x.toFloat(), 0f),
                        end = Offset(x.toFloat(), size.height),
                        strokeWidth = 1f
                    )
                }
                for (y in 0..size.height.toInt() step step.toInt()) {
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y.toFloat()),
                        end = Offset(size.width, y.toFloat()),
                        strokeWidth = 1f
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "SAMAR // $systemState",
                        color = NeonCyan,
                        fontSize = 12.sp,
                    
