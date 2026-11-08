package com.skullbeat.app.ui.screens.splash

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skullbeat.app.R
import com.skullbeat.app.ui.theme.BgDark
import com.skullbeat.app.ui.theme.ElectricBlue
import com.skullbeat.app.ui.theme.FredokaFontFamily
import com.skullbeat.app.ui.theme.SurfaceDark
import com.skullbeat.app.ui.theme.TextBone
import com.skullbeat.app.ui.theme.TextMuted
import com.skullbeat.app.ui.theme.VioletAccent

@Composable
fun SplashScreen(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "SplashAnimations")

    // Pulsing scale for logo
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "LogoScale"
    )

    // Glow elevation animation
    val glowElevation by infiniteTransition.animateFloat(
        initialValue = 18f,
        targetValue = 38f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "LogoGlow"
    )

    // Animated Heights for Beat Wave Equalizer
    val bar1 by infiniteTransition.animateFloat(
        initialValue = 6f, targetValue = 26f,
        animationSpec = infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse),
        label = "Bar1"
    )
    val bar2 by infiniteTransition.animateFloat(
        initialValue = 14f, targetValue = 34f,
        animationSpec = infiniteRepeatable(tween(560, easing = LinearEasing), RepeatMode.Reverse),
        label = "Bar2"
    )
    val bar3 by infiniteTransition.animateFloat(
        initialValue = 8f, targetValue = 30f,
        animationSpec = infiniteRepeatable(tween(380, easing = LinearEasing), RepeatMode.Reverse),
        label = "Bar3"
    )
    val bar4 by infiniteTransition.animateFloat(
        initialValue = 16f, targetValue = 32f,
        animationSpec = infiniteRepeatable(tween(480, easing = LinearEasing), RepeatMode.Reverse),
        label = "Bar4"
    )
    val bar5 by infiniteTransition.animateFloat(
        initialValue = 6f, targetValue = 22f,
        animationSpec = infiniteRepeatable(tween(520, easing = LinearEasing), RepeatMode.Reverse),
        label = "Bar5"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BgDark),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Glowing Skull Logo Card
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .scale(scale)
                    .shadow(
                        elevation = glowElevation.dp,
                        shape = CircleShape,
                        spotColor = ElectricBlue,
                        ambientColor = VioletAccent
                    )
                    .clip(CircleShape)
                    .background(SurfaceDark),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logo),
                    contentDescription = "SkullBeat Logo",
                    modifier = Modifier.size(92.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // App Name in Fredoka Font
            Text(
                text = "SkullBeat",
                fontFamily = FredokaFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 38.sp,
                color = ElectricBlue,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Subtitle
            Text(
                text = "FEEL THE BASS • HEAR THE BEAT",
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    letterSpacing = 3.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = TextMuted
            )

            Spacer(modifier = Modifier.height(42.dp))

            // Animated Equalizer Beats Wave
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.height(36.dp)
            ) {
                EqualizerBar(height = bar1, color = ElectricBlue)
                EqualizerBar(height = bar2, color = VioletAccent)
                EqualizerBar(height = bar3, color = ElectricBlue)
                EqualizerBar(height = bar4, color = VioletAccent)
                EqualizerBar(height = bar5, color = ElectricBlue)
            }
        }
    }
}

@Composable
private fun EqualizerBar(
    height: Float,
    color: androidx.compose.ui.graphics.Color
) {
    Box(
        modifier = Modifier
            .width(4.dp)
            .height(height.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(color)
    )
}
