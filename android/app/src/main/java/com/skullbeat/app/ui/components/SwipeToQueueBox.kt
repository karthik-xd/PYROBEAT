package com.skullbeat.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skullbeat.app.ui.theme.CyanAccent
import com.skullbeat.app.ui.theme.ElectricBlue
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SwipeToQueueBox(
    onSwipeToQueue: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val maxDragPx = with(density) { 92.dp.toPx() }
    val thresholdPx = with(density) { 40.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
    ) {
        // Revealed background on left-to-right swipe
        if (dragOffset > 0f) {
            val progress = (dragOffset / maxDragPx).coerceIn(0f, 1f)
            val isThresholdReached = dragOffset >= thresholdPx

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                ElectricBlue,
                                CyanAccent.copy(alpha = 0.9f)
                            )
                        )
                    )
                    .padding(start = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.graphicsLayer {
                        alpha = (progress * 1.5f).coerceIn(0.3f, 1f)
                        scaleX = if (isThresholdReached) 1.05f else 0.92f
                        scaleY = if (isThresholdReached) 1.05f else 0.92f
                    }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = "Add to Queue",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isThresholdReached) "Release to Queue" else "Swipe to Queue",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // Foreground content sliding right
        Box(
            modifier = Modifier
                .offset { IntOffset(dragOffset.roundToInt(), 0) }
                .pointerInput(thresholdPx, maxDragPx) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val triggered = dragOffset >= thresholdPx
                            if (triggered) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSwipeToQueue()
                            }
                            val startOffset = dragOffset
                            coroutineScope.launch {
                                val anim = Animatable(startOffset)
                                anim.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                ) {
                                    dragOffset = value
                                }
                                dragOffset = 0f
                            }
                        },
                        onDragCancel = {
                            val startOffset = dragOffset
                            coroutineScope.launch {
                                val anim = Animatable(startOffset)
                                anim.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                ) {
                                    dragOffset = value
                                }
                                dragOffset = 0f
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            // Slide left-to-right only
                            if (dragOffset > 0f || dragAmount > 0f) {
                                change.consume()
                                dragOffset = (dragOffset + dragAmount).coerceIn(0f, maxDragPx)
                            }
                        }
                    )
                }
        ) {
            content()
        }
    }
}
