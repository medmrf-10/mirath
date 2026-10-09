package com.irth.mirath.ui.theme

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
import com.irth.mirath.R

val Amiri = FontFamily(
    Font(R.font.amiri_regular, FontWeight.Normal),
    Font(R.font.amiri_bold, FontWeight.Bold),
)
val Naskh = FontFamily(Font(R.font.naskh_regular, FontWeight.Normal))

data class Pal(
    val bg: Color, val card: Color, val text: Color, val muted: Color,
    val line: Color, val primary: Color, val gold: Color, val soft: Color, val press: Color,
)

val NightPal = Pal(
    bg = Color(0xFF14110E), card = Color(0xFF1C1814), text = Color(0xFFEFE6D6),
    muted = Color(0xFFA39684), line = Color(0xFF2F2923), primary = Color(0xFFE3A598),
    gold = Color(0xFFC9A35C), soft = Color(0xFF2A241E), press = Color(0xFF241F1A),
)
val PaperPal = Pal(
    bg = Color(0xFFF4EEE2), card = Color(0xFFFBF7EF), text = Color(0xFF2A241D),
    muted = Color(0xFF8A7E6E), line = Color(0xFFE5DAC6), primary = Color(0xFF7B2D26),
    gold = Color(0xFFAE8848), soft = Color(0xFFEADFCB), press = Color(0xFFF2EADB),
)
val WhitePal = Pal(
    bg = Color(0xFFFFFFFF), card = Color(0xFFF0F2F5), text = Color(0xFF050505),
    muted = Color(0xFF65676B), line = Color(0xFFCED0D4), primary = Color(0xFF7B2D26),
    gold = Color(0xFFAE8848), soft = Color(0xFFE4E6EB), press = Color(0xFFF0F2F5),
)
val DarkPal = Pal(
    bg = Color(0xFF18191A), card = Color(0xFF242526), text = Color(0xFFE4E6EB),
    muted = Color(0xFFB0B3B8), line = Color(0xFF3E4042), primary = Color(0xFFE3A598),
    gold = Color(0xFFC9A35C), soft = Color(0xFF3A3B3C), press = Color(0xFF2D2E2F),
)

fun palOf(name: String): Pal = when (name) {
    "paper" -> PaperPal; "white" -> WhitePal; "dark" -> DarkPal; else -> NightPal
}

val MirathTypography = Typography(
    displayLarge = TextStyle(fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 44.sp),
    headlineMedium = TextStyle(fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 34.sp),
    titleLarge = TextStyle(fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 30.sp),
    titleMedium = TextStyle(fontFamily = Naskh, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 26.sp),
    bodyLarge = TextStyle(fontFamily = Naskh, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 30.sp),
    bodyMedium = TextStyle(fontFamily = Naskh, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 26.sp),
    labelMedium = TextStyle(fontFamily = Naskh, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 20.sp),
)

@Composable
fun MirathTheme(themeName: String, content: @Composable () -> Unit) {
    val p = palOf(themeName)
    val dark = p.text.red > 0.5f
    val scheme = if (dark) darkColorScheme(
        primary = p.primary, onPrimary = p.card,
        background = p.bg, onBackground = p.text,
        surface = p.bg, onSurface = p.text,
        surfaceVariant = p.card, onSurfaceVariant = p.muted,
        outline = p.line, secondary = p.gold, tertiary = p.soft,
    ) else lightColorScheme(
        primary = p.primary, onPrimary = p.card,
        background = p.bg, onBackground = p.text,
        surface = p.bg, onSurface = p.text,
        surfaceVariant = p.card, onSurfaceVariant = p.muted,
        outline = p.line, secondary = p.gold, tertiary = p.soft,
    )
    MaterialTheme(colorScheme = scheme, typography = MirathTypography, content = content)
}
