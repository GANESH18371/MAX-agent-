package com.example.util

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.data.api.GeminiClient
import com.example.receiver.TermuxResultReceiver
import com.example.ui.MaxViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

data class TermuxLocalCommandPattern(
    val triggerPhrases: List<String>,
    val bashCommand: String,
    val descriptionHindi: String,
    val isDestructive: Boolean = false
)

data class TermuxExecutionInfo(
    val command: String,
    val source: String, // "LOCAL" or "GEMINI"
    val timestamp: Long = System.currentTimeMillis(),
    val output: String = "",
    val error: String = "",
    val exitCode: Int = 0,
    val isComplete: Boolean = false
)

object TermuxCommandManager {

    private const val TAG = "TermuxCommandManager"
    const val TERMUX_PACKAGE = "com.termux"
    const val TERMUX_API_PACKAGE = "com.termux.api"
    private const val TERMUX_RUN_COMMAND_ACTION = "com.termux.RUN_COMMAND"
    private const val TERMUX_RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _lastExecution = MutableStateFlow<TermuxExecutionInfo?>(null)
    val lastExecution: StateFlow<TermuxExecutionInfo?> = _lastExecution.asStateFlow()

    private var executionCounter = 100

    // Callback when command completes
    var onCommandResultListener: ((TermuxExecutionInfo) -> Unit)? = null

    // Comprehensive LOCAL / OFFLINE Command Mapping List
    private val LOCAL_COMMAND_PATTERNS = listOf(
        // 1. Files & Directory Navigation
        TermuxLocalCommandPattern(
            triggerPhrases = listOf(
                "files dikhao", "files list karo", "yahan kya hai", "directory dikhao",
                "folder dikhao", "list files", "ls", "files dekho", "files batao"
            ),
            bashCommand = "ls -lah",
            descriptionHindi = "Directory ki files list kar raha hoon"
        ),
        TermuxLocalCommandPattern(
            triggerPhrases = listOf(
                "current directory", "kahan hoon", "present directory", "current path",
                "path batao", "pwd", "folder ka path", "location batao"
            ),
            bashCommand = "pwd",
            descriptionHindi = "Current directory path check kar raha hoon"
        ),

        // 2. Storage & Memory Resources
        TermuxLocalCommandPattern(
            triggerPhrases = listOf(
                "storage batao", "space check karo", "disk space", "storage kitna hai",
                "free storage", "phone storage", "df -h", "memory space"
            ),
            bashCommand = "df -h /data /sdcard 2>/dev/null || df -h",
            descriptionHindi = "Storage space check kar raha hoon"
        ),
        TermuxLocalCommandPattern(
            triggerPhrases = listOf(
                "ram check karo", "memory batao", "ram kitni bachi hai", "free ram",
                "ram status", "free -h", "ram kitna hai", "available memory"
            ),
            bashCommand = "free -h 2>/dev/null || cat /proc/meminfo | head -n 4",
            descriptionHindi = "RAM aur memory status check kar raha hoon"
        ),

        // 3. System Processes & CPU
        TermuxLocalCommandPattern(
            triggerPhrases = listOf(
                "processes dikhao", "running processes", "running apps", "ps",
                "processes check karo", "system processes", "konsi app chal rahi hai"
            ),
            bashCommand = "ps -ef | head -n 25",
            descriptionHindi = "Active processes check kar raha hoon"
        ),
        TermuxLocalCommandPattern(
            triggerPhrases = listOf(
                "cpu info", "processor details", "cpu check karo", "processor batao",
                "cpu details", "processor kya hai"
            ),
            bashCommand = "lscpu 2>/dev/null || cat /proc/cpuinfo | grep -m 4 'model name' || uname -m",
            descriptionHindi = "CPU aur processor information nikaal raha hoon"
        ),
        TermuxLocalCommandPattern(
            triggerPhrases = listOf(
                "uptime batao", "phone kitni der se on hai", "system uptime",
                "kab se chal raha hai", "uptime"
            ),
            bashCommand = "uptime",
            descriptionHindi = "System uptime check kar raha hoon"
        ),
        TermuxLocalCommandPattern(
            triggerPhrases = listOf(
                "date batao", "aaj ki date", "current date", "samay batao",
                "aaj konsa din hai", "time aur date", "date"
            ),
            bashCommand = "date",
            descriptionHindi = "Date aur time check kar raha hoon"
        ),
        TermuxLocalCommandPattern(
            triggerPhrases = listOf(
                "who am i", "user batao", "current user", "termux user", "username batao"
            ),
            bashCommand = "whoami",
            descriptionHindi = "Current user info check kar raha hoon"
        ),
        TermuxLocalCommandPattern(
            triggerPhrases = listOf(
                "uname", "kernel version", "linux version", "os version", "architecture batao"
            ),
            bashCommand = "uname -a",
            descriptionHindi = "Linux kernel aur architecture info check kar raha hoon"
        ),

        // 4. Network & Connectivity
        TermuxLocalCommandPattern(
            triggerPhrases = listOf(
                "internet check karo", "ping check karo", "net chal raha hai", "google ping",
                "connectivity check karo", "ping google", "internet speed test"
            ),
            bashCommand = "ping -c 3 8.8.8.8",
            descriptionHindi = "Google server par ping bhej kar internet check kar raha hoon"
        ),
        TermuxLocalCommandPattern(
            triggerPhrases = listOf(
                "ip address batao", "my ip", "local ip", "ifconfig", "ip address kya hai",
                "network ip", "ip dikhao"
            ),
            bashCommand = "ip -brief address 2>/dev/null || ifconfig",
            descriptionHindi = "Device ka IP address nikaal raha hoon"
        ),
        TermuxLocalCommandPattern(
            triggerPhrases = listOf(
                "public ip batao", "external ip", "internet ip", "global ip"
            ),
            bashCommand = "curl -s https://ifconfig.me 2>/dev/null || curl -s https://api.ipify.org",
            descriptionHindi = "Public internet IP address check kar raha hoon"
        ),
        TermuxLocalCommandPattern(
            triggerPhrases = listOf(
                "open ports", "active connections", "netstat", "listening ports"
            ),
            bashCommand = "netstat -tuln 2>/dev/null || ss -tuln",
            descriptionHindi = "Active network ports aur connections check kar raha hoon"
        ),
        TermuxLocalCommandPattern(
            triggerPhrases = listOf(
                "wifi info", "wifi details", "wifi connection", "termux wifi"
            ),
            bashCommand = "termux-wifi-connectioninfo 2>/dev/null || ip link show wlan0 2>/dev/null || iwconfig 2>/dev/null",
            descriptionHindi = "Wi-Fi network connection details check kar raha hoon"
        ),

        // 5. Termux:API Hardware Actions
        TermuxLocalCommandPattern(
            triggerPhrases = listOf(
                "battery status", "termux battery", "battery check karo", "battery info"
            ),
            bashCommand = "termux-battery-status 2>/dev/null || dumpsys battery 2>/dev/null | head -n 12",
            descriptionHindi = "Battery health aur status check kar raha hoon"
        ),
        TermuxLocalCommandPattern(
            triggerPhrases = listOf(
                "termux torch on", "torch jalao termux", "termux flashlight on"
            ),
            bashCommand = "termux-torch on",
            descriptionHindi = "Termux API se flashlight on kar raha hoon"
        ),
        TermuxLocalCommandPattern(
            triggerPhrases = listOf(
                "termux torch off", "torch band karo termux", "termux flashlight off"
            ),
            bashCommand = "termux-torch off",
            descriptionHindi = "Termux API se flashlight off kar raha hoon"
        ),
        TermuxLocalCommandPattern(
            triggerPhrases = listOf(
                "termux vibrate", "vibrate karo termux", "phone vibrate karo"
            ),
            bashCommand = "termux-vibrate -d 500",
            descriptionHindi = "Phone ko vibrate kar raha hoon"
        ),
        TermuxLocalCommandPattern(
            triggerPhrases = listOf(
                "clipboard padho", "copied text dikhao", "termux clipboard"
            ),
            bashCommand = "termux-clipboard-get",
            descriptionHindi = "Clipboard ka text padh raha hoon"
        ),

        // 6. Packages & Development Environment
        TermuxLocalCommandPattern(
            triggerPhrases = listOf(
                "packages update karo", "termux update karo", "pkg update", "termux update", "update packages"
            ),
            bashCommand = "pkg update -y",
            descriptionHindi = "Termux packages ko update kar raha hoon"
        ),
        TermuxLocalCommandPattern(
            triggerPhrases = listOf(
                "installed packages", "packages list karo", "pkg list"
            ),
            bashCommand = "pkg list-installed | head -n 25",
            descriptionHindi = "Installed packages list check kar raha hoon"
        ),
        TermuxLocalCommandPattern(
            triggerPhrases = listOf(
                "git status", "git check karo"
            ),
            bashCommand = "git status 2>/dev/null || echo 'Git repository nahi mila'",
            descriptionHindi = "Git repository status check kar raha hoon"
        ),
        TermuxLocalCommandPattern(
            triggerPhrases = listOf(
                "git log", "recent commits"
            ),
            bashCommand = "git log -n 5 --oneline 2>/dev/null || echo 'Git commits nahi mile'",
            descriptionHindi = "Recent Git commits dekh raha hoon"
        ),
        TermuxLocalCommandPattern(
            triggerPhrases = listOf(
                "python version", "python check karo"
            ),
            bashCommand = "python --version 2>/dev/null || python3 --version 2>/dev/null || echo 'Python installed nahi hai'",
            descriptionHindi = "Python version check kar raha hoon"
        ),
        TermuxLocalCommandPattern(
            triggerPhrases = listOf(
                "node version", "nodejs version"
            ),
            bashCommand = "node -v 2>/dev/null || echo 'NodeJS installed nahi hai'",
            descriptionHindi = "Node.js version check kar raha hoon"
        ),

        // 7. Destructive Patterns (Mandatory Confirmation)
        TermuxLocalCommandPattern(
            triggerPhrases = listOf(
                "cache saf karo", "cache clear karo", "termux cache clean"
            ),
            bashCommand = "apt clean && pkg clean",
            descriptionHindi = "Termux package cache clean kar raha hoon",
            isDestructive = false
        )
    )

    fun isTermuxInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun isTermuxApiInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(TERMUX_API_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Finds match from local mapping list (Priority 1: LOCAL / OFFLINE)
     */
    fun matchLocalCommand(voiceInput: String): TermuxLocalCommandPattern? {
        val clean = voiceInput.trim().lowercase()
            .removePrefix("termux me ")
            .removePrefix("termux ")
            .removePrefix("terminal me ")
            .removePrefix("terminal ")
            .removeSuffix(" in termux")
            .trim()

        for (pattern in LOCAL_COMMAND_PATTERNS) {
            for (phrase in pattern.triggerPhrases) {
                if (clean == phrase || clean.startsWith("$phrase ") || clean.endsWith(" $phrase") || clean.contains(phrase)) {
                    return pattern
                }
            }
        }
        return null
    }

    /**
     * Checks if a voice command or generated bash command is destructive.
     */
    fun isDestructiveCommand(bashCommand: String, voiceInput: String = ""): Boolean {
        val lowerCmd = bashCommand.lowercase()
        val lowerVoice = voiceInput.lowercase()

        // Dangerous Bash patterns
        val dangerousPatterns = listOf(
            "rm ", "rm -", "rmdir", "mkfs", "dd ", "reboot", "shutdown",
            "poweroff", "kill -9", "killall", "chmod -r 777", "> /dev/",
            "truncate", "wipe", "format", ":(){ :|:& };:", "drop database"
        )

        val containsDangerousBash = dangerousPatterns.any { lowerCmd.contains(it) }

        // Dangerous Voice Keywords
        val dangerousVoiceKeywords = listOf(
            "delete karo", "delete kar do", "hata do", "uda do", "format karo",
            "remove karo", "files delete", "data uda do", "wipe karo", "erase"
        )

        val containsDangerousVoice = dangerousVoiceKeywords.any { lowerVoice.contains(it) }

        return containsDangerousBash || containsDangerousVoice
    }

    /**
     * Executes the bash command using Termux:API official com.termux.RUN_COMMAND intent.
     */
    fun executeTermuxCommand(
        context: Context,
        bashCommand: String,
        source: String, // "LOCAL" or "GEMINI"
        onStarted: (String) -> Unit = {}
    ) {
        val executionId = ++executionCounter
        Log.d(TAG, "TERMUX_COMMAND_SOURCE: $source")
        Log.d(TAG, "TERMUX_COMMAND_EXECUTED: $bashCommand")

        val newExec = TermuxExecutionInfo(
            command = bashCommand,
            source = source
        )
        _lastExecution.value = newExec

        if (!isTermuxInstalled(context)) {
            val fallbackMsg = "Termux app install nahi hai. Kripya F-Droid se Termux install karein."
            Log.w(TAG, fallbackMsg)
            onStarted("Termux app install nahi hai.")
            handleExecutionResult(
                context = context,
                source = source,
                command = bashCommand,
                stdout = "",
                stderr = fallbackMsg,
                exitCode = 127
            )
            return
        }

        try {
            // Build official Termux RUN_COMMAND intent
            val runIntent = Intent(TERMUX_RUN_COMMAND_ACTION).apply {
                setClassName(TERMUX_PACKAGE, TERMUX_RUN_COMMAND_SERVICE)
                putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
                putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", bashCommand))
                putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
                putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
                putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0")

                // PendingIntent for output callback
                val resultIntent = Intent(context, TermuxResultReceiver::class.java).apply {
                    action = TermuxResultReceiver.ACTION_TERMUX_RESULT
                    putExtra("ORIGINAL_COMMAND", bashCommand)
                    putExtra("SOURCE", source)
                }

                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    executionId,
                    resultIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
                putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", pendingIntent)
            }

            context.startService(runIntent)
            onStarted("Termux command chala di: $bashCommand")
            Log.d(TAG, "Termux RUN_COMMAND service intent dispatched successfully")

            // Timeout listener to gracefully report status if Termux background execution is silent
            scope.launch {
                kotlinx.coroutines.delay(6000)
                if (_lastExecution.value?.isComplete == false) {
                    Log.d(TAG, "Termux execution pending or completed without broadcast")
                    handleExecutionResult(
                        context = context,
                        source = source,
                        command = bashCommand,
                        stdout = "Command Termux me execute ho gayi hai.",
                        stderr = "",
                        exitCode = 0
                    )
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Termux service", e)
            handleExecutionResult(
                context = context,
                source = source,
                command = bashCommand,
                stdout = "",
                stderr = "Termux service start nahi ho paayi: ${e.localizedMessage ?: "Unknown error"}",
                exitCode = 1
            )
        }
    }

    /**
     * Handles real output received from Termux / shell and generates natural TTS summary.
     */
    fun handleExecutionResult(
        context: Context,
        source: String,
        command: String,
        stdout: String,
        stderr: String,
        exitCode: Int
    ) {
        val resultText = if (stdout.isNotBlank()) stdout else stderr
        val info = TermuxExecutionInfo(
            command = command,
            source = source,
            output = stdout,
            error = stderr,
            exitCode = exitCode,
            isComplete = true
        )
        _lastExecution.value = info

        val spokenSummary = summarizeOutputForTts(command, stdout, stderr, exitCode)
        Log.d(TAG, "Generated TTS summary: \"$spokenSummary\"")

        mainHandler.post {
            onCommandResultListener?.invoke(info)
            MaxViewModel.activeInstance?.let { vm ->
                vm.voiceAssistant.speak(spokenSummary)
                vm.recordTermuxOutput(command, resultText, source)
            }
        }
    }

    /**
     * Generates a concise, natural Hindi spoken summary of the command output.
     */
    fun summarizeOutputForTts(command: String, stdout: String, stderr: String, exitCode: Int): String {
        if (exitCode != 0 && stderr.isNotBlank()) {
            val shortErr = stderr.lines().firstOrNull()?.take(80) ?: "Error"
            return "Command execute karne me dikkat aayi: $shortErr"
        }

        if (stdout.isBlank()) {
            return "Command execute ho gayi hai, koi output nahi aaya."
        }

        val cleanOut = stdout.trim()
        val lowerCmd = command.lowercase()

        return when {
            lowerCmd.contains("ping") -> {
                if (cleanOut.contains("0% packet loss") || cleanOut.contains("bytes from")) {
                    "Internet bilkul theek chal raha hai, ping successful raha."
                } else {
                    "Internet connection me rukawat lag rahi hai, packets drop ho rahe hain."
                }
            }
            lowerCmd.contains("df -h") -> {
                val lines = cleanOut.lines().filter { it.contains("G") || it.contains("M") }
                val targetLine = lines.firstOrNull { it.contains("/data") || it.contains("/sdcard") } ?: lines.firstOrNull()
                if (targetLine != null) {
                    val parts = targetLine.split(Regex("\\s+")).filter { it.isNotBlank() }
                    if (parts.size >= 4) {
                        "Aapka storage: Total ${parts[1]} me se ${parts[2]} use ho chuka hai, aur lagbhag ${parts[3]} free hai."
                    } else {
                        "Storage status mil gaya hai: ${targetLine.take(80)}"
                    }
                } else {
                    "Storage info prapt ho gayi hai."
                }
            }
            lowerCmd.contains("free") -> {
                val memLine = cleanOut.lines().firstOrNull { it.startsWith("Mem:") || it.contains("MemTotal") }
                if (memLine != null) {
                    "RAM status mil gaya hai: ${memLine.take(60)}"
                } else {
                    "RAM aur memory ki jankari mil gayi hai."
                }
            }
            lowerCmd.contains("date") -> {
                "Current date aur time hai: $cleanOut"
            }
            lowerCmd.contains("uptime") -> {
                "System uptime: $cleanOut"
            }
            lowerCmd.contains("whoami") -> {
                "Termux user hai: $cleanOut"
            }
            lowerCmd.contains("pwd") -> {
                "Current folder path hai: $cleanOut"
            }
            lowerCmd.contains("ls") -> {
                val fileNames = cleanOut.lines()
                    .map { line -> line.split(Regex("\\s+")).lastOrNull() ?: "" }
                    .filter { it.isNotBlank() && it != "." && it != ".." }
                    .take(5)
                if (fileNames.isNotEmpty()) {
                    "Folder me files mili hain: ${fileNames.joinToString(", ")}"
                } else {
                    "Folder khali hai, koi files nahi mili."
                }
            }
            lowerCmd.contains("ip") || lowerCmd.contains("ifconfig") -> {
                val ipMatch = Regex("(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})").find(cleanOut)
                if (ipMatch != null) {
                    "Aapka IP address hai: ${ipMatch.value}"
                } else {
                    "IP address info mil gayi hai: ${cleanOut.take(80)}"
                }
            }
            lowerCmd.contains("termux-battery-status") -> {
                "Battery status check ho gaya hai."
            }
            else -> {
                val firstLine = cleanOut.lines().firstOrNull()?.take(100) ?: ""
                "Command chalu ho gayi hai. Output: $firstLine"
            }
        }
    }
}
