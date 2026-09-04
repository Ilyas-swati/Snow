package com.example.ui.orb

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.voice.VoiceState
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun VoiceOrb(
    voiceState: VoiceState,
    rmsAmplitude: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 260.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orbAnimations")

    // Breathing pulse for idle
    val idlePulse by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idlePulse"
    )

    // Fast rotation for thinking state
    val thinkingRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "thinkingRotation"
    )

    // Speaking oscillation waves
    val speakingWave by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "speakingWave"
    )

    // Outer glow expand animation
    val glowExpansion by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowExpansion"
    )

    // Calculate dynamic scaling from microphone decibels
    val voiceScale = when (voiceState) {
        VoiceState.LISTENING -> 1f + (rmsAmplitude.coerceIn(0f, 12f) / 18f)
        VoiceState.SPEAKING -> speakingWave
        VoiceState.THINKING -> 1.03f
        VoiceState.ERROR -> 0.98f
        VoiceState.IDLE -> idlePulse
    }

    Box(
        modifier = modifier
            .size(size)
            .testTag("voice_orb_container")
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = size / 2),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val centerOffset = Offset(size.toPx() / 2f, size.toPx() / 2f)
            val baseRadius = (size.toPx() / 2f) * 0.52f
            val scaledRadius = baseRadius * voiceScale

            // Dynamic color palette based on voice state
            val coreColors = when (voiceState) {
                VoiceState.LISTENING -> listOf(
                    Color(0xFFFFFFFF),
                    Color(0xFF80F9FF),
                    Color(0xFF00E5FF),
                    Color(0xFF0288D1),
                    Color(0x000288D1)
                )
                VoiceState.THINKING -> listOf(
                    Color(0xFFFFFFFF),
                    Color(0xFFD8B4FE),
                    Color(0xFFA855F7),
                    Color(0xFF6366F1),
                    Color(0x004F46E5)
                )
                VoiceState.SPEAKING -> listOf(
                    Color(0xFFFFFFFF),
                    Color(0xFFB2EBF2),
                    Color(0xFF00F0FF),
                    Color(0xFF00B0FF),
                    Color(0x000091EA)
                )
                VoiceState.ERROR -> listOf(
                    Color(0xFFFFFFFF),
                    Color(0xFFFCA5A5),
                    Color(0xFFEF4444),
                    Color(0xFFB91C1C),
                    Color(0x00B91C1C)
                )
                VoiceState.IDLE -> listOf(
                    Color(0xFFFFFFFF),
                    Color(0xFFB0ECFD),
                    Color(0xFF38BDF8),
                    Color(0xFF0284C7),
                    Color(0x000369A1)
                )
            }

            // Layer 1: Ambient outer nebula glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        coreColors[2].copy(alpha = 0.35f),
                        coreColors[3].copy(alpha = 0.15f),
                        Color.Transparent
                    ),
                    center = centerOffset,
                    radius = scaledRadius * 1.8f * glowExpansion
                ),
                radius = scaledRadius * 1.8f * glowExpansion,
                center = centerOffset
            )

            // Layer 2: Concentric sound wave ripples when listening or speaking
            if (voiceState == VoiceState.LISTENING || voiceState == VoiceState.SPEAKING) {
                val ringCount = 3
                for (i in 1..ringCount) {
                    val ringRadius = scaledRadius + (i * 18f * voiceScale)
                    val alpha = (0.6f / i) * (if (voiceState == VoiceState.LISTENING) (rmsAmplitude / 10f).coerceIn(0.3f, 1f) else 0.8f)
                    drawCircle(
                        color = coreColors[2].copy(alpha = alpha),
                        radius = ringRadius,
                        center = centerOffset,
                        style = Stroke(width = 2.5f)
                    )
                }
            }

            // Layer 3: Orbiting celestial particles in Thinking state
            if (voiceState == VoiceState.THINKING) {
                val particleCount = 8
                for (i in 0 until particleCount) {
                    val angle = Math.toRadians((thinkingRotation + (i * 360f / particleCount)).toDouble())
                    val particleDist = scaledRadius * 1.35f
                    val px = centerOffset.x + (particleDist * cos(angle)).toFloat()
                    val py = centerOffset.y + (particleDist * sin(angle)).toFloat()
                    drawCircle(
                        color = if (i % 2 == 0) Color(0xFFE9D5FF) else Color(0xFF67E8F9),
                        radius = 4f,
                        center = Offset(px, py)
                    )
                }
            }

            // Layer 4: Main glowing orb core
            drawCircle(
                brush = Brush.radialGradient(
                    colors = coreColors,
                    center = centerOffset,
                    radius = scaledRadius
                ),
                radius = scaledRadius,
                center = centerOffset
            )

            // Layer 5: Concentric inner refraction ring
            drawCircle(
                color = Color.White.copy(alpha = 0.45f),
                radius = scaledRadius * 0.78f,
                center = centerOffset,
                style = Stroke(width = 1.5f)
            )

            // Layer 6: Bright crystalline light specular highlight
            drawCircle(
                color = Color.White.copy(alpha = 0.85f),
                radius = scaledRadius * 0.28f,
                center = Offset(centerOffset.x - (scaledRadius * 0.22f), centerOffset.y - (scaledRadius * 0.24f))
            )
        }
    }
}
