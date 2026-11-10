package com.skullbeat.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.skullbeat.app.ui.theme.BgDark
import com.skullbeat.app.ui.theme.PrimaryGradient
import com.skullbeat.app.ui.theme.TextBone

@Composable
fun SkullPlayButton(
    isPlaying: Boolean,
    isLoading: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp
) {
    val scale by animateFloatAsState(if (isPlaying || isLoading) 1.05f else 1.0f, label = "button_scale")

    val infiniteTransition = rememberInfiniteTransition(label = "skull_spin_transition")
    val spinRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "skull_rotation"
    )

    Box(
        modifier = modifier
            .size(size)
            .scale(scale)
            .clip(CircleShape)
            .background(PrimaryGradient)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(bounded = true, color = Color.White),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .size(size * 0.65f)
                .graphicsLayer {
                    rotationZ = if (isLoading) spinRotation else 0f
                }
        ) {
            val w = this.size.width
            val h = this.size.height

            if (!isPlaying || isLoading) {
                // Skull Play Icon (Optically centered cranium + jaw, hollow eyes)
                val skullPath = Path().apply {
                    // Cranium dome
                    moveTo(w * 0.2f, h * 0.45f)
                    cubicTo(w * 0.15f, h * 0.15f, w * 0.85f, h * 0.15f, w * 0.8f, h * 0.45f)
                    // Cheekbone curve
                    cubicTo(w * 0.85f, h * 0.65f, w * 0.7f, h * 0.7f, w * 0.65f, h * 0.85f)
                    // Teeth / jaw line
                    lineTo(w * 0.35f, h * 0.85f)
                    cubicTo(w * 0.3f, h * 0.7f, w * 0.15f, h * 0.65f, w * 0.2f, h * 0.45f)
                    close()
                }
                drawPath(path = skullPath, color = TextBone)

                // Left & right eye sockets (hollow dark)
                drawCircle(
                    color = BgDark,
                    radius = w * 0.09f,
                    center = Offset(w * 0.38f, h * 0.45f)
                )
                drawCircle(
                    color = BgDark,
                    radius = w * 0.09f,
                    center = Offset(w * 0.62f, h * 0.45f)
                )

                // Inverted nose cavity
                val nosePath = Path().apply {
                    moveTo(w * 0.5f, h * 0.55f)
                    lineTo(w * 0.45f, h * 0.63f)
                    lineTo(w * 0.55f, h * 0.63f)
                    close()
                }
                drawPath(path = nosePath, color = BgDark)

                // Teeth notches
                drawRoundRect(
                    color = BgDark,
                    topLeft = Offset(w * 0.42f, h * 0.78f),
                    size = Size(w * 0.04f, h * 0.07f),
                    cornerRadius = CornerRadius(2f, 2f)
                )
                drawRoundRect(
                    color = BgDark,
                    topLeft = Offset(w * 0.54f, h * 0.78f),
                    size = Size(w * 0.04f, h * 0.07f),
                    cornerRadius = CornerRadius(2f, 2f)
                )
            } else {
                // Skull Pause State: skull cranium with two vertical pause bars replacing jaw
                val skullCraniumPath = Path().apply {
                    moveTo(w * 0.2f, h * 0.45f)
                    cubicTo(w * 0.15f, h * 0.15f, w * 0.85f, h * 0.15f, w * 0.8f, h * 0.45f)
                    cubicTo(w * 0.85f, h * 0.62f, w * 0.72f, h * 0.66f, w * 0.68f, h * 0.70f)
                    lineTo(w * 0.32f, h * 0.70f)
                    cubicTo(w * 0.28f, h * 0.66f, w * 0.15f, h * 0.62f, w * 0.2f, h * 0.45f)
                    close()
                }
                drawPath(path = skullCraniumPath, color = TextBone)

                // Eye sockets
                drawCircle(
                    color = BgDark,
                    radius = w * 0.09f,
                    center = Offset(w * 0.38f, h * 0.45f)
                )
                drawCircle(
                    color = BgDark,
                    radius = w * 0.09f,
                    center = Offset(w * 0.62f, h * 0.45f)
                )

                // Inverted nose
                val nosePath = Path().apply {
                    moveTo(w * 0.5f, h * 0.53f)
                    lineTo(w * 0.45f, h * 0.61f)
                    lineTo(w * 0.55f, h * 0.61f)
                    close()
                }
                drawPath(path = nosePath, color = BgDark)

                // Two vertical pause bars replacing jaw
                val barW = w * 0.11f
                val barH = h * 0.24f
                val barCorner = CornerRadius(barW * 0.45f, barW * 0.45f)

                drawRoundRect(
                    color = TextBone,
                    topLeft = Offset(w * 0.35f, h * 0.73f),
                    size = Size(barW, barH),
                    cornerRadius = barCorner
                )
                drawRoundRect(
                    color = TextBone,
                    topLeft = Offset(w * 0.54f, h * 0.73f),
                    size = Size(barW, barH),
                    cornerRadius = barCorner
                )
            }
        }
    }
}
