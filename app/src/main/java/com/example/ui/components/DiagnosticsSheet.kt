package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.agent.ToolExecutor
import com.example.data.SnowPreferences
import com.example.permissions.PermissionManager
import com.example.service.SnowAccessibilityService
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DiagnosticsSheet(
    preferences: SnowPreferences,
    permissionManager: PermissionManager,
    onDismiss: () -> Unit,
    onTestProvider: suspend (String) -> Pair<Boolean, String>,
    onTestVoice: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isTestingGemini by remember { mutableStateOf(false) }
    var geminiResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    var isTestingOllama by remember { mutableStateOf(false) }
    var ollamaResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    var isTestingOpenAi by remember { mutableStateOf(false) }
    var openAiResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    var executionLogsVersion by remember { mutableStateOf(0) }

    val hasMic = permissionManager.hasRecordAudio()
    val hasContacts = permissionManager.hasContacts()
    val hasLocation = permissionManager.hasLocation()
    val hasNotifications = permissionManager.hasPostNotifications()
    val hasNotificationListener = permissionManager.isNotificationListenerEnabled()
    val hasAccessibility = permissionManager.isAccessibilityServiceEnabled()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f)
                .padding(vertical = 12.dp)
                .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                .testTag("diagnostics_sheet_card"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF070D1A))
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Build,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Agent Diagnostics & Logs",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // SECTION 1: AI Provider Connectivity
                Text(
                    text = "AI PROVIDER HEALTH",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF00E5FF),
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Gemini Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Google Gemini", color = Color.White, fontWeight = FontWeight.SemiBold)
                            val keySet = preferences.customApiKey.isNotBlank()
                            Text(
                                if (keySet) "Model: ${preferences.apiEndpointModel} (Configured)" else "Default Server Key",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                            if (geminiResult != null) {
                                Text(
                                    geminiResult!!.second,
                                    color = if (geminiResult!!.first) Color(0xFF10B981) else Color(0xFFEF4444),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    isTestingGemini = true
                                    geminiResult = onTestProvider("GEMINI")
                                    isTestingGemini = false
                                }
                            },
                            enabled = !isTestingGemini,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color(0xFF050B14)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (isTestingGemini) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color(0xFF050B14), strokeWidth = 2.dp)
                            } else {
                                Text("Test", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Ollama Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Ollama Server", color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text(
                                "URL: ${preferences.ollamaBaseUrl} (${preferences.ollamaModel})",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                            if (ollamaResult != null) {
                                Text(
                                    ollamaResult!!.second,
                                    color = if (ollamaResult!!.first) Color(0xFF10B981) else Color(0xFFEF4444),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    isTestingOllama = true
                                    ollamaResult = onTestProvider("OLLAMA")
                                    isTestingOllama = false
                                }
                            },
                            enabled = !isTestingOllama,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color(0xFF050B14)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (isTestingOllama) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color(0xFF050B14), strokeWidth = 2.dp)
                            } else {
                                Text("Test", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // OpenAI Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("OpenAI (GPT-4o)", color = Color.White, fontWeight = FontWeight.SemiBold)
                            val hasKey = preferences.openAiApiKey.isNotBlank()
                            Text(
                                if (hasKey) "Key configured" else "No key configured",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                            if (openAiResult != null) {
                                Text(
                                    openAiResult!!.second,
                                    color = if (openAiResult!!.first) Color(0xFF10B981) else Color(0xFFEF4444),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    isTestingOpenAi = true
                                    openAiResult = onTestProvider("OPENAI")
                                    isTestingOpenAi = false
                                }
                            },
                            enabled = !isTestingOpenAi,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color(0xFF050B14)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (isTestingOpenAi) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color(0xFF050B14), strokeWidth = 2.dp)
                            } else {
                                Text("Test", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // SECTION 2: Agent Action Execution Logs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "AGENT ACTION EXECUTION LOGS",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF00E5FF),
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = {
                            ToolExecutor.executionLogs.clear()
                            executionLogsVersion++
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear logs", tint = Color(0xFF64748B), modifier = Modifier.size(16.dp))
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))

                val logs = ToolExecutor.executionLogs.toList()
                if (logs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Text("No agent actions executed yet. Say or type a command like 'Send WhatsApp to Ali' or 'Create Snow folder' to see verified action logs.", color = Color(0xFF64748B), fontSize = 11.sp)
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        logs.reversed().forEach { log ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0B1526)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = log.actionType,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF38BDF8),
                                            fontSize = 12.sp
                                        )
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    if (log.isSuccess) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFEF4444).copy(alpha = 0.2f),
                                                    RoundedCornerShape(4.dp)
                                                )
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = if (log.isSuccess) "VERIFIED / SUCCESS" else "FAILED",
                                                color = if (log.isSuccess) Color(0xFF10B981) else Color(0xFFEF4444),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Text(
                                        text = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp)) + " | Target: " + log.targetOrDetails,
                                        fontSize = 10.sp,
                                        color = Color(0xFF64748B)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = log.verificationSummary,
                                        fontSize = 11.sp,
                                        color = Color(0xFFE2E8F0),
                                        maxLines = 4
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // SECTION 3: Accessibility Live Monitor
                Text(
                    text = "ACCESSIBILITY REAL-TIME MONITOR",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF00E5FF),
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Service Connected:", color = Color(0xFF94A3B8), fontSize = 12.sp)
                            Text(
                                if (SnowAccessibilityService.isServiceRunning) "YES (Active)" else "NO (Disabled in Settings)",
                                color = if (SnowAccessibilityService.isServiceRunning) Color(0xFF10B981) else Color(0xFFEF4444),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Foreground Package:", color = Color(0xFF94A3B8), fontSize = 12.sp)
                            Text(
                                SnowAccessibilityService.currentForegroundPackage.ifBlank { "None detected" },
                                color = Color(0xFF00E5FF),
                                fontSize = 12.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Last Window Event:", color = Color(0xFF94A3B8), fontSize = 12.sp)
                            Text(
                                SnowAccessibilityService.lastEventTypeString,
                                color = Color.White,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // SECTION 4: System Permissions Health
                Text(
                    text = "SYSTEM PERMISSIONS HEALTH",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF00E5FF),
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DiagnosticItem(
                        name = "Microphone (Speech)",
                        isGranted = hasMic,
                        onAction = { permissionManager.openAppSettings() }
                    )
                    DiagnosticItem(
                        name = "Contacts Search",
                        isGranted = hasContacts,
                        onAction = { permissionManager.openAppSettings() }
                    )
                    DiagnosticItem(
                        name = "Location Services",
                        isGranted = hasLocation,
                        onAction = { permissionManager.openAppSettings() }
                    )
                    DiagnosticItem(
                        name = "Notifications",
                        isGranted = hasNotifications,
                        onAction = { permissionManager.openAppSettings() }
                    )
                    DiagnosticItem(
                        name = "Notification Listener",
                        isGranted = hasNotificationListener,
                        onAction = { permissionManager.openNotificationListenerSettings() }
                    )
                    DiagnosticItem(
                        name = "Screen Control (Accessibility)",
                        isGranted = hasAccessibility,
                        onAction = { permissionManager.openAccessibilitySettings() }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = { permissionManager.openAppSettings() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Open Android App Settings", color = Color(0xFF00E5FF))
                }
            }
        }
    }
}

@Composable
private fun DiagnosticItem(
    name: String,
    isGranted: Boolean,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isGranted) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = if (isGranted) Color(0xFF10B981) else Color(0xFFEF4444),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(name, color = Color.White, fontSize = 13.sp)
            }
            if (!isGranted) {
                Text(
                    text = "Grant",
                    color = Color(0xFF00E5FF),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .background(Color(0xFF00E5FF).copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            } else {
                Text("Active", color = Color(0xFF10B981), fontSize = 12.sp)
            }
        }
    }
}
