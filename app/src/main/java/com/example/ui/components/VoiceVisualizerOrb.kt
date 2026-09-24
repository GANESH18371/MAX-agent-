package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.PrimaryIndigo
import com.example.ui.theme.SecondaryViolet
import com.example.ui.theme.SurfaceDark

@Composable
fun VoiceVisualizerOrb(
    isListening: Boolean,
    isSpeaking: Boolean,
    isProcessing: Boolean,
    amplitude: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val currentAmplitudeScale = if (isListening) {
        1f + (amplitude * 0.4f)
    } else if (isSpeaking) {
        pulseScale
    } else 1f

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.testTag("voice_orb_button")
    ) {
        // Outer pulsing rings when active
        if (isListening || isSpeaking || isProcessing) {
            Canvas(
                modifier = Modifier
                    .size(160.dp)
                    .scale(currentAmplitudeScale)
            ) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            PrimaryIndigo.copy(alpha = 0.4f),
                            SecondaryViolet.copy(alpha = 0.2f),
                            Color.Transparent
                        )
                    )
                )
                drawCircle(
                    color = AccentCyan.copy(alpha = 0.6f),
                    style = Stroke(width = 3.dp.toPx()),
                    radius = (size.minDimension / 2f) * pulseScale * 0.85f
                )
            }
        }

        // Inner glowing core button
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(110.dp)
                .scale(if (isListening) currentAmplitudeScale else 1f)
                .clip(CircleShape)
                .background(
                    brush = when {
                        isProcessing -> Brush.linearGradient(listOf(SecondaryViolet, AccentCyan))
                        isSpeaking -> Brush.linearGradient(listOf(AccentCyan, PrimaryIndigo))
                        isListening -> Brush.linearGradient(listOf(PrimaryIndigo, SecondaryViolet))
                        else -> Brush.linearGradient(listOf(PrimaryIndigo.copy(alpha = 0.9f), SurfaceDark))
                    }
                )
                .clickable { onClick() }
        ) {
            Icon(
                imageVector = when {
                    isListening -> Icons.Default.Stop
                    isSpeaking -> Icons.Default.GraphicEq
                    else -> Icons.Default.Mic
                },
                contentDescription = "Mic Voice Command",
                tint = Color.White,
                modifier = Modifier.size(48.dp)
            )
        }
    }
}
