package com.example.ui.components

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.SnowPreferences
import com.example.service.SnowAccessibilityService
import com.example.service.SnowVoiceService

@Composable
fun ConfigDialog(
    preferences: SnowPreferences,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onTestVoice: (pitch: Float, rate: Float, lang: String) -> Unit
) {
    val context = LocalContext.current
    var apiKeyText by remember { mutableStateOf(preferences.customApiKey) }
    var showApiKey by remember { mutableStateOf(false) }
    var selectedModel by remember { mutableStateOf(preferences.apiEndpointModel) }
    var selectedLang by remember { mutableStateOf(preferences.languageMode) }
    var pitchValue by remember { mutableFloatStateOf(preferences.femaleVoicePitch) }
    var rateValue by remember { mutableFloatStateOf(preferences.speechRate) }
    var wakeWordEnabled by remember { mutableStateOf(preferences.wakeWordEnabled) }
    var continuousListen by remember { mutableStateOf(preferences.continuousListening) }
    var bgServiceEnabled by remember { mutableStateOf(preferences.backgroundServiceEnabled) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 16.dp)
                .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                .testTag("config_dialog_card"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF0A1120)
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Title Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Snow AI Settings",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color(0xFF00F0FF),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "AI Voice & Device Configuration",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8)
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_config_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF94A3B8)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color(0xFF1E293B))
                Spacer(modifier = Modifier.height(16.dp))

                // 1. API Key Section
                Text(
                    text = "AI Service API Key",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Uses project BuildConfig key by default, or paste a custom Gemini API key below:",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                OutlinedTextField(
                    value = apiKeyText,
                    onValueChange = { apiKeyText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("api_key_input"),
                    placeholder = { Text("Leave blank to use default BuildConfig key", color = Color(0xFF64748B)) },
                    leadingIcon = {
                        Icon(Icons.Default.Key, contentDescription = null, tint = Color(0xFF00E5FF))
                    },
                    trailingIcon = {
                        IconButton(onClick = { showApiKey = !showApiKey }) {
                            Icon(
                                imageVector = if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showApiKey) "Hide key" else "Show key",
                                tint = Color(0xFF94A3B8)
                            )
                        }
                    },
                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00E5FF),
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF070D18),
                        unfocusedContainerColor = Color(0xFF070D18)
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 2. API Endpoint / Model
                Text(
                    text = "AI Model Engine",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("gemini-3.5-flash", "gemini-3.1-pro-preview").forEach { model ->
                        FilterChip(
                            selected = selectedModel == model,
                            onClick = { selectedModel = model },
                            label = { Text(model, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF00E5FF).copy(alpha = 0.2f),
                                selectedLabelColor = Color(0xFF00F0FF),
                                containerColor = Color(0xFF0F172A),
                                labelColor = Color(0xFF94A3B8)
                            ),
                            modifier = Modifier.testTag("model_chip_$model")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 3. Language Selection
                Text(
                    text = "Language Support",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val langs = listOf(
                        SnowPreferences.LANG_AUTO to "Auto",
                        SnowPreferences.LANG_EN to "English",
                        SnowPreferences.LANG_UR to "اردو (Urdu)",
                        SnowPreferences.LANG_PS to "پښتو (Pashto)"
                    )
                    langs.forEach { (code, label) ->
                        FilterChip(
                            selected = selectedLang == code,
                            onClick = { selectedLang = code },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF00E5FF).copy(alpha = 0.25f),
                                selectedLabelColor = Color(0xFF00F0FF),
                                containerColor = Color(0xFF0F172A),
                                labelColor = Color(0xFF94A3B8)
                            ),
                            modifier = Modifier.testTag("lang_chip_$code")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 4. Female Voice Tuning
                Text(
                    text = "Female Voice Settings",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Vocal Pitch (Female tone): ${"%.2f".format(pitchValue)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8)
                    )
                }
                Slider(
                    value = pitchValue,
                    onValueChange = { pitchValue = it },
                    valueRange = 0.8f..1.6f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF00F0FF),
                        activeTrackColor = Color(0xFF00E5FF),
                        inactiveTrackColor = Color(0xFF1E293B)
                    ),
                    modifier = Modifier.testTag("voice_pitch_slider")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Speech Rate: ${"%.2f".format(rateValue)}x",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8)
                    )
                }
                Slider(
                    value = rateValue,
                    onValueChange = { rateValue = it },
                    valueRange = 0.7f..1.5f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF00F0FF),
                        activeTrackColor = Color(0xFF00E5FF),
                        inactiveTrackColor = Color(0xFF1E293B)
                    ),
                    modifier = Modifier.testTag("speech_rate_slider")
                )

                OutlinedButton(
                    onClick = { onTestVoice(pitchValue, rateValue, selectedLang) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .testTag("test_voice_button"),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00F0FF)),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF00E5FF)))
                ) {
                    Icon(Icons.Default.RecordVoiceOver, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Test Female Voice")
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 5. Wake Word & Continuous Listening
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "\"Snow\" Wake Word",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Say \"Snow\" or \"Hey Snow\" anytime",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8)
                        )
                    }
                    Switch(
                        checked = wakeWordEnabled,
                        onCheckedChange = { wakeWordEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF00E5FF),
                            uncheckedTrackColor = Color(0xFF1E293B)
                        ),
                        modifier = Modifier.testTag("wake_word_switch")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Continuous Listening",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Auto resumes listening after speaking",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8)
                        )
                    }
                    Switch(
                        checked = continuousListen,
                        onCheckedChange = { continuousListen = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF00E5FF),
                            uncheckedTrackColor = Color(0xFF1E293B)
                        ),
                        modifier = Modifier.testTag("continuous_listening_switch")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Background Voice Service",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Keep listening when app is minimized",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8)
                        )
                    }
                    Switch(
                        checked = bgServiceEnabled,
                        onCheckedChange = { bgServiceEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF00E5FF),
                            uncheckedTrackColor = Color(0xFF1E293B)
                        ),
                        modifier = Modifier.testTag("bg_service_switch")
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 6. WhatsApp & Accessibility Integration
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
                            Text(
                                text = "WhatsApp & Device Automation",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                            val isRunning = SnowAccessibilityService.isServiceRunning
                            Text(
                                text = if (isRunning) "Accessibility Service: ACTIVE" else "Enable Accessibility in Android Settings to auto-type and send WhatsApp messages",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isRunning) Color(0xFF34D399) else Color(0xFFF59E0B)
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00E5FF)),
                            modifier = Modifier.testTag("open_accessibility_settings_button")
                        ) {
                            Icon(Icons.Default.Accessibility, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Setup", fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Bottom Action Buttons: Save / Cancel
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("cancel_config_button"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF94A3B8))
                    ) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            preferences.customApiKey = apiKeyText
                            preferences.apiEndpointModel = selectedModel
                            preferences.languageMode = selectedLang
                            preferences.femaleVoicePitch = pitchValue
                            preferences.speechRate = rateValue
                            preferences.wakeWordEnabled = wakeWordEnabled
                            preferences.continuousListening = continuousListen
                            preferences.backgroundServiceEnabled = bgServiceEnabled

                            if (bgServiceEnabled) {
                                SnowVoiceService.start(context)
                            } else {
                                SnowVoiceService.stop(context)
                            }

                            onSave()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00E5FF),
                            contentColor = Color(0xFF050B14)
                        ),
                        modifier = Modifier.testTag("save_config_button")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save Settings", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
