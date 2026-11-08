package com.skullbeat.app.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import com.skullbeat.app.R
import com.skullbeat.app.ui.theme.BgDark
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

@Composable
fun BeatWaveView(
    isPlaying: Boolean,
    currentPositionMs: Long,
    volume: Float = 1.0f,
    modifier: Modifier = Modifier
        .width(168.dp)
        .height(34.dp)
) {
    val context = LocalContext.current
    var waveEnergy by remember { mutableFloatStateOf(0.2f) }
    var wallClockTime by remember { mutableFloatStateOf(0f) }

    val orbitronTypeface = remember {
        try {
            ResourcesCompat.getFont(context, R.font.orbitron_extrabold)
        } catch (_: Exception) {
            Typeface.DEFAULT_BOLD
        }
    }

    LaunchedEffect(Unit) {
        val startNano = System.nanoTime()
        while (true) {
            withFrameNanos { nowNano ->
                wallClockTime = (nowNano - startNano) / 1_000_000_000f
            }
        }
    }

    Canvas(modifier = modifier) {
        val t = if (isPlaying && currentPositionMs > 0) (currentPositionMs / 1000f) else wallClockTime

        val targetEnergy = if (isPlaying) {
            val kick = max(0.0, sin(t * PI * 2.22)).pow(4.0).toFloat()
            val snare = max(0.0, sin((t + 0.115) * PI * 4.44)).pow(3.0).toFloat()
            val sub = (sin(t * 16.0) * 0.08).toFloat()
            min(1.0f, (0.18f + kick * 0.72f + snare * 0.22f + sub) * volume)
        } else {
            0.15f
        }

        waveEnergy += (targetEnergy - waveEnergy) * (if (targetEnergy > waveEnergy) 0.40f else 0.16f)

        val w = size.width
        val h = size.height

        // 1. Draw base dark background
        drawRect(color = BgDark, size = size)

        // 2. Build wave path
        val baseLineY = h * 0.82f - waveEnergy * (h * 0.68f)
        val amp = 3.0f + waveEnergy * 9.0f

        val wavePath = Path().apply {
            moveTo(0f, h)
            lineTo(0f, baseLineY)

            val step = 2f
            var x = 0f
            while (x <= w) {
                val w1 = sin(x * 0.055 + t * 7.5).toFloat() * amp
                val w2 = cos(x * 0.11 - t * 10.5).toFloat() * (amp * 0.55f)
                val w3 = sin(x * 0.20 + t * 15.0).toFloat() * (amp * 0.3f)
                val spike = max(0.0, sin(x * 0.085 + t * 8.5)).pow(3.0).toFloat() * (waveEnergy * 8.5f)
                val y = baseLineY + w1 + w2 + w3 - spike
                lineTo(x, y)
                x += step
            }
            lineTo(w, h)
            close()
        }

        // Add flame tongues to wave path for unified inversion
        val fullWavePath = Path().apply {
            addPath(wavePath)
            var tx = 6f
            while (tx < w - 6f) {
                val tongueH = (4f + waveEnergy * 15f) * (0.8f + 0.25f * sin(tx + t * 11.0).toFloat())
                val waveY = baseLineY + sin(tx * 0.055 + t * 7.5).toFloat() * amp
                val tongue = Path().apply {
                    moveTo(tx - 3.5f, waveY)
                    quadraticBezierTo(tx, waveY - tongueH, tx + 3.5f, waveY)
                    close()
                }
                addPath(tongue)
                tx += 14f
            }
        }

        // Draw white fire wave
        drawPath(path = fullWavePath, color = Color.White)

        // 3. Inverting SKULLBEAT text via crisp dual clip
        drawIntoCanvas { canvas ->
            val nCanvas = canvas.nativeCanvas
            val whitePaint = Paint().apply {
                isAntiAlias = true
                typeface = orbitronTypeface
                textSize = h * 0.62f
                color = android.graphics.Color.parseColor("#F2F5FF")
                textAlign = Paint.Align.LEFT
            }
            val blackPaint = Paint().apply {
                isAntiAlias = true
                typeface = orbitronTypeface
                textSize = h * 0.62f
                color = android.graphics.Color.BLACK
                textAlign = Paint.Align.LEFT
            }

            val text = "SKULLBEAT"
            val textBounds = android.graphics.Rect()
            whitePaint.getTextBounds(text, 0, text.length, textBounds)
            val textX = 4f
            val textY = (h / 2f) + (textBounds.height() / 2f) - 2f

            val androidPath = fullWavePath.asAndroidPath()

            // Outside wave: draw pure bone white
            nCanvas.save()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nCanvas.clipOutPath(androidPath)
            } else {
                @Suppress("DEPRECATION")
                nCanvas.clipPath(androidPath, android.graphics.Region.Op.DIFFERENCE)
            }
            nCanvas.drawText(text, textX, textY, whitePaint)
            nCanvas.restore()

            // Inside wave: draw pure jet black
            nCanvas.save()
            nCanvas.clipPath(androidPath)
            nCanvas.drawText(text, textX, textY, blackPaint)
            nCanvas.restore()
        }
    }
}
