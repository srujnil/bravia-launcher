package dev.srujnil.launcher

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

// The TV's canvas is 960x540 dp, and these margins are the 5% safe area.
private val MarginHorizontal = 48.dp
private val MarginVertical = 27.dp
private val DockShape = RoundedCornerShape(18.dp)
private val PanelShape = RoundedCornerShape(16.dp)

// The NBA and F1 panels share a top and height, and their edges line up with the docks below.
private val PanelsTop = 66.dp
private val PanelsHeight = 166.dp

private const val CARD_SECONDS = 18
private const val TOUR_SECONDS = 180

private class CardStyle(
    val width: Dp,
    val height: Dp,
    val gap: Dp,
    val corner: Dp,
    val ring: Dp,
    val icon: Dp,
    val textSize: TextUnit,
)

private val LargeCard = CardStyle(150.dp, 84.dp, 14.dp, 12.dp, 3.dp, 44.dp, 16.sp)
private val GridCard = CardStyle(150.dp, 84.dp, 14.dp, 12.dp, 3.dp, 44.dp, 16.sp)

private enum class AppGroup { Favourites, AllApps }

private data class AppInGroup(val app: AppEntry, val group: AppGroup)

@Composable
fun HomeScreen(
    apps: List<AppEntry>,
    favourites: List<String>,
    weather: Weather?,
    stats: F1Stats,
    nba: NbaDay?,
    teamMarks: List<TeamMark>,
    deviceStatus: DeviceStatus,
    welcomeShowing: Boolean,
    greetingPhrase: String,
    speaker: Speaker,
    onWelcomeDone: () -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onQuickAction: (QuickAction) -> Unit,
    onToggleFavourite: (AppEntry) -> Unit,
    onMoveFavourite: (AppEntry, Int) -> Unit,
) {
    val favouriteApps = favourites.mapNotNull { key -> apps.find { it.key == key } }
    val firstCard = remember { FocusRequester() }
    var focusFirstCard by remember { mutableStateOf(0) }
    var menuFor by remember { mutableStateOf<AppInGroup?>(null) }
    var moving by remember { mutableStateOf<AppInGroup?>(null) }
    var showAllApps by remember { mutableStateOf(false) }

    // Runs once the apps have loaded, and again if the focused favourite was just removed.
    LaunchedEffect(apps.isNotEmpty(), focusFirstCard) {
        // The card might not be on screen yet, or at all with no favourites.
        if (apps.isNotEmpty()) runCatching { firstCard.requestFocus() }
    }

    val context = LocalContext.current
    val now by rememberMinuteClock()
    val circuits = remember { CircuitBook(context) }
    val hue = rememberHueClock()

    Box(
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (welcomeShowing) {
                    if (event.type == KeyEventType.KeyUp) onWelcomeDone()
                    return@onPreviewKeyEvent true
                }
                val target = moving ?: return@onPreviewKeyEvent false
                handleMoveKey(
                    event,
                    onMove = { delta -> onMoveFavourite(target.app, delta) },
                    onDone = { moving = null },
                )
            },
    ) {
        HueBackground(hue, Modifier.fillMaxSize())
        if (showAllApps) {
            BackHandler { showAllApps = false; focusFirstCard++ }
            AllAppsGrid(
                apps = apps,
                onLaunch = {
                    onLaunch(it)
                    showAllApps = false
                    focusFirstCard++
                },
                onLongPress = { menuFor = AppInGroup(it, AppGroup.AllApps) },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // This is the home screen, so there is nothing to go back to.
            BackHandler { }
            TeamLogoBackground(teamMarks)
            Clock(now, weather, Modifier.padding(horizontal = MarginHorizontal, vertical = MarginVertical))
            RacePanel(
                cards = buildStatCards(now, stats, stats.next?.let { circuits.info(it.circuitId) }),
                modifier = Modifier.padding(start = 718.dp, top = PanelsTop).size(194.dp, PanelsHeight),
            )
            nba?.let { NbaPanel(it, Modifier.padding(start = 322.dp, top = PanelsTop).size(380.dp, PanelsHeight)) }
            QuickActionRow(
                actions = quickActions(deviceStatus),
                firstFocus = if (favouriteApps.isEmpty()) firstCard else null,
                onClick = {
                    if (it.intent == null) showAllApps = true else onQuickAction(it)
                },
                modifier = Modifier.align(Alignment.TopEnd).padding(end = MarginHorizontal, top = MarginVertical),
            )
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(bottom = MarginVertical),
            ) {
                if (favouriteApps.isNotEmpty()) {
                    // No blur: a translucent panel over a calm background is much cheaper on this TV.
                    Box(
                        Modifier
                            .padding(horizontal = MarginHorizontal)
                            .fillMaxWidth()
                            .glass(DockShape)
                            .padding(top = 6.dp),
                    ) {
                        AppRow(
                            apps = favouriteApps,
                            style = LargeCard,
                            sidePadding = 14.dp,
                            firstCard = firstCard,
                            movingKey = moving?.takeIf { it.group == AppGroup.Favourites }?.app?.key,
                            onLaunch = onLaunch,
                            onLongPress = { menuFor = AppInGroup(it, AppGroup.Favourites) },
                        )
                    }
                }
            }
        }
        if (welcomeShowing) {
            WelcomeOverlay(greetingPhrase, hue, speaker, onWelcomeDone, Modifier.fillMaxSize())
        }
    }

    menuFor?.let { target ->
        AppMenu(
            app = target.app,
            isFavourite = target.app.key in favourites,
            canMove = target.group == AppGroup.Favourites,
            onMove = {
                menuFor = null
                moving = target
            },
            onToggleFavourite = {
                menuFor = null
                onToggleFavourite(target.app)
                // The card holding focus has gone, so put focus somewhere sensible.
                if (target.group == AppGroup.Favourites) focusFirstCard++
            },
            onDismiss = { menuFor = null },
        )
    }
}

/** While moving a card: left and right move it, OK or Back finish, and nothing else gets through. */
private fun handleMoveKey(event: KeyEvent, onMove: (Int) -> Unit, onDone: () -> Unit): Boolean {
    when (event.key) {
        Key.DirectionLeft -> if (event.type == KeyEventType.KeyDown) onMove(-1)
        Key.DirectionRight -> if (event.type == KeyEventType.KeyDown) onMove(1)
        Key.DirectionUp, Key.DirectionDown -> Unit
        Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Back ->
            if (event.type == KeyEventType.KeyUp) onDone()
        else -> return false
    }
    return true
}

@Composable
private fun rememberMinuteClock(): State<ZonedDateTime> {
    val now = remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now.value = ZonedDateTime.now()
            delay((60 - now.value.second) * 1000L)
        }
    }
    return now
}

@Composable
private fun Clock(now: ZonedDateTime, weather: Weather?, modifier: Modifier = Modifier) {
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    val mainPattern = if (is24Hour) "HH:mm" else "h:mm"
    val worldPattern = if (is24Hour) "EEE HH:mm" else "EEE h:mm a"
    Column(modifier) {
        Text(
            text = now.format(DateTimeFormatter.ofPattern(mainPattern)),
            color = Palette.Text,
            fontFamily = Inter,
            fontWeight = FontWeight.Medium,
            fontSize = 72.sp,
            lineHeight = 76.sp,
        )
        Text(
            text = now.format(DateTimeFormatter.ofPattern("EEE d MMM")),
            color = Palette.Text,
            fontFamily = Inter,
            fontSize = 22.sp,
        )
        if (weather != null) {
            Text(
                text = "Mumbai ${weather.temperature}° ${weather.summary}".trim(),
                color = Palette.Text,
                fontFamily = Inter,
                fontSize = 18.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        WorldClock("Melbourne", now, "Australia/Melbourne", worldPattern)
        WorldClock("Philadelphia", now, "America/New_York", worldPattern)
    }
}

@Composable
private fun WorldClock(city: String, now: ZonedDateTime, zone: String, pattern: String) {
    val time = now.withZoneSameInstant(ZoneId.of(zone)).format(DateTimeFormatter.ofPattern(pattern))
    Text(
        text = "$city  $time",
        color = Palette.TextMuted,
        fontFamily = Inter,
        fontSize = 15.sp,
    )
}

@Composable
private fun RacePanel(
    cards: List<StatCard>,
    modifier: Modifier = Modifier,
) {
    var position by remember { mutableStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(CARD_SECONDS * 1000L)
                position++
            }
        }
    }
    Box(modifier.glass(PanelShape).padding(horizontal = 14.dp, vertical = 11.dp)) {
        // Fades on the card's position, so a countdown ticking over doesn't trigger a fade.
        Crossfade(targetState = position, animationSpec = tween(500), label = "stat card") { shown ->
            val card = cards.getOrNull(if (cards.isEmpty()) 0 else shown % cards.size)
            Column(Modifier.fillMaxSize()) {
                if (card != null) {
                    PanelTitle(card.title)
                    if (card.headline != null) {
                        Text(
                            text = card.headline,
                            color = Palette.Text,
                            fontFamily = Inter,
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Spacer(Modifier.height(5.dp))
                    card.lines.forEach { StatRow(it) }
                }
            }
        }
        Text(
            text = "Open-Meteo · Jolpica · ESPN",
            color = Palette.TextMuted,
            fontFamily = Inter,
            fontSize = 9.sp,
            modifier = Modifier.align(Alignment.BottomStart),
        )
    }
}

@Composable
private fun StatRow(line: StatLine) {
    Row(Modifier.fillMaxWidth().height(23.dp), verticalAlignment = Alignment.CenterVertically) {
        if (line.dot != null) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(line.dot))
            Spacer(Modifier.width(7.dp))
        }
        Text(
            text = line.label,
            color = Palette.Text,
            fontFamily = Inter,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (line.value != null) {
            Text(
                text = line.value,
                color = Palette.TextMuted,
                fontFamily = Inter,
                fontSize = 15.sp,
                maxLines = 1,
            )
        }
    }
}

/** Every app, opened from the top row. Back returns to the home screen. */
@Composable
private fun AllAppsGrid(
    apps: List<AppEntry>,
    onLaunch: (AppEntry) -> Unit,
    onLongPress: (AppEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Column(modifier.padding(horizontal = MarginHorizontal).padding(top = MarginVertical)) {
        Text(
            text = "All apps",
            color = Palette.Text,
            fontFamily = Inter,
            fontWeight = FontWeight.Bold,
            fontSize = 30.sp,
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            contentPadding = PaddingValues(top = 14.dp, bottom = MarginVertical),
            horizontalArrangement = Arrangement.spacedBy(29.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(apps, key = { it.key }) { app ->
                AppCard(
                    app = app,
                    style = GridCard,
                    moving = false,
                    modifier = if (app == apps.first()) Modifier.focusRequester(first) else Modifier,
                    onClick = { onLaunch(app) },
                    onLongClick = { onLongPress(app) },
                )
            }
        }
    }
}

/** Icons for the TV's own settings, with the focused one's name shown beside the row. */
@Composable
private fun QuickActionRow(
    actions: List<QuickAction>,
    firstFocus: FocusRequester?,
    onClick: (QuickAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var focusedLabel by remember { mutableStateOf<String?>(null) }
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        focusedLabel?.let {
            Text(
                text = it,
                color = Palette.Text,
                fontFamily = Inter,
                fontSize = 15.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Palette.Menu)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        actions.forEach { action ->
            Surface(
                onClick = { onClick(action) },
                modifier = Modifier
                    .size(32.dp)
                    .then(if (firstFocus != null && action == actions.first()) Modifier.focusRequester(firstFocus) else Modifier)
                    .onFocusChanged { state ->
                        if (state.isFocused) focusedLabel = action.label
                        else if (focusedLabel == action.label) focusedLabel = null
                    },
                shape = ClickableSurfaceDefaults.shape(CircleShape),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.15f),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Palette.Tile,
                    contentColor = Palette.Text,
                    focusedContainerColor = Palette.TileFocused,
                    focusedContentColor = Palette.OnTileFocused,
                    pressedContainerColor = Palette.TileFocused,
                    pressedContentColor = Palette.OnTileFocused,
                ),
            ) {
                Icon(
                    imageVector = action.icon,
                    contentDescription = action.label,
                    tint = LocalContentColor.current,
                    modifier = Modifier.size(20.dp).align(Alignment.Center),
                )
            }
        }
    }
}

/** The small heading used on every panel. */
@Composable
fun PanelTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Palette.TextMuted,
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 17.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
private fun AppMenu(
    app: AppEntry,
    isFavourite: Boolean,
    canMove: Boolean,
    onMove: () -> Unit,
    onToggleFavourite: () -> Unit,
    onDismiss: () -> Unit,
) {
    val first = remember { FocusRequester() }
    // OK is usually still held down when this opens. Ignore it until it's released, so
    // letting go doesn't pick the first option by accident.
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { first.requestFocus() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(300.dp)
                .clip(DockShape)
                .background(Palette.Menu)
                .border(1.dp, Palette.MenuEdge, DockShape)
                .padding(16.dp)
                .onPreviewKeyEvent { event ->
                    if (armed) return@onPreviewKeyEvent false
                    if (event.type == KeyEventType.KeyUp) {
                        armed = true
                        return@onPreviewKeyEvent true
                    }
                    if (event.nativeKeyEvent.repeatCount == 0) {
                        armed = true
                        return@onPreviewKeyEvent false
                    }
                    true
                },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = app.label,
                color = Palette.Text,
                fontFamily = Inter,
                fontWeight = FontWeight.Medium,
                fontSize = 20.sp,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            if (canMove) MenuItem("Move", onMove, Modifier.focusRequester(first))
            MenuItem(
                if (isFavourite) "Remove from favourites" else "Add to favourites",
                onToggleFavourite,
                if (canMove) Modifier else Modifier.focusRequester(first),
            )
        }
    }
}

@Composable
private fun MenuItem(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Palette.Tile,
            contentColor = Palette.Text,
            focusedContainerColor = Palette.TileFocused,
            focusedContentColor = Palette.OnTileFocused,
            pressedContainerColor = Palette.TileFocused,
            pressedContentColor = Palette.OnTileFocused,
        ),
    ) {
        Text(
            text = label,
            color = LocalContentColor.current,
            fontFamily = Inter,
            fontSize = 18.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun AppRow(
    apps: List<AppEntry>,
    style: CardStyle,
    sidePadding: Dp,
    firstCard: FocusRequester?,
    movingKey: String?,
    onLaunch: (AppEntry) -> Unit,
    onLongPress: (AppEntry) -> Unit,
) {
    val listState = rememberLazyListState()
    // A moving card has focus but isn't scrolled to automatically, so keep it on screen.
    val movingIndex = apps.indexOfFirst { it.key == movingKey }
    LaunchedEffect(movingIndex) {
        if (movingIndex < 0) return@LaunchedEffect
        val visible = listState.layoutInfo.visibleItemsInfo
        val fullyVisible = visible.filter {
            it.offset >= 0 && it.offset + it.size <= listState.layoutInfo.viewportEndOffset
        }
        if (fullyVisible.none { it.index == movingIndex }) {
            listState.animateScrollToItem((movingIndex - 2).coerceAtLeast(0))
        }
    }
    LazyRow(
        state = listState,
        // The vertical padding leaves room for the focused card's scale.
        contentPadding = PaddingValues(horizontal = sidePadding, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(style.gap),
        modifier = Modifier.fillMaxWidth().focusRestorer(),
    ) {
        items(apps, key = { it.key }) { app ->
            AppCard(
                app = app,
                style = style,
                moving = app.key == movingKey,
                modifier = if (firstCard != null && app == apps.first()) Modifier.focusRequester(firstCard) else Modifier,
                onClick = { onLaunch(app) },
                onLongClick = { onLongPress(app) },
            )
        }
    }
}

@Composable
private fun AppCard(
    app: AppEntry,
    style: CardStyle,
    moving: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    Column(Modifier.width(style.width)) {
        Surface(
            onClick = onClick,
            onLongClick = onLongClick,
            interactionSource = interactionSource,
            modifier = modifier.size(style.width, style.height),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(style.corner)),
            scale = ClickableSurfaceDefaults.scale(focusedScale = if (moving) 1.14f else 1.08f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Palette.Tile,
                contentColor = Palette.Text,
                focusedContainerColor = if (moving) Palette.Moving else Palette.TileFocused,
                focusedContentColor = Palette.OnTileFocused,
                pressedContainerColor = Palette.TileFocused,
                pressedContentColor = Palette.OnTileFocused,
            ),
        ) {
            when {
                // The cream tile shows around the banner as the focus ring.
                app.image != null && app.isBanner -> Image(
                    bitmap = app.image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(style.ring)
                        .clip(RoundedCornerShape(style.corner - style.ring)),
                )
                app.image != null -> Image(
                    bitmap = app.image,
                    contentDescription = null,
                    modifier = Modifier.size(style.icon).align(Alignment.Center),
                )
                else -> Text(
                    text = app.label,
                    color = LocalContentColor.current,
                    fontFamily = Inter,
                    fontWeight = FontWeight.Medium,
                    fontSize = style.textSize,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
        // Always takes up its height, so rows don't shift as focus moves.
        // Wider than a small card, so its name isn't cut short.
        Box(
            Modifier.requiredWidth(LargeCard.width).height(26.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            if (focused) {
                Text(
                    text = if (moving) "◀ Move ▶" else app.label,
                    color = if (moving) Palette.Moving else Palette.Text,
                    fontFamily = Inter,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
