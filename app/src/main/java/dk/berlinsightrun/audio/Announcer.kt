package dk.berlinsightrun.audio

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dk.berlinsightrun.i18n.Lang
import android.speech.tts.Voice
import java.util.Locale

/**
 * Prioriteret lydkø. Rutevejledning afbryder fortællinger, fortællinger venter, til næste
 * sving er mindst 250 m væk, og lydfokus holdes, så længe køen taler. Alle metoder kaldes
 * fra hovedtråden.
 */
class Announcer(
    context: Context,
    initialLang: Lang,
    private val progressM: () -> Double,
    private val distanceToNextTurnM: () -> Double,
    private val onSpeaking: (Announcement?) -> Unit,
    private val onTtsProblem: (String?) -> Unit,
) {
    private var lang: Lang = initialLang
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val focus = VoiceAudioFocus(appContext.getSystemService(AudioManager::class.java))
    private val queue = mutableListOf<Announcement>()
    private var tts: TextToSpeech? = null
    private var ready = false
    private var paused = false
    private var current: Announcement? = null
    private var currentId: String? = null
    private var counter = 0
    private var player: MediaPlayer? = null
    private var lastSpoken: Announcement? = null
    private val releaseFocus = Runnable { focus.release() }
    private val recordedAudio: Set<String> = Lang.entries.flatMap { l ->
        runCatching { appContext.assets.list("audio/${l.code}")?.map { "audio/${l.code}/$it" } }.getOrNull() ?: emptyList()
    }.toSet()

    init {
        // Googles talemotor har neurale stemmer på alle fire sprog og lyder markant bedre end fx
        // Samsungs eller Xiaomis egne motorer. Den bruges, når den er installeret.
        val listener = TextToSpeech.OnInitListener { status -> main.post { onInit(status) } }
        tts = if (isInstalled(GOOGLE_TTS)) TextToSpeech(appContext, listener, GOOGLE_TTS) else TextToSpeech(appContext, listener)
    }

    private fun onInit(status: Int) {
        val engine = tts ?: return
        if (status != TextToSpeech.SUCCESS) {
            onTtsProblem(lang.strings.ttsFailed)
            return
        }
        applyTtsLanguage(engine)
        engine.setAudioAttributes(focus.attributes)
        engine.setSpeechRate(1.0f)
        engine.setPitch(1.0f)
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { main.post { onSegmentDone(utteranceId) } }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { main.post { onSegmentDone(utteranceId) } }
            override fun onError(utteranceId: String?, errorCode: Int) { main.post { onSegmentDone(utteranceId) } }
        })
        ready = true
        pump()
    }

    /** Skifter talesprog. Igangværende sætning tales færdig, næste besked kommer på det nye sprog. */
    fun setLanguage(newLang: Lang) {
        if (newLang == lang) return
        lang = newLang
        tts?.takeIf { ready }?.let { applyTtsLanguage(it) }
        // Beskeder i kø og den, der tales lige nu, oversættes, så de ikke læses op på det gamle sprog.
        queue.forEach { it.relocalize(newLang) }
        lastSpoken?.relocalize(newLang)
        val cur = current
        if (cur != null && !paused) {
            stopOutput(cur)
            cur.relocalize(newLang)
            play(cur)
        } else {
            cur?.relocalize(newLang)
        }
    }

    private fun applyTtsLanguage(engine: TextToSpeech) {
        val r = engine.setLanguage(lang.locale)
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            onTtsProblem(lang.strings.ttsMissing)
            engine.setLanguage(Locale.UK)
        } else {
            onTtsProblem(null)
            bestVoice(engine, lang.locale)?.let { engine.voice = it }
        }
    }

    private fun isInstalled(pkg: String): Boolean =
        runCatching { appContext.packageManager.getPackageInfo(pkg, 0); true }.getOrDefault(false)

    companion object {
        const val GOOGLE_TTS = "com.google.android.tts"

        /**
         * Vælger den bedste installerede stemme til sproget: højeste kvalitet, rigtigt land
         * (fx da-DK, en-GB), helst en stemme, der virker uden netforbindelse, og aldrig en
         * stemme, der ikke er hentet ned endnu.
         */
        fun bestVoice(engine: TextToSpeech, locale: Locale): Voice? {
            val voices = runCatching { engine.voices }.getOrNull() ?: return null
            return voices
                .filter { it.locale.language == locale.language }
                .filterNot { TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED in it.features }
                .maxByOrNull { scoreVoice(it.quality, it.locale.country == locale.country, it.isNetworkConnectionRequired, it.latency) }
        }

        /** Ren funktion, så valget kan unit-testes. */
        fun scoreVoice(quality: Int, countryMatches: Boolean, needsNetwork: Boolean, latency: Int): Int =
            quality * 10 + (if (countryMatches) 300 else 0) + (if (needsNetwork) -150 else 0) - latency
    }

    fun enqueue(a: Announcement) {
        val cur = current
        if (cur != null && a.priority < cur.priority) {
            stopOutput(cur)
            current = null
            queue.add(0, cur)
        }
        queue.add(a)
        val sorted = queue.sortedBy { it.priority } // stabil: samme prioritet bevarer rækkefølgen
        queue.clear()
        queue.addAll(sorted)
        pump()
    }

    /** Kaldes ved hver positionsopdatering, så ventende fortællinger kan starte efter et sving. */
    fun tick() = pump()

    val isSpeakingNarration: Boolean get() = current?.kind == Kind.NARRATION

    fun pause() {
        paused = true
        current?.let { stopOutput(it); queue.add(0, it) }
        current = null
        focus.release()
        onSpeaking(null)
    }

    fun resume() {
        paused = false
        pump()
    }

    fun skipNarration() {
        val cur = current ?: return
        if (cur.kind != Kind.NARRATION) return
        stopOutput(cur)
        current = null
        pump()
    }

    fun repeatLast() {
        lastSpoken?.let { enqueue(it.copyForRepeat()) }
    }

    fun clear() {
        current?.let { stopOutput(it) }
        current = null
        queue.clear()
        main.removeCallbacks(releaseFocus)
        focus.release()
        onSpeaking(null)
    }

    fun shutdown() {
        clear()
        main.removeCallbacksAndMessages(null)
        tts?.shutdown()
        tts = null
    }

    private fun pump() {
        if (paused || current != null || !ready) return
        val now = progressM()
        queue.removeAll { stale -> stale.staleAfterM?.let { now > it } == true }
        val next = queue.firstOrNull { it.kind != Kind.NARRATION || it.force || distanceToNextTurnM() > 250.0 }
        if (next == null) {
            main.removeCallbacks(releaseFocus)
            main.postDelayed(releaseFocus, 500)
            onSpeaking(null)
            return
        }
        queue.remove(next)
        current = next
        play(next)
    }

    private fun play(a: Announcement) {
        main.removeCallbacks(releaseFocus)
        focus.acquire()
        lastSpoken = a
        onSpeaking(a)
        val id = "u${++counter}"
        currentId = id

        val asset = a.poi?.audioAsset(a.currentLang.code)
        if (a.kind == Kind.NARRATION && asset != null && asset in recordedAudio) {
            playRecorded(a, asset, id)
            return
        }
        val engine = tts
        val sentence = a.sentences.getOrNull(a.index)
        if (engine == null || sentence == null) {
            main.postDelayed({ onSegmentDone(id) }, 300)
            return
        }
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f) }
        if (engine.speak(sentence, TextToSpeech.QUEUE_FLUSH, params, id) == TextToSpeech.ERROR) {
            main.postDelayed({ onSegmentDone(id) }, 300)
        }
    }

    private fun playRecorded(a: Announcement, asset: String, id: String) {
        val mp = MediaPlayer()
        player = mp
        try {
            appContext.assets.openFd(asset).use { fd -> mp.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length) }
            mp.setAudioAttributes(focus.attributes)
            mp.setOnCompletionListener {
                a.index = a.sentences.size - 1 // hele filen er én bid
                onSegmentDone(id)
            }
            mp.setOnErrorListener { _, _, _ -> onSegmentDone(id); true }
            mp.prepare()
            if (a.audioPositionMs > 0) mp.seekTo(a.audioPositionMs)
            mp.start()
        } catch (e: Exception) {
            mp.release()
            player = null
            // Faldback til tekst-til-tale
            val engine = tts ?: return onSegmentDone(id)
            engine.speak(a.sentences.getOrElse(a.index) { a.text }, TextToSpeech.QUEUE_FLUSH, null, id)
        }
    }

    private fun onSegmentDone(id: String?) {
        if (id == null || id != currentId) return
        val a = current ?: return
        player?.release()
        player = null
        a.audioPositionMs = 0
        a.index++
        if (a.index < a.sentences.size) {
            play(a)
        } else {
            a.onDone?.invoke()
            current = null
            currentId = null
            pump()
        }
    }

    private fun stopOutput(a: Announcement) {
        currentId = null
        player?.let {
            a.audioPositionMs = runCatching { it.currentPosition }.getOrDefault(0)
            it.release()
        }
        player = null
        tts?.stop()
    }
}
