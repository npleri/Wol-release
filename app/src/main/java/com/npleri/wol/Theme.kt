package com.npleri.wol

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Paleta Nothing: monocromo + un solo acento rojo por pantalla. */
data class Palette(val bg: Color, val ink: Color, val ink2: Color, val ink3: Color, val hairline: Color) {
    val red = Color(0xFFD71921)
}

private val Light = Palette(Color(0xFFF4F4F4), Color(0xFF040404), Color(0xFF595A5A), Color(0xFFCFCFCF), Color(0x24000000))
private val Dark = Palette(Color(0xFF040404), Color(0xFFF4F4F4), Color(0xFF8A8B8B), Color(0xFF2A2A2A), Color(0x29FFFFFF))

val LocalPalette = staticCompositionLocalOf { Light }

@Composable
fun WolTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPalette provides if (isSystemInDarkTheme()) Dark else Light, content = content)
}

@OptIn(ExperimentalTextApi::class)
val Doto = FontFamily(
    Font(
        R.font.doto,
        FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700), FontVariation.Setting("ROND", 100f)),
    )
)

@OptIn(ExperimentalTextApi::class)
val Grotesk = FontFamily(
    Font(R.font.space_grotesk, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.space_grotesk, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
)

val Mono = FontFamily(Font(R.font.space_mono), Font(R.font.space_mono_bold, FontWeight.Bold))

/** Etiqueta mono, mayúsculas, tracking 0.12em: la "serigrafía" de la interfaz. */
fun labelStyle(color: Color, size: TextUnit = 11.sp) =
    TextStyle(fontFamily = Mono, fontSize = size, letterSpacing = 0.12.em, color = color)
