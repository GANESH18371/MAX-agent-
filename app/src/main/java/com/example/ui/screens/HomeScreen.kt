package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Assistant
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraFront
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.MaxViewModel
import com.example.ui.components.VoiceVisualizerOrb
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.PrimaryIndigo
import com.example.ui.theme.SecondaryViolet

data class QuickPreset(
    val title: String,
    val command: String,
    val icon: ImageVector,
    val color: Color
)

@Composable
fun HomeScreen(
    viewModel: MaxViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isServiceEnabled by viewModel.isServiceEnabled.collectAsStateWithLifecycle()
    val canDrawOverlays by viewModel.canDrawOverlays.collectAsStateWithLifecycle()
    val isOverlayActive by viewModel.isOverlayActive.collectAsStateWithLifecycle()
    val isDefaultAssistant by viewModel.isDefaultAssistant.collectAsStateWithLifecycle()
    val currentApp by viewModel.currentApp.collectAsStateWithLifecycle()

    val voiceStatus by viewModel.voiceAssistant.voiceStatus.collectAsStateWithLifecycle()
    val transcript by viewModel.voiceAssistant.transcript.collectAsStateWithLifecycle()
    val isListening by viewModel.voiceAssistant.isListening.collectAsStateWithLifecycle()
    val isSpeaking by viewModel.voiceAssistant.isSpeaking.collectAsStateWithLifecycle()
    val amplitude by viewModel.voiceAssistant.speechAmplitude.collectAsStateWithLifecycle()

    val isProcessing by viewModel.isProcessing.collectAsStateWithLifecycle()
    val lastDecision by viewModel.lastDecision.collectAsStateWithLifecycle()
    val activeReminders by viewModel.activeReminders.collectAsStateWithLifecycle()
    val isTheftAlarmActive by com.example.util.TheftAlarmManager.isAlarmActive.collectAsStateWithLifecycle()

    val termuxLastExec by viewModel.termuxLastExecution.collectAsStateWithLifecycle()
    val isTermuxInstalled by viewModel.isTermuxInstalled.collectAsStateWithLifecycle()
    val isTermuxApiInstalled by viewModel.isTermuxApiInstalled.collectAsStateWithLifecycle()
    var showTermuxGuideDialog by remember { mutableStateOf(false) }

    var manualTextInput by remember { mutableStateOf("") }

    val presets = remember {
        listOf(
            QuickPreset("Files Dikhao", "files dikhao", Icons.Default.Code, AccentCyan),
            QuickPreset("Storage Check", "storage batao", Icons.Default.FormatListBulleted, AccentGreen),
            QuickPreset("Internet Ping", "internet check karo", Icons.Default.AutoAwesome, PrimaryIndigo),
            QuickPreset("Chori Alarm", "chori alarm bajao", Icons.Default.Alarm, Color(0xFFEF4444)),
            QuickPreset("Tum Kaun Ho?", "tumhe kisne banaya aur tum kaun ho", Icons.Default.AutoAwesome, PrimaryIndigo),
            QuickPreset("Selfie Lo", "selfie lo", Icons.Default.CameraFront, AccentCyan),
            QuickPreset("Saamne Kya Hai?", "saamne kya hai batao", Icons.Default.RemoveRedEye, SecondaryViolet),
            QuickPreset("Aaj Ka Mausam", "aaj ka mausam kaisa hai", Icons.Default.WbSunny, AccentAmber),
            QuickPreset("5 Min Reminder", "5 minute baad dawai lene ka reminder set karo", Icons.Default.Alarm, PrimaryIndigo),
            QuickPreset("Arijit Singh Play", "YouTube khol ke Arijit Singh ke gane play karo", Icons.Default.PlayArrow, AccentGreen)
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Header Banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Max AI",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = SecondaryViolet.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Hindi Voice Control",
                            color = SecondaryViolet,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = "Aapka Personal Phone Operator",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                )
            }

            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(PrimaryIndigo, SecondaryViolet)))
                    .clickable { viewModel.checkServiceStatus() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "Sync",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // EMERGENCY THEFT ALARM ACTIVE BANNER
        AnimatedVisibility(visible = isTheftAlarmActive) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF7F1D1D)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .border(2.dp, Color(0xFFEF4444), RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .padding(14.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Alarm,
                            contentDescription = "Siren Alert",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "🚨 EMERGENCY ALARM RINGING",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                            Text(
                                text = "Loud Siren Playing at Max Volume",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.LightGray)
                            )
                        }
                    }
                    Button(
                        onClick = { com.example.util.TheftAlarmManager.stopEmergencySiren(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("STOP SIREN", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // Accessibility Service Alert if disabled
        AnimatedVisibility(visible = !isServiceEnabled) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2E1065)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .border(1.dp, SecondaryViolet, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AccessibilityNew,
                        contentDescription = null,
                        tint = SecondaryViolet,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Accessibility Service Enable Karein",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Max ko screen read aur auto-tap karne ke liye zaroori hai.",
                            color = Color.LightGray,
                            fontSize = 12.sp
                        )
                    }
                    Button(
                        onClick = { viewModel.openAccessibilitySettings(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = SecondaryViolet),
                        modifier = Modifier.testTag("enable_accessibility_button")
                    ) {
                        Text("Enable", fontSize = 12.sp)
                    }
                }
            }
        }

        // Floating Overlay Control Banner
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Floating Voice Widget",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = if (isOverlayActive) "Active (YouTube ke upar floating widget)" else "YouTube ke upar voice widget chalayein",
                            color = if (isOverlayActive) AccentGreen else Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                }
                Button(
                    onClick = { viewModel.toggleOverlayService(context) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isOverlayActive) Color(0xFF334155) else PrimaryIndigo
                    ),
                    modifier = Modifier.testTag("toggle_overlay_button")
                ) {
                    Text(if (isOverlayActive) "Stop" else "Start Widget", fontSize = 11.sp)
                }
            }
        }

        // Default Digital Assistant Banner
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isDefaultAssistant) Color(0xFF0F291E) else Color(0xFF1E1B4B)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .border(
                    1.dp,
                    if (isDefaultAssistant) AccentGreen else PrimaryIndigo,
                    RoundedCornerShape(12.dp)
                ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (isDefaultAssistant) Icons.Default.CheckCircle else Icons.Default.Assistant,
                        contentDescription = "Default Assistant",
                        tint = if (isDefaultAssistant) AccentGreen else AccentCyan,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (isDefaultAssistant) "Default Assistant: Active ✅" else "Default Digital Assistant Set Karein",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 13.sp
                        )
                        Text(
                            text = if (isDefaultAssistant) "Home long-press ya gesture par Max turant activate hoga." else "Home-button long press par Max chalu karne ke liye set karein.",
                            color = Color.LightGray,
                            fontSize = 11.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { viewModel.openDefaultAssistantSettings() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDefaultAssistant) Color(0xFF065F46) else PrimaryIndigo
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("setup_default_assistant_button")
                ) {
                    Text(if (isDefaultAssistant) "Settings" else "Set Karein", fontSize = 11.sp, color = Color.White)
                }
            }
        }

        // Voice Orb Controller
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = voiceStatus,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (isListening) AccentCyan else if (isSpeaking) AccentGreen else Color.White
                    )
                )

                Spacer(modifier = Modifier.height(20.dp))

                VoiceVisualizerOrb(
                    isListening = isListening,
                    isSpeaking = isSpeaking,
                    isProcessing = isProcessing,
                    amplitude = amplitude,
                    onClick = {
                        if (isListening) {
                            viewModel.stopListening()
                        } else {
                            viewModel.startListening()
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (transcript.isNotBlank()) "\"$transcript\"" else "Bolein: \"YouTube khol ke Arijit Singh ke gane play karo\"",
                    fontSize = 13.sp,
                    color = Color.LightGray
                )
            }
        }

        // Manual Text Command Input
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = manualTextInput,
                onValueChange = { manualTextInput = it },
                placeholder = { Text("Hindi voice command yahan type karein...", fontSize = 12.sp, color = Color.Gray) },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("command_input_field"),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryIndigo,
                    unfocusedBorderColor = Color(0xFF334155),
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (manualTextInput.isNotBlank()) {
                        viewModel.processVoiceCommand(manualTextInput)
                        manualTextInput = ""
                    }
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(PrimaryIndigo)
                    .testTag("send_command_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send",
                    tint = Color.White
                )
            }
        }

        // AI Response & Action Card
        lastDecision?.let { decision ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1B4B)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .border(1.dp, PrimaryIndigo, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Max ka Jawab:",
                            fontWeight = FontWeight.Bold,
                            color = AccentCyan,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = decision.spokenResponseHindi,
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = PrimaryIndigo,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "Action: ${decision.action}",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        if (decision.targetText.isNotBlank()) {
                            Text(
                                text = "Target: \"${decision.targetText}\"",
                                color = Color.LightGray,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        // Termux Hybrid Terminal Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = "Termux",
                            tint = AccentCyan,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Termux Hybrid Shell",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Offline List ➔ Gemini AI Shell Engine",
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Status Badge
                    Surface(
                        color = if (isTermuxInstalled) Color(0xFF065F46) else Color(0xFF374151),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (isTermuxInstalled) "Installed" else "Not Installed",
                            color = if (isTermuxInstalled) Color(0xFF6EE7B7) else Color.LightGray,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Quick Termux Command Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val termuxChips = listOf(
                        "files dikhao" to "ls -lah",
                        "storage batao" to "df -h",
                        "internet check karo" to "ping",
                        "ram check karo" to "free -h",
                        "processes dikhao" to "ps",
                        "ip address batao" to "ifconfig",
                        "date batao" to "date"
                    )
                    items(termuxChips) { (voice, cmd) ->
                        Surface(
                            color = Color(0xFF1E293B),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .clickable { viewModel.executeManualTermuxCommand(voice) }
                                .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                Text(voice, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                Text(cmd, color = AccentCyan, fontSize = 9.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Last Execution Output Box
                termuxLastExec?.let { exec ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Source: ${exec.source}",
                                color = if (exec.source == "LOCAL") AccentGreen else AccentCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Exit: ${exec.exitCode}",
                                color = if (exec.exitCode == 0) Color.LightGray else Color(0xFFEF4444),
                                fontSize = 11.sp
                            )
                        }
                        Text(
                            text = "$ ${exec.command}",
                            color = Color.White,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        val outText = exec.output.ifBlank { exec.error }.ifBlank { "Executing..." }
                        Text(
                            text = outText.take(300),
                            color = Color(0xFF94A3B8),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Action Buttons: Setup Guide & Open Termux
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showTermuxGuideDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("termux_setup_guide_button"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Setup Guide", fontSize = 12.sp)
                    }
                    Button(
                        onClick = { viewModel.openTermuxApp() },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("open_termux_button"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Open Termux", fontSize = 12.sp, color = Color.White)
                    }
                }
            }
        }

        // Quick YouTube Preset Commands
        Text(
            text = "YouTube Fast Commands",
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White
            ),
            modifier = Modifier
                .align(Alignment.Start)
                .padding(bottom = 8.dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(bottom = 20.dp)
        ) {
            items(presets) { preset ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .clickable { viewModel.processVoiceCommand(preset.command) }
                        .border(1.dp, preset.color.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = preset.icon,
                            contentDescription = null,
                            tint = preset.color,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = preset.title,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Active Reminders Section
        AnimatedVisibility(visible = activeReminders.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp)
            ) {
                Text(
                    text = "⏰ Active Reminders (${activeReminders.size})",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    ),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                activeReminders.forEach { reminder ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .border(1.dp, PrimaryIndigo.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = Icons.Default.Alarm,
                                    contentDescription = null,
                                    tint = AccentCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = reminder.title,
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "Time: ${reminder.formattedTime}",
                                        color = Color.LightGray,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            IconButton(
                                onClick = { viewModel.deleteReminder(reminder.id) }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Reminder",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showTermuxGuideDialog) {
            AlertDialog(
                onDismissRequest = { showTermuxGuideDialog = false },
                title = {
                    Text("Termux + Termux:API Setup Guide", fontWeight = FontWeight.Bold)
                },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text(
                            text = "Max app official com.termux.RUN_COMMAND intent ke zariye commands chalata hai. Setup karne ke aasan steps:",
                            fontSize = 13.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "1. F-Droid app store se 'Termux' aur 'Termux:API' install karein.\n\n" +
                                    "2. Termux open karein aur external apps access enable karein:\n" +
                                    "mkdir -p ~/.termux && echo \"allow-external-apps=true\" >> ~/.termux/termux.properties\n\n" +
                                    "3. Termux:API tool install karein:\n" +
                                    "pkg update -y && pkg install termux-api -y\n\n" +
                                    "4. Termux app ko restart karein ya exit command dekar dubara open karein.",
                            fontSize = 12.sp,
                            color = AccentCyan,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showTermuxGuideDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo)
                    ) {
                        Text("Theek Hai", color = Color.White)
                    }
                }
            )
        }
    }
}
