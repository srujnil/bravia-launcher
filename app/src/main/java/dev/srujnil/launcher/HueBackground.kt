package dev.srujnil.launcher

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.cos
import kotlin.math.sin

private const val DEGREES_PER_SECOND = 0.5f
private const val FRAME_MS = 66L

/** The colour wheel position. Moves slowly, and only while this screen is in front. */
@Composable
fun rememberHueClock(): FloatState {
    val hue = remember { mutableFloatStateOf(210f) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var last = 0L
            while (true) {
                withFrameMillis { now ->
                    if (last == 0L) last = now
                    if (now - last >= FRAME_MS) {
                        hue.floatValue = (hue.floatValue + (now - last) / 1000f * DEGREES_PER_SECOND) % 360f
                        last = now
                    }
                }
            }
        }
    }
    return hue
}

private fun shade(hue: Float, saturation: Float, lightness: Float) =
    Color.hsl((hue + 360f) % 360f, saturation, lightness)

/** Two drifting glows over a diagonal wash. Dark enough that white text stays readable. */
@Composable
fun HueBackground(hue: FloatState, modifier: Modifier = Modifier) {
    Spacer(
        modifier
            .graphicsLayer()
            .drawBehind {
                val h = hue.floatValue
                val angle = Math.toRadians(h.toDouble())
                drawRect(
                    Brush.linearGradient(
                        listOf(shade(h, 0.60f, 0.20f), shade(h + 50f, 0.60f, 0.26f)),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    ),
                )
                val first = Offset(
                    size.width * (0.72f + 0.14f * cos(angle).toFloat()),
                    size.height * (0.28f + 0.16f * sin(angle).toFloat()),
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(shade(h + 120f, 0.70f, 0.36f).copy(alpha = 0.7f), Color.Transparent),
                        center = first,
                        radius = size.width * 0.5f,
                    ),
                )
                val second = Offset(
                    size.width * (0.22f - 0.12f * sin(angle).toFloat()),
                    size.height * (0.72f + 0.14f * cos(angle).toFloat()),
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(shade(h - 70f, 0.70f, 0.34f).copy(alpha = 0.7f), Color.Transparent),
                        center = second,
                        radius = size.width * 0.45f,
                    ),
                )
            },
    )
}
