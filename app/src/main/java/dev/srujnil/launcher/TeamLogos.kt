package dev.srujnil.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Log
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.tv.material3.Text
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val TAG = "TeamLogos"
private const val LOGO_ALPHA = 0.75f
private const val F1_LOGO_ALPHA = 1f
private const val DARK_INK_LIMIT = 0.2f
private const val SWAP_SECONDS = 4
private const val FADE_MS = 1800
private const val DECODED_PIXELS = 300

private const val F1_LOGOS = "https://media.formula1.com/image/upload/c_fit,w_512/content/dam/fom-website/2018-redesign-assets/team%20logos/"
private const val NBA_TEAMS = "https://site.api.espn.com/apis/site/v2/sports/basketball/nba/teams"

/** A team for the background: its downloaded logo, or its name if there isn't one. */
data class TeamMark(
    val name: String,
    val teamId: String?,
    val file: File?,
    val isF1: Boolean = false,
    val lightenDarkInk: Boolean = false,
)

private class F1Team(val name: String, val teamId: String, val logo: String?, val lightenDarkInk: Boolean = true)

private val F1Teams = listOf(
    F1Team("Mercedes", "mercedes", "mercedes"),
    // Black is part of the badge here, and this one is a solid picture, so both stay as they are.
    F1Team("Ferrari", "ferrari", "ferrari", lightenDarkInk = false),
    F1Team("McLaren", "mclaren", "mclaren"),
    F1Team("Alpine", "alpine", "alpine"),
    F1Team("Williams", "williams", "williams"),
    F1Team("Haas", "haas", "haas"),
    F1Team("Aston Martin", "aston_martin", "aston%20martin"),
    F1Team("Racing Bulls", "rb", "rb", lightenDarkInk = false),
    F1Team("Red Bull", "red_bull", null),
    F1Team("Audi", "audi", null),
    F1Team("Cadillac", "cadillac", null),
)

/** Downloads team logos once and keeps them on the TV. Call off the main thread. */
fun loadTeamMarks(context: Context): List<TeamMark> {
    val folder = File(context.filesDir, "logos").apply { mkdirs() }
    val f1 = F1Teams.map { team ->
        val file = team.logo?.let { download("$F1_LOGOS$it.png", File(folder, "f1-${team.teamId}.png")) }
        TeamMark(team.name, team.teamId, file, isF1 = true, lightenDarkInk = team.lightenDarkInk)
    }
    return f1 + nbaLogoUrls().mapNotNull { (abbreviation, url) ->
        download(url, File(folder, "nba-$abbreviation.png"))?.let { TeamMark(abbreviation, null, it) }
    }
}

/** ESPN's logos for dark backgrounds, or whichever logos are already saved if it can't be reached. */
private fun nbaLogoUrls(): List<Pair<String, String>> = try {
    val teams = JSONObject(fetchBytes(NBA_TEAMS).decodeToString()).getJSONArray("sports").getJSONObject(0)
        .getJSONArray("leagues").getJSONObject(0).getJSONArray("teams")
    List(teams.length()) { i ->
        val team = teams.getJSONObject(i).getJSONObject("team")
        val logos = team.getJSONArray("logos")
        val all = List(logos.length()) { logos.getJSONObject(it) }
        val logo = all.find { it.getJSONArray("rel").toString().contains("dark") } ?: all.first()
        team.getString("abbreviation").lowercase() to logo.getString("href")
    }
} catch (e: Exception) {
    Log.w(TAG, "Couldn't list NBA teams", e)
    emptyList()
}

private fun download(url: String, file: File): File? {
    if (file.exists()) return file
    return try {
        file.writeBytes(fetchBytes(url))
        file
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't download $url", e)
        null
    }
}

private fun fetchBytes(url: String): ByteArray {
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.connectTimeout = 8000
    connection.readTimeout = 8000
    try {
        return connection.inputStream.use { it.readBytes() }
    } finally {
        connection.disconnect()
    }
}

/** Decoded small, because they're never shown large, and only while on screen. */
private fun decodeLogo(file: File): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= DECODED_PIXELS) sample *= 2
    return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}

/** F1's logos are made for light backgrounds, so near-black ink turns white to show on dark colours. */
private fun lightenDarkInk(original: Bitmap): ImageBitmap {
    val width = original.width
    val height = original.height
    val pixels = IntArray(width * height)
    original.getPixels(pixels, 0, width, 0, 0, width, height)
    for (i in pixels.indices) {
        val pixel = pixels[i]
        val brightness = (0.299f * Color.red(pixel) + 0.587f * Color.green(pixel) + 0.114f * Color.blue(pixel)) / 255f
        if (brightness < DARK_INK_LIMIT) pixels[i] = (pixel and 0xFF000000.toInt()) or 0x00FFFFFF
    }
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()
}

private class Shown(val mark: TeamMark, val image: ImageBitmap?)

// Where the logos sit on the 960x540 dp canvas, in the open space above the dock.
private val Slots = listOf(
    Triple(70, 236, 130),
    Triple(250, 262, 104),
    Triple(405, 240, 130),
    Triple(585, 262, 104),
    Triple(750, 236, 130),
)

/** Faint team logos. One changes every few seconds, only while this screen is in front. */
@Composable
fun TeamLogoBackground(marks: List<TeamMark>, modifier: Modifier = Modifier) {
    if (marks.isEmpty()) return
    val deck = remember(marks) { marks.shuffled() }
    val shown = remember(deck) { mutableStateOf(List<Shown?>(Slots.size) { null }) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(deck, lifecycle) {
        var next = 0
        var slot = 0
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                val mark = deck[next % deck.size]
                val image = withContext(Dispatchers.IO) {
                    mark.file?.let { file ->
                        if (mark.lightenDarkInk) {
                            BitmapFactory.decodeFile(file.path)?.let(::lightenDarkInk)
                        } else {
                            decodeLogo(file)
                        }
                    }
                }
                shown.value = shown.value.toMutableList().also { it[slot] = Shown(mark, image) }
                next++
                slot = (slot + 2) % Slots.size
                // Fills every position quickly at the start, then changes slowly.
                delay(if (next <= Slots.size) 300L else SWAP_SECONDS * 1000L)
            }
        }
    }
    Box(modifier.fillMaxSize()) {
        Slots.forEachIndexed { i, (x, y, size) ->
            Crossfade(
                targetState = shown.value[i],
                animationSpec = tween(FADE_MS),
                label = "team logo",
                modifier = Modifier.offset(x.dp, y.dp).size(size.dp),
            ) { item ->
                when {
                    item == null -> Box(Modifier.fillMaxSize())
                    item.image != null -> Image(
                        bitmap = item.image,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        alpha = if (item.mark.isF1) F1_LOGO_ALPHA else LOGO_ALPHA,
                        modifier = Modifier.fillMaxSize(),
                    )
                    else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = item.mark.name,
                            color = teamColour(item.mark.teamId.orEmpty()).copy(alpha = LOGO_ALPHA + 0.15f),
                            fontFamily = Inter,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}
