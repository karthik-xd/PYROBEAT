package com.skullbeat.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skullbeat.app.ui.theme.CyanAccent
import com.skullbeat.app.ui.theme.ElectricBlue
import com.skullbeat.app.ui.theme.SurfaceDark
import com.skullbeat.app.ui.theme.VioletAccent
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

enum class SwipeAction(
    val label: String,
    val releaseLabel: String,
    val icon: ImageVector,
    val gradient: List<Color>
) {
    ADD_TO_QUEUE(
        label = "Add to queue",
        releaseLabel = "Release to add to queue",
        icon = Icons.AutoMirrored.Filled.QueueMusic,
        gradient = listOf(ElectricBlue, CyanAccent)
    ),
    PLAY_NEXT(
        label = "Play next",
        releaseLabel = "Release to play next",
        icon = Icons.Default.PlaylistPlay,
        gradient = listOf(ElectricBlue, VioletAccent)
    ),
    ADD_TO_PLAYLIST(
        label = "Add to playlist",
        releaseLabel = "Release to add to playlist",
        icon = Icons.AutoMirrored.Filled.PlaylistAdd,
        gradient = listOf(VioletAccent, CyanAccent)
    )
}

@Composable
fun SwipeToQueueBox(
    onSwipeToQueue: () -> Unit,
    modifier: Modifier = Modifier,
    startToEndAction: SwipeAction = SwipeAction.ADD_TO_QUEUE,
    endToStartAction: SwipeAction = SwipeAction.ADD_TO_QUEUE,
    onTriggerAction: ((SwipeAction) -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var hasTriggeredHaptic by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clipToBounds()
    ) {
        val totalWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val thresholdPx = totalWidthPx * 0.38f
        val maxDragPx = totalWidthPx * 0.55f

        // 1. Colored background with icon and label (rendered only when offset != 0)
        if (dragOffset != 0f) {
            val isLtr = dragOffset > 0f
            val currentAction = if (isLtr) startToEndAction else endToStartAction
            val currentAbsOffset = abs(dragOffset)
            val isThresholdReached = currentAbsOffset >= thresholdPx

            val gradientBrush = remember(currentAction, isLtr) {
                val colors = if (isLtr) currentAction.gradient else currentAction.gradient.reversed()
                Brush.horizontalGradient(colors)
            }

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(gradientBrush)
                    .padding(horizontal = 20.dp),
                contentAlignment = if (isLtr) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.graphicsLayer {
                        alpha = if (isThresholdReached) 1f else 0.45f
                    }
                ) {
                    if (!isLtr && isThresholdReached) {
                        Text(
                            text = currentAction.releaseLabel,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Icon(
                        imageVector = currentAction.icon,
                        contentDescription = currentAction.label,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )

                    if (isLtr && isThresholdReached) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = currentAction.releaseLabel,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // 2. Foreground content with 100% OPAQUE background
        Box(
            modifier = Modifier
                .offset { IntOffset(dragOffset.roundToInt(), 0) }
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceDark) // Opaque so background never shows through!
                .pointerInput(thresholdPx, maxDragPx) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val finalOffset = dragOffset
                            val reached = abs(finalOffset) >= thresholdPx
                            if (reached) {
                                val action = if (finalOffset > 0f) startToEndAction else endToStartAction
                                if (onTriggerAction != null) {
                                    onTriggerAction(action)
                                } else {
                                    onSwipeToQueue()
                                }
                            }
                            hasTriggeredHaptic = false

                            coroutineScope.launch {
                                val anim = Animatable(finalOffset)
                                anim.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                ) {
                                    dragOffset = value
                                }
                                dragOffset = 0f
                            }
                        },
                        onDragCancel = {
                            hasTriggeredHaptic = false
                            val finalOffset = dragOffset
                            coroutineScope.launch {
                                val anim = Animatable(finalOffset)
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
                            val newOffset = (dragOffset + dragAmount).coerceIn(-maxDragPx, maxDragPx)
                            if (abs(newOffset) > 2f) {
                                change.consume()
                            }
                            dragOffset = newOffset

                            val reached = abs(dragOffset) >= thresholdPx
                            if (reached && !hasTriggeredHaptic) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                hasTriggeredHaptic = true
                            } else if (!reached && hasTriggeredHaptic) {
                                hasTriggeredHaptic = false
                            }
                        }
                    )
                }
        ) {
            content()
        }
    }
}
