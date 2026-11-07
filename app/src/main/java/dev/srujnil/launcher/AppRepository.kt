package dev.srujnil.launcher

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

data class AppEntry(
    val key: String,
    val label: String,
    val image: ImageBitmap?,
    val isBanner: Boolean,
)

class AppRepository(context: Context) {
    private val appContext = context.applicationContext
    private val pm = appContext.packageManager
    private val prefs = appContext.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    // Decoded images, reused until the app is updated.
    private val imageCache = HashMap<String, Pair<Long, Pair<ImageBitmap, Boolean>>>()

    /** Installed leanback apps, sorted by name, with a Settings tile last. Call off the main thread. */
    fun loadApps(): List<AppEntry> {
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val apps = pm.queryIntentActivities(query, 0)
            .filter { it.activityInfo.packageName != appContext.packageName }
            .filter { it.activityInfo.packageName !in HIDDEN_PACKAGES }
            .distinctBy { it.activityInfo.packageName }
            .map { info ->
                val pkg = info.activityInfo.packageName
                val updated = pm.getPackageInfo(pkg, 0).lastUpdateTime
                val cached = imageCache[pkg]?.takeIf { it.first == updated }?.second
                val (image, isBanner) = cached ?: run {
                    val banner = info.activityInfo.loadBanner(pm)
                    val decoded = if (banner != null) {
                        banner.toBitmap(320, 180).asImageBitmap() to true
                    } else {
                        info.activityInfo.loadIcon(pm).toBitmap(96, 96).asImageBitmap() to false
                    }
                    imageCache[pkg] = updated to decoded
                    decoded
                }
                AppEntry(pkg, info.loadLabel(pm).toString(), image, isBanner)
            }
            .sortedBy { it.label.lowercase() }
        // Settings has no leanback launcher entry, and this is the way in once this is the home screen.
        return apps + AppEntry(SETTINGS_KEY, "Settings", null, false)
    }

    /** Puts apps in the order the user chose. New apps go at the end. */
    fun inSavedOrder(apps: List<AppEntry>): List<AppEntry> {
        val order = prefs.getString(APP_ORDER, null)?.split("\n").orEmpty()
        val positions = order.withIndex().associate { (index, key) -> key to index }
        return apps.sortedBy { positions[it.key] ?: Int.MAX_VALUE }
    }

    fun launchIntent(key: String): Intent? =
        if (key == SETTINGS_KEY) Intent(Settings.ACTION_SETTINGS)
        else pm.getLeanbackLaunchIntentForPackage(key)

    fun loadFavourites(): List<String> {
        val favourites = withAppleTv(loadSavedFavourites())
        if (prefs.getBoolean(RESTORED_NETFLIX, false)) return favourites
        // Runs once, so Netflix can still be removed with the menu afterwards.
        val restored = (favourites - NETFLIX).toMutableList()
        restored.add(minOf(1, restored.size), NETFLIX)
        saveFavourites(restored)
        prefs.edit().putBoolean(RESTORED_NETFLIX, true).apply()
        return restored
    }

    private fun withAppleTv(favourites: List<String>): List<String> {
        if (prefs.getBoolean(ADDED_APPLE_TV, false)) return favourites
        // Runs once, so Apple TV can still be removed with the menu afterwards.
        val added = (favourites - APPLE_TV) + APPLE_TV
        saveFavourites(added)
        prefs.edit().putBoolean(ADDED_APPLE_TV, true).apply()
        return added
    }

    private fun loadSavedFavourites(): List<String> {
        val saved = prefs.getString(FAVOURITES, null)?.split("\n")?.filter { it.isNotEmpty() } ?: DEFAULT_FAVOURITES
        if (prefs.getBoolean(ADDED_ONCE, false)) return saved
        // Runs once, so these can still be removed with a long press afterwards.
        val merged = (saved + ADD_ONCE_FAVOURITES).distinct()
        saveFavourites(merged)
        prefs.edit().putBoolean(ADDED_ONCE, true).apply()
        return merged
    }

    fun saveFavourites(keys: List<String>) {
        prefs.edit().putString(FAVOURITES, keys.joinToString("\n")).apply()
    }

    private companion object {
        const val SETTINGS_KEY = "launcher.settings"
        const val FAVOURITES = "favourites"
        const val APP_ORDER = "app_order"
        const val RESTORED_NETFLIX = "restored_netflix"
        const val NETFLIX = "com.netflix.ninja"
        const val ADDED_APPLE_TV = "added_apple_tv"
        const val APPLE_TV = "com.apple.atve.sony.appletv"
        const val ADDED_ONCE = "added_once_v1"
        val DEFAULT_FAVOURITES = listOf(
            "com.google.android.youtube.tv",
            "com.netflix.ninja",
            "com.amazon.amazonvideo.livingroom",
            "in.startv.hotstar",
            "com.nbaimd.gametime.nba2011",
            "com.crunchyroll.crunchyroid",
            APPLE_TV,
        )
        val ADD_ONCE_FAVOURITES = listOf(
            "com.google.android.youtube.tv",
            "com.crunchyroll.crunchyroid",
        )

        // Stay installed on the TV, just not shown here.
        val HIDDEN_PACKAGES = setOf(
            "com.aajtakpaid.tv",
            "com.indiatv.androidtv.livetv",
            "com.republicworld.tv",
        )
    }
}
