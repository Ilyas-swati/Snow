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
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import com.example.data.SnowPreferences
import com.example.permissions.PermissionManager
import com.example.service.SnowAccessibilityService
import com.example.service.SnowNotificationListenerService
import kotlinx.coroutines.launch

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

    var isTestingOpenAi by remember { mutableStateOf(false) }
    var openAiResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

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
                .fillMaxHeight(0.85f)
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
                        Icon(Icons.Default.Build, contentDescription = null, tint = Color(0xFF00F0FF), modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "System Diagnostics",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color(0xFF00F0FF),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Health & Connectivity Inspector",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_diagnostics_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
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

                // SECTION 2: TTS & Voice Engine Test
                Text(
                    text = "SPEECH ENGINE",
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("TTS Engine Test", color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text("Provider: ${preferences.ttsProvider} • Pitch: ${preferences.femaleVoicePitch}x", color = Color(0xFF94A3B8), fontSize = 11.sp)
                        }
                        Button(
                            onClick = onTestVoice,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color(0xFF050B14)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Play Voice", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // SECTION 3: Device Permissions Status
                Text(
                    text = "DEVICE PERMISSIONS & CAPABILITIES",
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
