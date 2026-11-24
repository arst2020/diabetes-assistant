package com.arst.diabetesassistant.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF00696E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB9F0F2),
    onPrimaryContainer = Color(0xFF002022),
    secondary = Color(0xFF4A6364),
    tertiary = Color(0xFF4F5F7D),
    error = Color(0xFFBA1A1A),
    background = Color(0xFFF7FBFB),
    surface = Color(0xFFF7FBFB),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF7FD4D8),
    onPrimary = Color(0xFF00373A),
    primaryContainer = Color(0xFF004F53),
    onPrimaryContainer = Color(0xFFB9F0F2),
    secondary = Color(0xFFB1CBCC),
    tertiary = Color(0xFFB7C7EA),
    error = Color(0xFFFFB4AB),
)

val ColorLow = Color(0xFFD32F2F)
val ColorHigh = Color(0xFFF57C00)
val ColorOk = Color(0xFF2E7D32)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
