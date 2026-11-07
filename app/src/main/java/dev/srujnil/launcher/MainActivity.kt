package dev.srujnil.launcher

import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var repository: AppRepository
    private var apps by mutableStateOf<List<AppEntry>>(emptyList())
    private var favourites by mutableStateOf<List<String>>(emptyList())
    private lateinit var liveData: LiveData
    private var weather by mutableStateOf<Weather?>(null)
    private var stats by mutableStateOf(F1Stats())
    private var nba by mutableStateOf<NbaDay?>(null)
    private var teamMarks by mutableStateOf<List<TeamMark>>(emptyList())
    private var welcomeShowing by mutableStateOf(false)
    private var greeting: GreetingChoice? = null
    private lateinit var speaker: Speaker
    // Due after the TV starts up or wakes, so coming back from an app doesn't repeat it.
    private var greetingDue = true
    private val screenOn = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            greetingDue = true
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) greetIfDue()
        }
    }
    private var deviceStatus by mutableStateOf(DeviceStatus(NetworkKind.None, false))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = AppRepository(this)
        favourites = repository.loadFavourites()
        liveData = LiveData(this)
        weather = liveData.cachedWeather()
        stats = liveData.cachedStats()
        nba = liveData.cachedNba()
        speaker = Speaker(this)
        registerReceiver(screenOn, IntentFilter(Intent.ACTION_SCREEN_ON))
        lifecycleScope.launch {
            teamMarks = withContext(Dispatchers.IO) { loadTeamMarks(this@MainActivity) }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    withContext(Dispatchers.IO) { liveData.refreshWeather() }?.let { weather = it }
                    withContext(Dispatchers.IO) { liveData.refreshStats() }?.let { stats = it }
                    withContext(Dispatchers.IO) { liveData.refreshNba() }?.let { nba = it }
                    delay(5 * 60 * 1000L)
                }
            }
        }
        setContent {
            HomeScreen(
                apps = apps,
                favourites = favourites,
                weather = weather,
                stats = stats,
                nba = nba,
                teamMarks = teamMarks,
                deviceStatus = deviceStatus,
                welcomeShowing = welcomeShowing,
                greetingPhrase = greeting?.phrase.orEmpty(),
                speaker = speaker,
                onWelcomeDone = ::endWelcome,
                onLaunch = ::launch,
                onQuickAction = ::openQuickAction,
                onToggleFavourite = ::toggleFavourite,
                onMoveFavourite = ::moveFavourite,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        deviceStatus = readDeviceStatus(this)
        greetIfDue()
        // Picks up apps installed or removed while we were in the background.
        lifecycleScope.launch {
            apps = withContext(Dispatchers.IO) { repository.inSavedOrder(repository.loadApps()) }
        }
    }

    private fun launch(app: AppEntry) {
        val intent = repository.launchIntent(app.key)
        try {
            if (intent != null) startActivity(intent) else showMessage("Can't open ${app.label}")
        } catch (e: ActivityNotFoundException) {
            showMessage("Can't open ${app.label}")
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onPause() {
        super.onPause()
        endWelcome()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(screenOn)
        speaker.shutdown()
    }

    /** Says hello the first time this screen is seen after the TV turns on. */
    private fun greetIfDue() {
        // For testing: greets on launch, whether or not the TV just turned on.
        val forced = intent.getBooleanExtra("greet", false)
        if (forced) intent.removeExtra("greet")
        if (!forced && !greetingDue) return
        greetingDue = false
        // For testing: greets as if it were this local time, such as 2026-11-07T23:30.
        val testTime = intent.getStringExtra("greet_at")?.let(LocalDateTime::parse)
        if (testTime != null) intent.removeExtra("greet_at")
        greeting = chooseGreeting(testTime ?: LocalDateTime.now(), greeting)
        Log.i("Welcome", "Greeting: ${greeting?.period} ${greeting?.phrase}")
        welcomeShowing = true
    }

    private fun endWelcome() {
        welcomeShowing = false
        speaker.stop()
    }

    private fun openQuickAction(action: QuickAction) {
        try {
            action.intent?.let(::startActivity)
        } catch (e: ActivityNotFoundException) {
            openSettingsInstead()
        } catch (e: SecurityException) {
            openSettingsInstead()
        }
    }

    // Screens vary between TV models, and the main settings can reach them all.
    private fun openSettingsInstead() {
        startActivity(Intent(Settings.ACTION_SETTINGS))
    }

    private fun toggleFavourite(app: AppEntry) {
        val removing = app.key in favourites
        favourites = if (removing) favourites - app.key else favourites + app.key
        repository.saveFavourites(favourites)
        showMessage(if (removing) "Removed ${app.label} from favourites" else "Added ${app.label} to favourites")
    }

    private fun moveFavourite(app: AppEntry, delta: Int) {
        val installed = apps.map { it.key }.toSet()
        val (shown, missing) = favourites.partition { it in installed }
        favourites = shown.movedBy(app.key, delta) + missing
        repository.saveFavourites(favourites)
    }

    private fun List<String>.movedBy(key: String, delta: Int): List<String> {
        val from = indexOf(key)
        val to = from + delta
        if (from < 0 || to !in indices) return this
        return toMutableList().apply { add(to, removeAt(from)) }
    }

    private fun showMessage(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }
}
