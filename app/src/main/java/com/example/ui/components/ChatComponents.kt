package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Verified
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.ChatMessage
import com.example.voice.VoiceState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MessageInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: (String) -> Unit,
    voiceState: VoiceState,
    onMicClick: () -> Unit,
    onCameraClick: () -> Unit,
    onInterruptClick: () -> Unit = {},
    isProcessing: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Camera vision button
        IconButton(
            onClick = onCameraClick,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(0xFF0F1A2C))
                .border(1.dp, Color(0xFF1E2F4D), CircleShape)
                .testTag("chat_camera_button")
        ) {
            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = "Camera Vision",
                tint = Color(0xFF38BDF8),
                modifier = Modifier.size(20.dp)
            )
        }

        // Multiline Text Field container
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF0B1526))
                .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.35f), RoundedCornerShape(22.dp))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp, max = 120.dp)
                        .testTag("chat_message_input"),
                    placeholder = {
                        Text(
                            text = "Message Snow...",
                            color = Color(0xFF64748B),
                            fontSize = 14.sp
                        )
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (text.isNotBlank() && !isProcessing) {
                                onSend(text)
                            }
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        cursorColor = Color(0xFF00E5FF)
                    ),
                    maxLines = 4,
                    minLines = 1
                )

                // Clear button when text is present
                if (text.isNotEmpty()) {
                    IconButton(
                        onClick = { onTextChange("") },
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("chat_clear_text_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear text",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Interrupt / Stop button when speaking or processing (Req 32)
        if (voiceState == VoiceState.SPEAKING || isProcessing) {
            Button(
                onClick = onInterruptClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                shape = RoundedCornerShape(22.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier
                    .height(44.dp)
                    .testTag("interrupt_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "Interrupt",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("STOP", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.White)
            }
        } else {
            // Voice / Microphone button
            IconButton(
                onClick = onMicClick,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (voiceState == VoiceState.LISTENING) Color(0xFF00E5FF)
                        else Color(0xFF0F1A2C)
                    )
                    .border(
                        1.dp,
                        if (voiceState == VoiceState.LISTENING) Color(0xFF00F0FF) else Color(0xFF1E2F4D),
                        CircleShape
                    )
                    .testTag("chat_mic_button")
            ) {
                Icon(
                    imageVector = if (voiceState == VoiceState.LISTENING) Icons.Default.GraphicEq else Icons.Default.Mic,
                    contentDescription = "Voice input",
                    tint = if (voiceState == VoiceState.LISTENING) Color(0xFF050B14) else Color(0xFF38BDF8),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Send button
        val canSend = text.isNotBlank() && !isProcessing
        IconButton(
            onClick = {
                if (canSend) {
                    onSend(text)
                }
            },
            enabled = canSend,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    if (canSend) Brush.horizontalGradient(listOf(Color(0xFF00E5FF), Color(0xFF0284C7)))
                    else Brush.horizontalGradient(listOf(Color(0xFF1E293B), Color(0xFF1E293B)))
                )
                .testTag("chat_send_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send message",
                tint = if (canSend) Color(0xFF050B14) else Color(0xFF64748B),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun ChatMessageBubble(
    message: ChatMessage,
    onDelete: (Long) -> Unit,
    onSaveImage: ((String) -> Unit)? = null,
    onShareImage: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isUser = message.sender.equals("user", ignoreCase = true)
    val context = LocalContext.current
    val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(message.timestamp))

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isUser) {
            // Snow avatar icon
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF00E5FF).copy(alpha = 0.2f))
                    .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("❄", fontSize = 14.sp)
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Card(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isUser) 16.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 16.dp
                ),
                colors = CardDefaults.cardColors(
                    containerColor = if (isUser) Color(0xFF0D3256) else Color(0xFF0C192E).copy(alpha = 0.9f)
                ),
                modifier = Modifier
                    .border(
                        1.dp,
                        if (isUser) Color(0xFF00E5FF).copy(alpha = 0.4f) else Color(0xFF1E3A5F),
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isUser) 16.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 16.dp
                        )
                    )
                    .testTag("chat_bubble_${message.id}")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Tool / Action execution badge if tools were used
                    if (!isUser && !message.actionSummary.isNullOrBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(bottom = 6.dp)
                                .background(Color(0xFF00E5FF).copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = "Verified action",
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = message.actionSummary,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF7DD3FC),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        lineHeight = 20.sp
                    )

                    // Generated Image Display (Req 29)
                    if (!message.imageUri.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 160.dp, max = 240.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF070E1A))
                                .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                        ) {
                            AsyncImage(
                                model = message.imageUri,
                                contentDescription = "Generated image",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onSaveImage?.invoke(message.imageUri) },
                                modifier = Modifier.weight(1f).height(34.dp).testTag("save_image_btn_${message.id}"),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.6f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00E5FF))
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Save", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }

                            OutlinedButton(
                                onClick = { onShareImage?.invoke(message.imageUri) },
                                modifier = Modifier.weight(1f).height(34.dp).testTag("share_image_btn_${message.id}"),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.6f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8))
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Share", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = timeStr,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF64748B),
                            fontSize = 10.sp
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Copy button
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Snow Message", message.content))
                                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(24.dp).testTag("copy_message_${message.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy message",
                                    tint = Color(0xFF64748B),
                                    modifier = Modifier.size(12.dp)
                                )
                            }

                            // Delete button
                            IconButton(
                                onClick = { onDelete(message.id) },
                                modifier = Modifier.size(24.dp).testTag("delete_message_${message.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete message",
                                    tint = Color(0xFF64748B),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ThinkingBubble(
    status: String,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(Color(0xFF00E5FF).copy(alpha = 0.2f))
                .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("❄", fontSize = 14.sp)
        }
        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF0C192E))
                .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .testTag("thinking_bubble")
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF00E5FF))
                        .alpha(alphaAnim)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (status.isNotBlank()) status else "Thinking…",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF38BDF8),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatSuggestionsRow(
    onSuggestionSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val suggestions = listOf(
        "What's the weather?",
        "WhatsApp Ali",
        "Search the web",
        "Set a reminder",
        "Open YouTube",
        "Create Snow folder"
    )

    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        suggestions.forEach { prompt ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0F1A2C))
                    .border(1.dp, Color(0xFF1E2F4D), RoundedCornerShape(16.dp))
                    .clickable { onSuggestionSelected(prompt) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag("suggestion_chip_$prompt")
            ) {
                Text(
                    text = prompt,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF7DD3FC),
                    fontSize = 11.sp
                )
            }
        }
    }
}
