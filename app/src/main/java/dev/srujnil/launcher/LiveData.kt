package dev.srujnil.launcher

import android.content.Context
import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import org.json.JSONArray
import org.json.JSONObject

data class Weather(val temperature: Int, val summary: String)

data class NextRace(val name: String, val circuitId: String, val round: Int, val start: Instant)

data class Standing(val name: String, val points: String, val teamId: String)

data class QualifyingEntry(val driver: String, val time: String)

data class LastRace(
    val name: String,
    val date: LocalDate,
    val podium: List<String>,
    val fastestDriver: String?,
    val fastestTime: String?,
)

data class NbaGame(
    val away: String,
    val awayScore: String,
    val home: String,
    val homeScore: String,
    /** ESPN's "pre", "in" or "post". */
    val state: String,
    val period: Int,
    val start: Instant,
)

data class NbaDay(val date: LocalDate, val games: List<NbaGame>)

data class F1Stats(
    val next: NextRace? = null,
    val drivers: List<Standing> = emptyList(),
    val teams: List<Standing> = emptyList(),
    val qualifying: List<QualifyingEntry> = emptyList(),
    val lastRace: LastRace? = null,
)

/** Fetches weather and F1 data, keeping the last good answer so a network drop doesn't blank the screen. */
class LiveData(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("live", Context.MODE_PRIVATE)

    fun cachedWeather(): Weather? = prefs.getString(WEATHER, null)?.let(::parseWeather)

    fun cachedStats(): F1Stats = F1Stats(
        next = prefs.getString(NEXT, null)?.let(::parseNext),
        drivers = prefs.getString(DRIVERS, null)?.let(::parseDrivers).orEmpty(),
        teams = prefs.getString(TEAMS, null)?.let(::parseTeams).orEmpty(),
        qualifying = prefs.getString(QUALIFYING, null)?.let(::parseQualifying).orEmpty(),
        lastRace = prefs.getString(LAST_RACE, null)?.let(::parseLastRace),
    )

    fun cachedNba(): NbaDay? = prefs.getString(NBA, null)?.let(::parseNba)

    /** Call off the main thread. Returns null if there's nothing newer to show. */
    fun refreshNba(): NbaDay? {
        if (!isStale(NBA, STATS_MAX_AGE_MS)) return null
        val json = fetch(NBA_URL) ?: return null
        store(NBA, json)
        prefs.edit().putLong(NBA + AGE_SUFFIX, System.currentTimeMillis()).apply()
        return parseNba(json)
    }

    private fun parseNba(json: String): NbaDay? = parsing("NBA") {
        val root = JSONObject(json)
        val events = root.getJSONArray("events")
        val startFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mmX")
        NbaDay(
            date = LocalDate.parse(root.getJSONObject("day").getString("date")),
            games = List(events.length()) { i ->
                val event = events.getJSONObject(i)
                val competitors = event.getJSONArray("competitions").getJSONObject(0).getJSONArray("competitors")
                val teams = List(competitors.length()) { competitors.getJSONObject(it) }
                val home = teams.first { it.getString("homeAway") == "home" }
                val away = teams.first { it.getString("homeAway") == "away" }
                val status = event.getJSONObject("status")
                NbaGame(
                    away = away.getJSONObject("team").getString("abbreviation"),
                    awayScore = away.optString("score"),
                    home = home.getJSONObject("team").getString("abbreviation"),
                    homeScore = home.optString("score"),
                    state = status.getJSONObject("type").getString("state"),
                    period = status.optInt("period"),
                    start = OffsetDateTime.parse(event.getString("date"), startFormat).toInstant(),
                )
            },
        )
    }

    /** Call off the main thread. Returns null if there's nothing newer to show. */
    fun refreshWeather(): Weather? {
        if (!isStale(WEATHER, WEATHER_MAX_AGE_MS)) return null
        val json = fetch(WEATHER_URL) ?: return null
        store(WEATHER, json)
        prefs.edit().putLong(WEATHER + AGE_SUFFIX, System.currentTimeMillis()).apply()
        return parseWeather(json)
    }

    /** Call off the main thread. Returns null if there's nothing newer to show. */
    fun refreshStats(): F1Stats? {
        if (!isStale(NEXT, STATS_MAX_AGE_MS)) return null
        val next = fetch("$F1_URL/current/next.json") ?: return null
        store(NEXT, next)
        prefs.edit().putLong(NEXT + AGE_SUFFIX, System.currentTimeMillis()).apply()
        fetch("$F1_URL/current/driverStandings.json")?.let { store(DRIVERS, it) }
        fetch("$F1_URL/current/constructorStandings.json")?.let { store(TEAMS, it) }
        fetch("$F1_URL/current/last/results.json")?.let { store(LAST_RACE, it) }
        // The next race's qualifying, which is empty until that session has run.
        val table = JSONObject(next).getJSONObject("MRData").getJSONObject("RaceTable")
        val qualifying = fetch("$F1_URL/${table.optString("season")}/${table.optString("round")}/qualifying.json")
        store(QUALIFYING, qualifying ?: "")
        return cachedStats()
    }

    private fun isStale(key: String, maxAgeMs: Long) =
        System.currentTimeMillis() - prefs.getLong(key + AGE_SUFFIX, 0) > maxAgeMs

    private fun store(key: String, json: String) {
        prefs.edit().putString(key, json).apply()
    }

    private fun fetch(url: String): String? = try {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        try {
            if (connection.responseCode == 200) {
                connection.inputStream.bufferedReader().readText()
            } else {
                Log.w(TAG, "HTTP ${connection.responseCode} from $url")
                null
            }
        } finally {
            connection.disconnect()
        }
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't fetch $url", e)
        null
    }

    private fun parseWeather(json: String): Weather? = parsing("weather") {
        val current = JSONObject(json).getJSONObject("current")
        Weather(
            temperature = Math.round(current.getDouble("temperature_2m")).toInt(),
            summary = weatherSummary(current.getInt("weather_code")),
        )
    }

    private fun parseNext(json: String): NextRace? = parsing("next race") {
        val race = races(json).getJSONObject(0)
        NextRace(
            name = shortName(race.getString("raceName")),
            circuitId = race.getJSONObject("Circuit").getString("circuitId"),
            round = race.getString("round").toInt(),
            start = Instant.parse(race.getString("date") + "T" + race.getString("time")),
        )
    }

    private fun parseDrivers(json: String): List<Standing>? = parsing("driver standings") {
        val list = standings(json, "DriverStandings")
        List(list.length()) {
            val entry = list.getJSONObject(it)
            val teams = entry.getJSONArray("Constructors")
            Standing(
                name = driverCode(entry),
                points = entry.getString("points"),
                teamId = teams.getJSONObject(teams.length() - 1).getString("constructorId"),
            )
        }
    }

    private fun parseTeams(json: String): List<Standing>? = parsing("team standings") {
        val list = standings(json, "ConstructorStandings")
        List(list.length()) {
            val entry = list.getJSONObject(it)
            val team = entry.getJSONObject("Constructor")
            Standing(team.getString("name").removeSuffix(" F1 Team"), entry.getString("points"), team.getString("constructorId"))
        }
    }

    private fun parseQualifying(json: String): List<QualifyingEntry>? = parsing("qualifying") {
        if (json.isEmpty()) return@parsing emptyList()
        val all = races(json)
        if (all.length() == 0) return@parsing emptyList()
        val list = all.getJSONObject(0).getJSONArray("QualifyingResults")
        List(list.length()) {
            val entry = list.getJSONObject(it)
            val time = entry.optString("Q3").ifEmpty { entry.optString("Q2").ifEmpty { entry.optString("Q1") } }
            QualifyingEntry(driverCode(entry), time)
        }
    }

    private fun parseLastRace(json: String): LastRace? = parsing("last race") {
        val race = races(json).getJSONObject(0)
        val results = race.getJSONArray("Results")
        val drivers = List(results.length()) { results.getJSONObject(it) }
        val fastest = drivers.find { it.optJSONObject("FastestLap")?.optString("rank") == "1" }
        LastRace(
            name = shortName(race.getString("raceName")),
            date = LocalDate.parse(race.getString("date")),
            podium = drivers.take(3).map { driverCode(it) },
            fastestDriver = fastest?.let(::driverCode),
            fastestTime = fastest?.getJSONObject("FastestLap")?.getJSONObject("Time")?.getString("time"),
        )
    }

    // Not every driver has a three-letter code, such as a late-season substitute.
    private fun driverCode(entry: JSONObject): String {
        val driver = entry.getJSONObject("Driver")
        return driver.optString("code").ifEmpty { driver.getString("familyName").take(3).uppercase() }
    }

    private fun races(json: String): JSONArray =
        JSONObject(json).getJSONObject("MRData").getJSONObject("RaceTable").getJSONArray("Races")

    private fun standings(json: String, name: String): JSONArray =
        JSONObject(json).getJSONObject("MRData").getJSONObject("StandingsTable")
            .getJSONArray("StandingsLists").getJSONObject(0).getJSONArray(name)

    private fun shortName(raceName: String) = raceName.replace("Grand Prix", "GP")

    private inline fun <T> parsing(what: String, block: () -> T): T? = try {
        block()
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't read $what", e)
        null
    }

    private fun weatherSummary(code: Int) = when (code) {
        0 -> "Clear"
        1, 2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog"
        in 51..57 -> "Drizzle"
        in 61..67 -> "Rain"
        in 71..77, 85, 86 -> "Snow"
        80, 81, 82 -> "Showers"
        in 95..99 -> "Thunderstorm"
        else -> ""
    }

    private companion object {
        const val TAG = "LiveData"
        const val WEATHER = "weather"
        const val NEXT = "next"
        const val DRIVERS = "drivers"
        const val TEAMS = "teams"
        const val QUALIFYING = "qualifying"
        const val LAST_RACE = "last_race"
        const val NBA = "nba"
        const val NBA_URL = "https://site.api.espn.com/apis/site/v2/sports/basketball/nba/scoreboard"
        const val AGE_SUFFIX = "_at"
        const val TIMEOUT_MS = 8000
        const val WEATHER_MAX_AGE_MS = 60 * 60 * 1000L
        const val STATS_MAX_AGE_MS = 60 * 60 * 1000L
        const val WEATHER_URL =
            "https://api.open-meteo.com/v1/forecast?latitude=19.076&longitude=72.8777&current=temperature_2m,weather_code"
        const val F1_URL = "https://api.jolpi.ca/ergast/f1"
    }
}
