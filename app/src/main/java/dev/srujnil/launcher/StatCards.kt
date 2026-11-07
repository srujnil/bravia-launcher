package dev.srujnil.launcher

import androidx.compose.ui.graphics.Color
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.Locale

/** One row of a card: a label, an optional value on the right, and an optional team colour dot. */
data class StatLine(val label: String, val value: String? = null, val dot: Color? = null)

data class StatCard(val title: String, val headline: String? = null, val lines: List<StatLine> = emptyList())

fun isRaceSoon(now: ZonedDateTime, race: NextRace?): Boolean {
    if (race == null) return false
    val untilStart = Duration.between(now.toInstant(), race.start)
    return untilStart.toHours() in -6..72
}

/** The cards to rotate through, with whatever matters most right now first. */
fun buildStatCards(now: ZonedDateTime, stats: F1Stats, circuit: CircuitInfo?): List<StatCard> {
    val next = stats.next?.let { race ->
        StatCard("Next · Round ${race.round}", race.name, listOf(StatLine(countdown(now, race))))
    }
    val qualifying = stats.qualifying.takeIf { it.isNotEmpty() }?.let { grid ->
        StatCard("Qualifying", lines = grid.take(3).map { StatLine(it.driver, it.time) })
    }
    val drivers = stats.drivers.takeIf { it.isNotEmpty() }?.let { list ->
        StatCard("Drivers", lines = list.take(3).map { StatLine(it.name, it.points, teamColour(it.teamId)) })
    }
    val teams = stats.teams.takeIf { it.isNotEmpty() }?.let { list ->
        StatCard("Teams", lines = list.take(3).map { StatLine(it.name, it.points, teamColour(it.teamId)) })
    }
    val last = stats.lastRace?.let { race ->
        val lines = race.podium.mapIndexed { i, driver -> StatLine(driver, "P${i + 1}") }.toMutableList()
        if (race.fastestDriver != null) lines += StatLine("Fastest", "${race.fastestDriver} ${race.fastestTime}")
        StatCard("Last · ${race.name}", lines = lines)
    }
    val circuitCard = circuit?.let {
        val length = String.format(Locale.US, "%.2f km", it.lengthMetres / 1000f)
        val lines = mutableListOf(StatLine("Length", length))
        if (it.firstGp > 0) lines += StatLine("First GP", it.firstGp.toString())
        StatCard("Circuit", it.location, lines)
    }

    val raceJustHappened = stats.lastRace?.date?.let { !it.isBefore(LocalDate.now().minusDays(2)) } == true
    val order = when {
        isRaceSoon(now, stats.next) -> listOf(next, qualifying, circuitCard, drivers, teams, last)
        raceJustHappened -> listOf(last, drivers, teams, next, circuitCard)
        else -> listOf(circuitCard, next, drivers, teams, last)
    }
    return order.filterNotNull()
}

private fun countdown(now: ZonedDateTime, race: NextRace): String {
    val minutes = Duration.between(now.toInstant(), race.start).toMinutes()
    return when {
        minutes <= 0 -> "Race day"
        minutes < 60 -> "In ${minutes}m"
        minutes < 24 * 60 -> "In ${minutes / 60}h ${minutes % 60}m"
        else -> "In ${minutes / (24 * 60)}d ${minutes % (24 * 60) / 60}h"
    }
}
