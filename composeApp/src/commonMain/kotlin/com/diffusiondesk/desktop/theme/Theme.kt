package com.diffusiondesk.desktop.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme

private val DarkColors = darkColorScheme(
    background = Color(0xFF1E1F22),
    surface = Color(0xFF2B2D30),
    surfaceVariant = Color(0xFF393B40),
    primary = Color(0xFF6B9BFA),
    onPrimary = Color.White,
    onSurface = Color(0xFFDFE1E5),
    onSurfaceVariant = Color(0xFFB8BCC5),
    outline = Color(0xFF5F6470),
    outlineVariant = Color(0xFF3E4148),
    error = Color(0xFFFF6B68),
)
private val LightColors = lightColorScheme(
    background = Color(0xFFEEEDE9),
    surface = Color(0xFFF8F7F3),
    surfaceVariant = Color(0xFFE7E4DD),
    primary = Color(0xFF3574F0),
    onPrimary = Color.White,
    onSurface = Color(0xFF242321),
    onSurfaceVariant = Color(0xFF66625B),
    outline = Color(0xFFC8C2B7),
    outlineVariant = Color(0xFFD9D4CB),
    error = Color(0xFFD32F2F),
)

private val AppTypography = Typography(
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 13.sp, lineHeight = 18.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 16.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 16.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 22.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 20.sp, lineHeight = 28.sp),
)

@Composable
fun DiffusionDeskTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    IntUiTheme(isDark = darkTheme) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = AppTypography,
            content = content,
        )
    }
}
