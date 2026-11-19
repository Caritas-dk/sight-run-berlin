package dk.berlinsightrun.audio

import dk.berlinsightrun.i18n.Lang
import dk.berlinsightrun.route.Poi
import kotlin.math.floor

/** Lavere tal = højere prioritet. En besked med højere prioritet afbryder den igangværende. */
enum class Kind(val priority: Int) {
    OFF_ROUTE(1),
    TURN(2),
    SYSTEM(2),
    NARRATION(3),
    KM(4),
}

/**
 * En besked i lydkøen. [localize] laver titel og tekst på et givet sprog, så en besked, der
 * allerede står i kø eller bliver talt, kan skifte sprog, når brugeren vælger et nyt.
 */
class Announcement(
    val kind: Kind,
    val lang: Lang,
    /** Beskeden droppes, hvis løberen er nået forbi denne position (gade-meter), før den blev afspillet. */
    val staleAfterM: Double? = null,
    val poi: Poi? = null,
    /** Fortællinger venter normalt på, at næste sving er mindst 250 m væk; force springer det over. */
    val force: Boolean = false,
    val onDone: (() -> Unit)? = null,
    private val localize: (Lang) -> Pair<String, String>,
) {
    var currentLang: Lang = lang
        private set
    var title: String
        private set
    var text: String
        private set
    var sentences: List<String>
        private set
    var index: Int = 0
    /** Hvor langt en indspillet lydfil nåede, hvis den blev afbrudt. */
    var audioPositionMs: Int = 0

    init {
        val (t, x) = localize(lang)
        title = t
        text = x
        sentences = splitSentences(x)
    }

    val priority: Int get() = kind.priority

    /** Skifter sprog. En fortælling, der er halvvejs, fortsætter cirka samme sted i den nye tekst. */
    fun relocalize(newLang: Lang) {
        if (newLang == currentLang) return
        val fraction = if (sentences.isEmpty()) 0.0 else index.toDouble() / sentences.size
        val (t, x) = localize(newLang)
        title = t
        text = x
        sentences = splitSentences(x)
        index = floor(fraction * sentences.size).toInt().coerceIn(0, (sentences.size - 1).coerceAtLeast(0))
        audioPositionMs = 0
        currentLang = newLang
    }

    fun copyForRepeat(): Announcement =
        Announcement(kind, currentLang, null, poi, force = true, onDone = null, localize = localize)

    companion object {
        /** Deler ikke efter dag-tal som "17. Juni" eller "9. november". */
        private val SENTENCE = Regex("(?<=[.!?])(?<!\\b\\d{1,2}\\.)\\s+(?=[A-ZÆØÅÉÀÂÇÈÊÎÔÛÄÖÜ0-9])")
        fun splitSentences(text: String): List<String> =
            text.split(SENTENCE).map { it.trim() }.filter { it.isNotEmpty() }
    }
}
