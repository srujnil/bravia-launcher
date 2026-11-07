package dev.srujnil.launcher

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "Welcome"
private const val GREETING_MIN_MS = 5500L
private const val WRITING_WAIT_MS = 10000L
private const val FADE_OUT_MS = 700
private const val SPEECH_MAX_MS = 60000L
private const val SPEAKER_WAIT_MS = 15000L

// Lower-pitched male voices in the TV's speech engine, best match first.
private val MaleVoices = listOf(
    "en-gb-x-rjs", "en-gb-x-gbd", "en-gb-x-gbb", "en-us-x-iom", "en-us-x-iol", "en-us-x-tpd", "en-au-x-aud", "en-au-x-auj",
)
private const val VOICE_PITCH = 0.95f
private const val VOICE_RATE = 0.88f
private const val VOICE_VOLUME = 0.8f

/** The TV's text-to-speech. If it isn't available, the welcome still shows, in silence. */
class Speaker(context: Context) {
    private val ready = CompletableDeferred<Boolean>()
    private val finished = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
    private var onSpeechStart: (() -> Unit)? = null
    private var hasVoice = false
    private val engine: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        // Choosing the voice now loads it, which can take many seconds the first time.
        if (status == TextToSpeech.SUCCESS) hasVoice = useMaleVoice()
        ready.complete(status == TextToSpeech.SUCCESS)
    }

    init {
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) {
                Log.i(TAG, "Speaking")
                onSpeechStart?.invoke()
            }

            override fun onDone(utteranceId: String) {
                Log.i(TAG, "Finished speaking")
                finished.remove(utteranceId)?.complete(Unit)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String) {
                Log.w(TAG, "Speech failed")
                finished.remove(utteranceId)?.complete(Unit)
            }

            override fun onStop(utteranceId: String, interrupted: Boolean) {
                finished.remove(utteranceId)?.complete(Unit)
            }
        })
    }

    /** After a restart the voice takes several seconds to load. Returns once it has, or it's given up. */
    suspend fun awaitReady(): Boolean = withTimeoutOrNull(SPEAKER_WAIT_MS) { ready.await() } ?: false

    /** Returns when the speech has ended, or straight away if it can't speak. */
    suspend fun say(text: String, onStart: () -> Unit) {
        onSpeechStart = onStart
        if (!awaitReady()) {
            Log.w(TAG, "Speech isn't available")
            return
        }
        // No suitable male voice means no speech at all, rather than a different voice.
        if (!hasVoice) return
        val id = UUID.randomUUID().toString()
        val done = CompletableDeferred<Unit>()
        finished[id] = done
        val gentle = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, VOICE_VOLUME) }
        if (engine.speak(text, TextToSpeech.QUEUE_FLUSH, gentle, id) != TextToSpeech.SUCCESS) {
            Log.w(TAG, "Speech was refused")
            finished.remove(id)
            return
        }
        withTimeoutOrNull(SPEECH_MAX_MS) { done.await() }
    }

    private fun useMaleVoice(): Boolean {
        val voices = engine.voices.orEmpty().filter {
            TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features
        }
        Log.i(TAG, "Voices: " + voices.filter { it.locale.language == "en" }.joinToString { it.name })
        val choice = MaleVoices.firstNotNullOfOrNull { wanted ->
            voices.filter { it.name.startsWith(wanted) }.minByOrNull { it.isNetworkConnectionRequired }
        }
        if (choice == null) {
            Log.w(TAG, "No male voice installed")
            return false
        }
        Log.i(TAG, "Using voice ${choice.name}")
        engine.voice = choice
        engine.setPitch(VOICE_PITCH)
        engine.setSpeechRate(VOICE_RATE)
        return true
    }

    fun stop() {
        engine.stop()
    }

    fun shutdown() {
        engine.shutdown()
    }
}

/** A quiet daily hello, in the middle of the screen. Any button skips it, which HomeScreen handles. */
@Composable
fun WelcomeOverlay(
    phrase: String,
    hue: FloatState,
    speaker: Speaker,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var started by remember { mutableStateOf(false) }
    val overlay = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        val voiceStarted = CompletableDeferred<Unit>()
        coroutineScope {
            launch {
                // So the writing and the voice start together, unless the voice is very slow.
                withTimeoutOrNull(WRITING_WAIT_MS) { voiceStarted.await() }
                started = true
                delay(GREETING_MIN_MS)
            }
            speaker.say(spokenGreeting(phrase)) { voiceStarted.complete(Unit) }
            voiceStarted.complete(Unit)
        }
        overlay.animateTo(0f, tween(FADE_OUT_MS))
        onDone()
    }
    Box(modifier.graphicsLayer { alpha = overlay.value }) {
        HueBackground(hue, Modifier.fillMaxSize())
        if (started) {
            // The side padding lets the longest phrases wrap onto a second line instead of running off screen.
            Column(
                Modifier.align(Alignment.Center).padding(horizontal = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                GreetingLine(phrase)
            }
        }
    }
}

/** Fades in while settling gently upward. */
@Composable
private fun GreetingLine(text: String) {
    val shown = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        shown.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
    }
    Text(
        text = text,
        color = Palette.Text,
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        // The largest size that keeps the longest phrase on one line.
        fontSize = 48.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.graphicsLayer {
            alpha = shown.value
            translationY = (1f - shown.value) * 18f * density
        },
    )
}
