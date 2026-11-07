package dev.srujnil.launcher

import android.content.Context
import org.json.JSONObject

data class CircuitInfo(val id: String, val location: String, val lengthMetres: Int, val firstGp: Int)

/** Facts about each circuit, bundled with the app. */
class CircuitBook(context: Context) {
    private val circuits = context.resources.openRawResource(R.raw.circuits).bufferedReader()
        .use { JSONObject(it.readText()) }

    fun info(id: String): CircuitInfo? {
        val circuit = circuits.optJSONObject(id) ?: return null
        return CircuitInfo(id, circuit.getString("location"), circuit.getInt("lengthM"), circuit.getInt("firstGp"))
    }
}
