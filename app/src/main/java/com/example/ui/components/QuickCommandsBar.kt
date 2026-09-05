package com.example.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class QuickCommand(
    val label: String,
    val prompt: String,
    val icon: ImageVector? = null
)

@Composable
fun QuickCommandsBar(
    onCommandSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val commands = listOf(
        QuickCommand("Roman Urdu: Kal uthana", "Snow kal mujhe 8 baje uthana", Icons.Default.Language),
        QuickCommand("Roman Urdu: Weather", "Snow mujhe batao weather kaisa hai", Icons.Default.Language),
        QuickCommand("اردو: یہ کام کر دو", "Snow یہ کام کر دو", Icons.Default.Language),
        QuickCommand("پښتو خبرې", "Snow ma sara Pukhto ke khabara kawa", Icons.Default.Language),
        QuickCommand("Hindi: नमस्ते स्नो", "नमस्ते स्नो, आप कैसी हैं?", Icons.Default.Language),
        QuickCommand("Open WhatsApp", "Open WhatsApp application", Icons.AutoMirrored.Filled.Message),
        QuickCommand("Flashlight On", "Turn on device flashlight", Icons.Default.FlashlightOn),
        QuickCommand("Volume Up", "Turn up music volume", Icons.AutoMirrored.Filled.VolumeUp),
        QuickCommand("What can you do?", "Snow, what are your full capabilities as an AI voice agent?")
    )

    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        commands.forEach { cmd ->
            AssistChip(
                onClick = { onCommandSelected(cmd.prompt) },
                label = { Text(cmd.label, fontSize = 12.sp, color = Color.White) },
                leadingIcon = {
                    cmd.icon?.let {
                        Icon(it, contentDescription = null, tint = Color(0xFF00E5FF))
                    }
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = Color(0xFF0F172A).copy(alpha = 0.8f)
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    Color(0xFF00E5FF).copy(alpha = 0.35f)
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.testTag("quick_command_${cmd.label}")
            )
        }
    }
}
