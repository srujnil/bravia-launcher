package dev.srujnil.launcher

import java.time.DayOfWeek
import java.time.LocalDateTime
import kotlin.random.Random

private enum class TimeOfDay(val startHour: Int, val phrases: List<String>) {
    Morning(5, listOf("Good morning, Sruj", "Morning, Sruj", "Rise and shine, Sruj", "Fresh start, Sruj")),
    Afternoon(
        12,
        listOf("Good afternoon, Sruj", "Afternoon, Sruj", "Hope the day's going well, Sruj", "Midday break, Sruj?"),
    ),
    Evening(17, listOf("Good evening, Sruj", "Evening, Sruj", "Time to unwind, Sruj", "Welcome back, Sruj")),
    Late(
        22,
        listOf("A late one, Sruj?", "Burning the midnight oil, Sruj?", "Still up, Sruj?", "Night owl hours, Sruj"),
    ),
}

private fun timeOfDay(hour: Int): TimeOfDay = when (hour) {
    in 5..11 -> TimeOfDay.Morning
    in 12..16 -> TimeOfDay.Afternoon
    in 17..21 -> TimeOfDay.Evening
    else -> TimeOfDay.Late
}

/** The phrase shown, and the stretch of time it belongs to. */
data class GreetingChoice(val period: String, val phrase: String)

/**
 * Keeps the previous phrase while it's still the same part of the day, so waking the TV
 * twice in one evening says the same thing. Otherwise picks at random from that part of
 * the day, plus a weekend phrase on Saturday and Sunday.
 */
fun chooseGreeting(now: LocalDateTime, previous: GreetingChoice?): GreetingChoice {
    val part = timeOfDay(now.hour)
    // Late night runs past midnight, so it belongs to the day it started on.
    val startedOn = if (part == TimeOfDay.Late && now.hour < part.startHour) now.toLocalDate().minusDays(1) else now.toLocalDate()
    val period = "$startedOn ${part.name}"
    if (previous?.period == period) return previous

    val weekend = when (now.dayOfWeek) {
        DayOfWeek.SATURDAY -> listOf("Happy Saturday, Sruj")
        DayOfWeek.SUNDAY -> listOf("Lazy Sunday, Sruj")
        else -> emptyList()
    }
    return GreetingChoice(period, (part.phrases + weekend).random(Random.Default))
}

/** The phrase as the voice should say it. */
fun spokenGreeting(phrase: String): String =
    // Spelt for the voice, not the screen, so the name isn't read out letter by letter.
    phrase.replace("Sruj", "Srooj")
