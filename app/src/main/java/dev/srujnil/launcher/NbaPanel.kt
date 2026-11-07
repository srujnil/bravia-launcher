package dev.srujnil.launcher

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val MAX_ROWS = 6
private val ColumnWidth = 170.dp
private val TeamWidth = 42.dp
private val StatusWidth = 22.dp
private val PanelShape = RoundedCornerShape(16.dp)

// ESPN's team abbreviations. Every other team is in the East.
private val WestTeams = setOf(
    "DAL", "DEN", "GS", "HOU", "LAC", "LAL", "MEM", "MIN", "NO", "OKC", "PHX", "POR", "SA", "SAC", "UTAH",
)

/** The day's games by conference. A game between conferences is listed under the home team's. */
@Composable
fun NbaPanel(day: NbaDay, modifier: Modifier = Modifier) {
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    val (west, east) = day.games.partition { it.home in WestTeams }
    Column(modifier.glass(PanelShape).padding(horizontal = 14.dp, vertical = 11.dp)) {
        PanelTitle("NBA · ${day.date.format(DateTimeFormatter.ofPattern("EEE d MMM"))}")
        if (day.games.isEmpty()) {
            Text("No games today", color = Palette.Text, fontFamily = Inter, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))
        } else {
            Row(Modifier.padding(top = 5.dp)) {
                Conference("West", west, is24Hour)
                Box(Modifier.padding(horizontal = 6.dp).width(1.dp).fillMaxHeight().background(Palette.Divider))
                Conference("East", east, is24Hour)
            }
        }
    }
}

@Composable
private fun Conference(title: String, games: List<NbaGame>, is24Hour: Boolean) {
    val shown = if (games.size > MAX_ROWS) MAX_ROWS - 1 else MAX_ROWS
    Column(Modifier.width(ColumnWidth)) {
        Text(
            text = title,
            color = Palette.Text,
            fontFamily = Inter,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            lineHeight = 17.sp,
        )
        games.take(shown).forEach { GameRow(it, is24Hour) }
        if (games.size > shown) Cell("+${games.size - shown} more", Palette.TextMuted, width = ColumnWidth, align = TextAlign.Start)
    }
}

/** Away team, score or start time, home team, status. Lines up as columns, with the winner brighter. */
@Composable
private fun GameRow(game: NbaGame, is24Hour: Boolean) {
    val finished = game.state == "post"
    val awayWon = finished && (game.awayScore.toIntOrNull() ?: 0) > (game.homeScore.toIntOrNull() ?: 0)
    val homeWon = finished && !awayWon
    val middleWidth = ColumnWidth - TeamWidth * 2 - StatusWidth
    Row(Modifier.height(17.dp), verticalAlignment = Alignment.CenterVertically) {
        Cell(game.away, if (finished && !awayWon) Palette.TextMuted else Palette.Text, TeamWidth, TextAlign.Start, awayWon)
        if (game.state == "pre") {
            val pattern = if (is24Hour) "HH:mm" else "h:mm a"
            val time = game.start.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(pattern))
            Cell(time, Palette.TextMuted, middleWidth, TextAlign.Center)
        } else {
            Cell("${game.awayScore} – ${game.homeScore}", Palette.Text, middleWidth, TextAlign.Center)
        }
        Cell(game.home, if (finished && !homeWon) Palette.TextMuted else Palette.Text, TeamWidth, TextAlign.End, homeWon)
        Spacer(Modifier.width(2.dp))
        val status = when {
            game.state == "in" && game.period > 4 -> "OT"
            game.state == "in" -> "Q${game.period}"
            else -> ""
        }
        Cell(status, Palette.TextMuted, StatusWidth - 2.dp, TextAlign.End)
    }
}

@Composable
private fun Cell(text: String, colour: androidx.compose.ui.graphics.Color, width: Dp, align: TextAlign, strong: Boolean = false) {
    Text(
        text = text,
        color = colour,
        fontFamily = Inter,
        fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 17.sp,
        maxLines = 1,
        textAlign = align,
        modifier = Modifier.width(width),
    )
}
