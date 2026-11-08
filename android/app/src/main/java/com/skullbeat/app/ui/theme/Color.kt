package com.skullbeat.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val BgDark = Color(0xFF05070F)
val SurfaceDark = Color(0xFF0E1426)
val RaisedDark = Color(0xFF172044)
val TextBone = Color(0xFFF2F5FF)
val TextMuted = Color(0xFF7F8BB0)
val ElectricBlue = Color(0xFF3D8BFF)
val CyanAccent = Color(0xFF00D4FF)
val VioletAccent = Color(0xFF8B5CFF)
val HeartRed = Color(0xFFFF3B5C)
val SuccessGreen = Color(0xFF2DE08A)
val WarningYellow = Color(0xFFFFB020)

val PrimaryGradient = Brush.linearGradient(
    colors = listOf(ElectricBlue, CyanAccent)
)

val HighlightGradient = Brush.linearGradient(
    colors = listOf(ElectricBlue, VioletAccent)
)
