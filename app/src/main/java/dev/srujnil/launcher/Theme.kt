package dev.srujnil.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

// All colours live here, so changing the look only touches this file.
object Palette {
    val Background = Color(0xFF12141C)
    val Text = Color(0xFFFFFFFF)
    val TextMuted = Color(0xBFFFFFFF)
    val Divider = Color(0x26FFFFFF)
    val Accent = Color(0xFFFFFFFF)
    val Tile = Color(0x1FFFFFFF)
    val TileFocused = Color(0xFFFFFFFF)
    val OnTileFocused = Color(0xFF14161C)
    val Menu = Color(0xF21C1E26)
    val MenuEdge = Color(0x33FFFFFF)
    val Moving = Color(0xFFFFD84D)
    val GlassTop = Color(0x38FFFFFF)
    val GlassBottom = Color(0x14FFFFFF)
    val GlassEdgeTop = Color(0x73FFFFFF)
    val GlassEdgeBottom = Color(0x1AFFFFFF)
}

/**
 * A light, see-through pane like the Mac dock. White at low opacity picks up the colour behind
 * it, and the brighter top edge catches the light. Real background blur isn't available on
 * this Android version, but the background is already soft, so it reads the same.
 */
fun Modifier.glass(shape: Shape): Modifier = this
    .clip(shape)
    .background(Brush.verticalGradient(listOf(Palette.GlassTop, Palette.GlassBottom)))
    .border(1.dp, Brush.verticalGradient(listOf(Palette.GlassEdgeTop, Palette.GlassEdgeBottom)), shape)

/** Roughly each team's colour, for the cars on track. */
fun teamColour(teamId: String): Color = when (teamId) {
    "mercedes" -> Color(0xFF27F4D2)
    "ferrari" -> Color(0xFFFF2A3C)
    "red_bull" -> Color(0xFF4F8DF5)
    "mclaren" -> Color(0xFFFF8000)
    "rb" -> Color(0xFF7FA6FF)
    "alpine" -> Color(0xFFFF87BC)
    "williams" -> Color(0xFF64C4FF)
    "aston_martin" -> Color(0xFF2FBF8F)
    else -> Palette.Text
}

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)
