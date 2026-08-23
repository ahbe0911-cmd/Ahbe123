package ir.rooznegar.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ir.rooznegar.app.R
import ir.rooznegar.app.data.ThemeMode

val RooznegarPurple = Color(0xFF6650D8)
val RooznegarPurpleDark = Color(0xFF4E3EB7)
val RooznegarNavy = Color(0xFF151934)
val RooznegarGreen = Color(0xFF2FA47A)
val RooznegarOrange = Color(0xFFF29A4A)

private val Light = lightColorScheme(
    primary = RooznegarPurple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFECE8FF),
    onPrimaryContainer = Color(0xFF241857),
    secondary = RooznegarGreen,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD8F4E9),
    tertiary = RooznegarOrange,
    background = Color(0xFFF7F7FC),
    onBackground = Color(0xFF1C1C24),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1C24),
    surfaceVariant = Color(0xFFF0EFF7),
    onSurfaceVariant = Color(0xFF6E6B79),
    outline = Color(0xFFE1DFE9),
    outlineVariant = Color(0xFFEEEAF4)
)

private val Dark = darkColorScheme(
    primary = Color(0xFFC9C0FF),
    onPrimary = Color(0xFF34247D),
    primaryContainer = Color(0xFF4B3A9F),
    onPrimaryContainer = Color(0xFFEAE5FF),
    secondary = Color(0xFF84D8BA),
    tertiary = Color(0xFFFFB77B),
    background = Color(0xFF101225),
    onBackground = Color(0xFFF3F1FA),
    surface = Color(0xFF191C35),
    onSurface = Color(0xFFF3F1FA),
    surfaceVariant = Color(0xFF242742),
    onSurfaceVariant = Color(0xFFC5C2D2),
    outline = Color(0xFF3B3E58),
    outlineVariant = Color(0xFF2C2F49)
)

val VazirFontFamily = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_bold, FontWeight.Bold)
)

private val RooznegarTypography = Typography(
    displayLarge = TextStyle(fontFamily = VazirFontFamily, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 44.sp),
    headlineLarge = TextStyle(fontFamily = VazirFontFamily, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontFamily = VazirFontFamily, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontFamily = VazirFontFamily, fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 31.sp),
    titleLarge = TextStyle(fontFamily = VazirFontFamily, fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 29.sp),
    titleMedium = TextStyle(fontFamily = VazirFontFamily, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 26.sp),
    titleSmall = TextStyle(fontFamily = VazirFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = VazirFontFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 27.sp),
    bodyMedium = TextStyle(fontFamily = VazirFontFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 23.sp),
    bodySmall = TextStyle(fontFamily = VazirFontFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = VazirFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = VazirFontFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = VazirFontFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp)
)

@Composable
fun RooznegarTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        typography = RooznegarTypography,
        content = content
    )
}
