package dk.berlinsightrun.i18n

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import kotlin.math.roundToInt

enum class Lang(val code: String, val locale: Locale, val label: String) {
    DA("da", Locale("da", "DK"), "Dansk"),
    DE("de", Locale.GERMANY, "Deutsch"),
    EN("en", Locale.UK, "English"),
    FR("fr", Locale.FRANCE, "Français");

    val strings: Strings
        get() = when (this) {
            DA -> Danish
            DE -> German
            EN -> English
            FR -> French
        }

    companion object {
        fun fromCode(code: String?): Lang? = entries.firstOrNull { it.code.equals(code, ignoreCase = true) }
        fun deviceDefault(): Lang = fromCode(Locale.getDefault().language) ?: EN
    }
}

/** Valgt sprog, delt mellem skærme og løbetjeneste og gemt mellem opstarter. */
object LanguageStore {
    private const val PREFS = "settings"
    private const val KEY = "language"
    private val _lang = MutableStateFlow(Lang.DA)
    val lang: StateFlow<Lang> = _lang.asStateFlow()
    @Volatile private var loaded = false

    fun init(context: Context) {
        if (loaded) return
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        _lang.value = Lang.fromCode(saved) ?: Lang.deviceDefault()
        loaded = true
    }

    fun set(context: Context, lang: Lang) {
        _lang.value = lang
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, lang.code).apply()
    }
}

/** Alle tekster i appen. Hvert sprog er et objekt nedenfor. */
abstract class Strings {
    abstract val decimalSeparator: Char

    // Forside
    abstract val tagline: String
    abstract val kmLabel: String
    abstract val sightsLabel: String
    abstract val etaLabel: String
    abstract val listTitle: String
    abstract val startRun: String
    abstract val tryDemo: String
    abstract val geofence: String
    abstract val close: String
    abstract val language: String

    // Demo-dialog
    abstract val demoTitle: String
    abstract val demoBody: String
    abstract val speed: String
    abstract fun startAtKm(km: String): String
    abstract val startDemo: String
    abstract val cancel: String

    // Under løbet
    abstract val kmOf: String
    abstract val time: String
    abstract val pace: String
    abstract fun nextPoi(name: String, km: String): String
    abstract val nextFinish: String
    abstract val goToStart: String
    abstract val offTitle: String
    abstract val offSub: String
    abstract val straightToFinish: String
    abstract fun eyebrowNarrating(n: Int, total: Int): String
    abstract val eyebrowFinish: String
    abstract val eyebrowGuidance: String
    abstract val eyebrowOff: String
    abstract val eyebrowKm: String
    abstract val musicDucked: String
    abstract val repeat: String
    abstract val pause: String
    abstract val resume: String
    abstract val skip: String
    abstract val stopRun: String
    abstract val stopTitle: String
    abstract val stopBody: String
    abstract val stopConfirm: String
    abstract val keepRunning: String

    // Resultat
    abstract val done: String
    abstract val summarySub: String
    abstract val avgPace: String
    abstract val heard: String
    abstract val kmRun: String
    abstract val toOverview: String

    // Beskeder
    abstract val needLocation: String
    abstract val noLocation: String
    abstract val ttsMissing: String
    abstract val ttsFailed: String
    abstract val notifStarting: String

    // Tale
    abstract val welcomeTitle: String
    abstract val welcome: String
    abstract val goToStartTitle: String
    abstract fun goToStartSpeech(distance: String): String
    abstract val turnAheadTitle: String
    abstract val turnNowTitle: String
    abstract fun turn(dir: Int, sharp: Boolean, street: String): String
    abstract fun preAnnounce(phrase: String): String
    abstract fun nowAnnounce(phrase: String): String
    abstract fun kmTitle(k: Int): String
    /** Ved hver kilometer: samlet tid, tid for seneste kilometer og gennemsnitsfart (sekunder). */
    abstract fun kmCall(k: Int, totalSec: Long, splitSec: Long, avgSec: Long): String
    abstract val lastKm: String
    abstract val avgPaceShort: String
    abstract val splitsTitle: String
    abstract val fastest: String
    abstract fun kmReached(k: Int): String

    // Startindikator
    abstract fun toStart(distance: String): String
    abstract val atStartTitle: String
    abstract val atStartSub: String
    abstract val searchingGps: String
    abstract val demoWalkOption: String
    abstract fun offRoute(street: String): String
    abstract val backOnTitle: String
    abstract val backOn: String
    abstract val finishTitle: String
    abstract fun distance(m: Double): String

    /** "5:48" */
    fun clock(sec: Long): String = if (sec >= 3600) "%d:%02d:%02d".format(sec / 3600, sec % 3600 / 60, sec % 60) else "%d:%02d".format(sec / 60, sec % 60)

    fun km(m: Double): String = String.format(Locale.ROOT, "%.1f", m / 1000.0).replace('.', decimalSeparator)
    fun shortDistance(m: Double): String = if (m < 1000) "${(m / 10).toInt() * 10} m" else "${km(m)} km"
    protected fun cap(s: String) = s.replaceFirstChar { it.uppercase() }
    protected fun roundedMetres(m: Double) = (m / 10).roundToInt() * 10
}

object Danish : Strings() {
    override val decimalSeparator = ','
    override val tagline = "Halvmarathon · fra TU Berlin til Brandenburger Tor"
    override val kmLabel = "kilometer"
    override val sightsLabel = "seværdigheder"
    override val etaLabel = "t ved 5:55/km"
    override val listTitle = "Seværdigheder på ruten"
    override val startRun = "Start løb"
    override val tryDemo = "Prøv demo-løb hjemmefra"
    override val geofence = "geofence"
    override val close = "Luk"
    override val language = "Sprog"
    override val demoTitle = "Demo-løb"
    override val demoBody = "En virtuel løber løber ruten, så du kan høre vejledning og fortællinger uden at være i Berlin. Start din musik først."
    override val speed = "Hastighed"
    override fun startAtKm(km: String) = "Start ved km $km"
    override val startDemo = "Start demo"
    override val cancel = "Annullér"
    override val kmOf = "km af 21,1"
    override val time = "tid"
    override val pace = "min/km"
    override fun nextPoi(name: String, km: String) = "Næste seværdighed: $name om $km km"
    override val nextFinish = "Næste: målet ved Brandenburger Tor"
    override val goToStart = "Gå til starten ved TU Berlin"
    override val offTitle = "Uden for ruten"
    override val offSub = "Vend om og løb tilbage"
    override val straightToFinish = "Ligeud til målet"
    override fun eyebrowNarrating(n: Int, total: Int) = "FORTÆLLER NU · $n AF $total"
    override val eyebrowFinish = "MÅL"
    override val eyebrowGuidance = "RUTEVEJLEDNING"
    override val eyebrowOff = "AFVIGELSE"
    override val eyebrowKm = "KILOMETER"
    override val musicDucked = "Musik dæmpet"
    override val repeat = "Gentag sidste besked"
    override val pause = "Pause"
    override val resume = "Fortsæt"
    override val skip = "Spring fortælling over"
    override val stopRun = "Afslut løb"
    override val stopTitle = "Afslut løbet?"
    override val stopBody = "Løbet stopper, og din tid nulstilles."
    override val stopConfirm = "Afslut"
    override val keepRunning = "Fortsæt løbet"
    override val done = "HALVMARATHON GENNEMFØRT"
    override val summarySub = "21,1 km fra TU Berlin til Brandenburger Tor"
    override val avgPace = "min/km i snit"
    override val heard = "fortællinger hørt"
    override val kmRun = "km løbet"
    override val toOverview = "Til oversigten"
    override val needLocation = "Appen skal kende din præcise placering for at guide dig. Giv adgang i indstillingerne, eller prøv demo-løb."
    override val noLocation = "Appen har ikke adgang til din placering. Giv adgang, eller brug demo-løb."
    override val ttsMissing = "Den danske stemme mangler. Installér den under Indstillinger › Tekst-til-tale. Indtil da bruges en anden stemme."
    override val ttsFailed = "Tekst-til-tale kunne ikke starte. Tjek Indstillinger › Tilgængelighed › Tekst-til-tale."
    override val notifStarting = "Løbet starter …"
    override val welcomeTitle = "Velkommen"
    override val welcome = "Velkommen til Berlin Sightrun. Løbet starter nu ved TU Berlin. Løb mod vest ad Straße des 17. Juni."
    override val goToStartTitle = "Gå til start"
    override fun goToStartSpeech(distance: String) = "Du er $distance fra starten. Gå hen til TU Berlins hovedbygning på Straße des 17. Juni. Løbet starter automatisk, når du er der."
    override val turnAheadTitle = "Sving forude"
    override val turnNowTitle = "Sving nu"
    override fun turn(dir: Int, sharp: Boolean, street: String): String {
        val side = if (dir > 0) "højre" else "venstre"
        val prep = when {
            street.contains("brücke", ignoreCase = true) -> "over $street"
            street.endsWith("platz", ignoreCase = true) || street.endsWith("Markt") -> "ind på $street"
            else -> "ad $street"
        }
        return "drej ${if (sharp) "skarpt " else ""}til $side $prep"
    }
    override fun preAnnounce(phrase: String) = "Om 100 meter, $phrase."
    override fun nowAnnounce(phrase: String) = "${cap(phrase)} nu."
    override fun kmTitle(k: Int) = "$k kilometer"
    override fun kmCall(k: Int, totalSec: Long, splitSec: Long, avgSec: Long) = buildString {
        append("$k kilometer. Tid: ${duration(totalSec)}. Sidste kilometer: ${pace(splitSec)}.")
        if (k > 1) append(" Gennemsnit: ${pace(avgSec)} per kilometer.")
    }
    private fun duration(sec: Long): String {
        val h = sec / 3600; val m = sec % 3600 / 60; val s = sec % 60
        return if (h > 0) "$h ${if (h == 1L) "time" else "timer"} og $m ${if (m == 1L) "minut" else "minutter"}"
        else "$m ${if (m == 1L) "minut" else "minutter"} og $s sekunder"
    }
    private fun pace(sec: Long): String = "${sec / 60} ${if (sec / 60 == 1L) "minut" else "minutter"}" + if (sec % 60 > 0) " ${sec % 60}" else ""
    override val lastKm = "sidste km"
    override fun toStart(distance: String) = "$distance til start"
    override val atStartTitle = "Du er ved start"
    override val atStartSub = "Løbet starter nu"
    override val searchingGps = "Finder din position …"
    override val demoWalkOption = "Start ca. 440 m fra startpunktet"
    override val avgPaceShort = "snit min/km"
    override val splitsTitle = "Kilometertider"
    override val fastest = "hurtigste"
    override fun kmReached(k: Int) = "KILOMETER $k"
    override fun offRoute(street: String) = "Du er drejet væk fra ruten. Vend om, og løb tilbage til $street."
    override val backOnTitle = "Tilbage på ruten"
    override val backOn = "Du er tilbage på ruten. Fortsæt."
    override val finishTitle = "Mål"
    override fun distance(m: Double) = if (m < 1000) "${roundedMetres(m)} meter" else "${km(m)} kilometer"
}

object German : Strings() {
    override val decimalSeparator = ','
    override val tagline = "Halbmarathon · von der TU Berlin zum Brandenburger Tor"
    override val kmLabel = "Kilometer"
    override val sightsLabel = "Sehenswürdigkeiten"
    override val etaLabel = "Std. bei 5:55/km"
    override val listTitle = "Sehenswürdigkeiten auf der Route"
    override val startRun = "Lauf starten"
    override val tryDemo = "Demo-Lauf von zu Hause"
    override val geofence = "Geofence"
    override val close = "Schließen"
    override val language = "Sprache"
    override val demoTitle = "Demo-Lauf"
    override val demoBody = "Ein virtueller Läufer läuft die Route, damit du Navigation und Geschichten hören kannst, ohne in Berlin zu sein. Starte zuerst deine Musik."
    override val speed = "Geschwindigkeit"
    override fun startAtKm(km: String) = "Start bei km $km"
    override val startDemo = "Demo starten"
    override val cancel = "Abbrechen"
    override val kmOf = "km von 21,1"
    override val time = "Zeit"
    override val pace = "min/km"
    override fun nextPoi(name: String, km: String) = "Nächste Sehenswürdigkeit: $name in $km km"
    override val nextFinish = "Als Nächstes: das Ziel am Brandenburger Tor"
    override val goToStart = "Geh zum Start an der TU Berlin"
    override val offTitle = "Abseits der Route"
    override val offSub = "Dreh um und lauf zurück"
    override val straightToFinish = "Geradeaus ins Ziel"
    override fun eyebrowNarrating(n: Int, total: Int) = "ERZÄHLT GERADE · $n VON $total"
    override val eyebrowFinish = "ZIEL"
    override val eyebrowGuidance = "NAVIGATION"
    override val eyebrowOff = "ABWEICHUNG"
    override val eyebrowKm = "KILOMETER"
    override val musicDucked = "Musik leiser"
    override val repeat = "Letzte Ansage wiederholen"
    override val pause = "Pause"
    override val resume = "Weiter"
    override val skip = "Geschichte überspringen"
    override val stopRun = "Lauf beenden"
    override val stopTitle = "Lauf beenden?"
    override val stopBody = "Der Lauf wird gestoppt und deine Zeit zurückgesetzt."
    override val stopConfirm = "Beenden"
    override val keepRunning = "Weiterlaufen"
    override val done = "HALBMARATHON GESCHAFFT"
    override val summarySub = "21,1 km von der TU Berlin zum Brandenburger Tor"
    override val avgPace = "min/km im Schnitt"
    override val heard = "Geschichten gehört"
    override val kmRun = "km gelaufen"
    override val toOverview = "Zur Übersicht"
    override val needLocation = "Die App braucht deinen genauen Standort, um dich zu führen. Erlaube den Zugriff in den Einstellungen oder probiere den Demo-Lauf."
    override val noLocation = "Die App hat keinen Zugriff auf deinen Standort. Erlaube den Zugriff oder nutze den Demo-Lauf."
    override val ttsMissing = "Die deutsche Stimme fehlt. Installiere sie unter Einstellungen › Sprachausgabe. Bis dahin wird eine andere Stimme verwendet."
    override val ttsFailed = "Die Sprachausgabe konnte nicht starten. Prüfe Einstellungen › Bedienungshilfen › Sprachausgabe."
    override val notifStarting = "Der Lauf startet …"
    override val welcomeTitle = "Willkommen"
    override val welcome = "Willkommen bei Berlin Sightrun. Der Lauf startet jetzt an der TU Berlin. Lauf auf der Straße des 17. Juni nach Westen."
    override val goToStartTitle = "Zum Start"
    override fun goToStartSpeech(distance: String) = "Du bist $distance vom Start entfernt. Geh zum Hauptgebäude der TU Berlin an der Straße des 17. Juni. Der Lauf startet automatisch, wenn du dort bist."
    override val turnAheadTitle = "Abbiegung voraus"
    override val turnNowTitle = "Jetzt abbiegen"
    override fun turn(dir: Int, sharp: Boolean, street: String): String {
        val side = if (dir > 0) "rechts" else "links"
        val prep = when {
            street.contains("brücke", ignoreCase = true) -> "über die $street"
            street.endsWith("straße", ignoreCase = true) -> "in die $street"
            street.endsWith("allee", ignoreCase = true) -> "auf die $street"
            street.endsWith("damm", ignoreCase = true) || street.endsWith("platz", ignoreCase = true) -> "auf den $street"
            else -> "Richtung $street"
        }
        return "${if (sharp) "scharf " else ""}$side abbiegen $prep"
    }
    override fun preAnnounce(phrase: String) = "In 100 Metern $phrase."
    override fun nowAnnounce(phrase: String) = "Jetzt $phrase."
    override fun kmTitle(k: Int) = "$k Kilometer"
    override fun kmCall(k: Int, totalSec: Long, splitSec: Long, avgSec: Long) = buildString {
        append("$k Kilometer. Zeit: ${duration(totalSec)}. Letzter Kilometer: ${pace(splitSec)}.")
        if (k > 1) append(" Schnitt: ${pace(avgSec)} pro Kilometer.")
    }
    private fun duration(sec: Long): String {
        val h = sec / 3600; val m = sec % 3600 / 60; val s = sec % 60
        return if (h > 0) "$h ${if (h == 1L) "Stunde" else "Stunden"} und $m ${if (m == 1L) "Minute" else "Minuten"}"
        else "$m ${if (m == 1L) "Minute" else "Minuten"} und $s Sekunden"
    }
    private fun pace(sec: Long): String = "${sec / 60} ${if (sec / 60 == 1L) "Minute" else "Minuten"}" + if (sec % 60 > 0) " ${sec % 60}" else ""
    override val lastKm = "letzter km"
    override fun toStart(distance: String) = "$distance bis zum Start"
    override val atStartTitle = "Du bist am Start"
    override val atStartSub = "Der Lauf beginnt jetzt"
    override val searchingGps = "Standort wird gesucht …"
    override val demoWalkOption = "Ca. 440 m vom Start entfernt beginnen"
    override val avgPaceShort = "Schnitt min/km"
    override val splitsTitle = "Kilometerzeiten"
    override val fastest = "schnellster"
    override fun kmReached(k: Int) = "KILOMETER $k"
    override fun offRoute(street: String) = "Du hast die Route verlassen. Dreh um und lauf zurück Richtung $street."
    override val backOnTitle = "Zurück auf der Route"
    override val backOn = "Du bist wieder auf der Route. Weiter so."
    override val finishTitle = "Ziel"
    override fun distance(m: Double) = if (m < 1000) "${roundedMetres(m)} Meter" else "${km(m)} Kilometer"
}

object English : Strings() {
    override val decimalSeparator = '.'
    override val tagline = "Half marathon · from TU Berlin to the Brandenburg Gate"
    override val kmLabel = "kilometres"
    override val sightsLabel = "sights"
    override val etaLabel = "h at 5:55/km"
    override val listTitle = "Sights on the route"
    override val startRun = "Start run"
    override val tryDemo = "Try a demo run from home"
    override val geofence = "geofence"
    override val close = "Close"
    override val language = "Language"
    override val demoTitle = "Demo run"
    override val demoBody = "A virtual runner runs the route, so you can hear the guidance and stories without being in Berlin. Start your music first."
    override val speed = "Speed"
    override fun startAtKm(km: String) = "Start at km $km"
    override val startDemo = "Start demo"
    override val cancel = "Cancel"
    override val kmOf = "km of 21.1"
    override val time = "time"
    override val pace = "min/km"
    override fun nextPoi(name: String, km: String) = "Next sight: $name in $km km"
    override val nextFinish = "Next: the finish at the Brandenburg Gate"
    override val goToStart = "Walk to the start at TU Berlin"
    override val offTitle = "Off route"
    override val offSub = "Turn around and run back"
    override val straightToFinish = "Straight on to the finish"
    override fun eyebrowNarrating(n: Int, total: Int) = "NOW TELLING · $n OF $total"
    override val eyebrowFinish = "FINISH"
    override val eyebrowGuidance = "ROUTE GUIDANCE"
    override val eyebrowOff = "OFF ROUTE"
    override val eyebrowKm = "KILOMETRE"
    override val musicDucked = "Music lowered"
    override val repeat = "Repeat last message"
    override val pause = "Pause"
    override val resume = "Resume"
    override val skip = "Skip story"
    override val stopRun = "End run"
    override val stopTitle = "End the run?"
    override val stopBody = "The run stops and your time is reset."
    override val stopConfirm = "End"
    override val keepRunning = "Keep running"
    override val done = "HALF MARATHON COMPLETED"
    override val summarySub = "21.1 km from TU Berlin to the Brandenburg Gate"
    override val avgPace = "avg min/km"
    override val heard = "stories heard"
    override val kmRun = "km run"
    override val toOverview = "Back to overview"
    override val needLocation = "The app needs your precise location to guide you. Allow access in settings, or try the demo run."
    override val noLocation = "The app has no access to your location. Allow access, or use the demo run."
    override val ttsMissing = "The English voice is missing. Install it under Settings › Text-to-speech. Until then another voice is used."
    override val ttsFailed = "Text-to-speech could not start. Check Settings › Accessibility › Text-to-speech."
    override val notifStarting = "The run is starting …"
    override val welcomeTitle = "Welcome"
    override val welcome = "Welcome to Berlin Sightrun. The run starts now at TU Berlin. Run west along Straße des 17. Juni."
    override val goToStartTitle = "Go to the start"
    override fun goToStartSpeech(distance: String) = "You are $distance from the start. Walk to the TU Berlin main building on Straße des 17. Juni. The run starts automatically when you get there."
    override val turnAheadTitle = "Turn ahead"
    override val turnNowTitle = "Turn now"
    override fun turn(dir: Int, sharp: Boolean, street: String): String {
        val side = if (dir > 0) "right" else "left"
        val prep = when {
            street.contains("brücke", ignoreCase = true) -> "over $street"
            else -> "onto $street"
        }
        return "turn ${if (sharp) "sharp " else ""}$side $prep"
    }
    override fun preAnnounce(phrase: String) = "In 100 metres, $phrase."
    override fun nowAnnounce(phrase: String) = "${cap(phrase)} now."
    override fun kmTitle(k: Int) = if (k == 1) "1 kilometre" else "$k kilometres"
    override fun kmCall(k: Int, totalSec: Long, splitSec: Long, avgSec: Long) = buildString {
        append("${kmTitle(k)}. Time: ${duration(totalSec)}. Last kilometre: ${pace(splitSec)}.")
        if (k > 1) append(" Average: ${pace(avgSec)} per kilometre.")
    }
    private fun duration(sec: Long): String {
        val h = sec / 3600; val m = sec % 3600 / 60; val s = sec % 60
        return if (h > 0) "$h ${if (h == 1L) "hour" else "hours"} $m ${if (m == 1L) "minute" else "minutes"}"
        else "$m ${if (m == 1L) "minute" else "minutes"} $s seconds"
    }
    private fun pace(sec: Long): String = "${sec / 60} ${if (sec / 60 == 1L) "minute" else "minutes"}" + if (sec % 60 > 0) " ${sec % 60}" else ""
    override val lastKm = "last km"
    override fun toStart(distance: String) = "$distance to the start"
    override val atStartTitle = "You are at the start"
    override val atStartSub = "The run starts now"
    override val searchingGps = "Finding your position …"
    override val demoWalkOption = "Begin about 440 m from the start"
    override val avgPaceShort = "avg min/km"
    override val splitsTitle = "Kilometre splits"
    override val fastest = "fastest"
    override fun kmReached(k: Int) = "KILOMETRE $k"
    override fun offRoute(street: String) = "You have left the route. Turn around and run back to $street."
    override val backOnTitle = "Back on route"
    override val backOn = "You are back on the route. Keep going."
    override val finishTitle = "Finish"
    override fun distance(m: Double) = if (m < 1000) "${roundedMetres(m)} metres" else "${km(m)} kilometres"
}

object French : Strings() {
    override val decimalSeparator = ','
    override val tagline = "Semi-marathon · de la TU Berlin à la porte de Brandebourg"
    override val kmLabel = "kilomètres"
    override val sightsLabel = "monuments"
    override val etaLabel = "h à 5:55/km"
    override val listTitle = "Monuments sur le parcours"
    override val startRun = "Démarrer la course"
    override val tryDemo = "Essayer une course démo"
    override val geofence = "géorepérage"
    override val close = "Fermer"
    override val language = "Langue"
    override val demoTitle = "Course démo"
    override val demoBody = "Un coureur virtuel parcourt l'itinéraire pour que vous entendiez le guidage et les récits sans être à Berlin. Lancez d'abord votre musique."
    override val speed = "Vitesse"
    override fun startAtKm(km: String) = "Départ au km $km"
    override val startDemo = "Lancer la démo"
    override val cancel = "Annuler"
    override val kmOf = "km sur 21,1"
    override val time = "temps"
    override val pace = "min/km"
    override fun nextPoi(name: String, km: String) = "Prochain monument : $name dans $km km"
    override val nextFinish = "Ensuite : l'arrivée à la porte de Brandebourg"
    override val goToStart = "Rendez-vous au départ, à la TU Berlin"
    override val offTitle = "Hors parcours"
    override val offSub = "Faites demi-tour"
    override val straightToFinish = "Tout droit jusqu'à l'arrivée"
    override fun eyebrowNarrating(n: Int, total: Int) = "RÉCIT EN COURS · $n SUR $total"
    override val eyebrowFinish = "ARRIVÉE"
    override val eyebrowGuidance = "GUIDAGE"
    override val eyebrowOff = "ÉCART"
    override val eyebrowKm = "KILOMÈTRE"
    override val musicDucked = "Musique baissée"
    override val repeat = "Répéter le dernier message"
    override val pause = "Pause"
    override val resume = "Reprendre"
    override val skip = "Passer le récit"
    override val stopRun = "Terminer la course"
    override val stopTitle = "Terminer la course ?"
    override val stopBody = "La course s'arrête et votre temps est remis à zéro."
    override val stopConfirm = "Terminer"
    override val keepRunning = "Continuer"
    override val done = "SEMI-MARATHON TERMINÉ"
    override val summarySub = "21,1 km de la TU Berlin à la porte de Brandebourg"
    override val avgPace = "min/km en moyenne"
    override val heard = "récits écoutés"
    override val kmRun = "km parcourus"
    override val toOverview = "Retour à l'aperçu"
    override val needLocation = "L'application a besoin de votre position précise pour vous guider. Autorisez l'accès dans les réglages ou essayez la course démo."
    override val noLocation = "L'application n'a pas accès à votre position. Autorisez l'accès ou utilisez la course démo."
    override val ttsMissing = "La voix française est absente. Installez-la dans Réglages › Synthèse vocale. En attendant, une autre voix est utilisée."
    override val ttsFailed = "La synthèse vocale n'a pas pu démarrer. Vérifiez Réglages › Accessibilité › Synthèse vocale."
    override val notifStarting = "La course commence …"
    override val welcomeTitle = "Bienvenue"
    override val welcome = "Bienvenue sur Berlin Sightrun. La course commence maintenant à la TU Berlin. Courez vers l'ouest sur la Straße des 17. Juni."
    override val goToStartTitle = "Rejoindre le départ"
    override fun goToStartSpeech(distance: String) = "Vous êtes à $distance du départ. Rendez-vous devant le bâtiment principal de la TU Berlin, sur la Straße des 17. Juni. La course démarre automatiquement à votre arrivée."
    override val turnAheadTitle = "Virage à venir"
    override val turnNowTitle = "Tournez maintenant"
    private fun art(s: String) = when {
        s == "Unter den Linden" -> s
        Regex("(straße|allee|brücke)$", RegexOption.IGNORE_CASE).containsMatchIn(s) -> "la $s"
        else -> "le $s"
    }
    override fun turn(dir: Int, sharp: Boolean, street: String): String {
        val side = if (dir > 0) "droite" else "gauche"
        val prep = if (street.contains("brücke", ignoreCase = true)) "pour traverser ${art(street)}" else "vers ${art(street)}"
        return "tournez ${if (sharp) "franchement " else ""}à $side $prep"
    }
    override fun preAnnounce(phrase: String) = "Dans 100 mètres, $phrase."
    override fun nowAnnounce(phrase: String) = "${cap(phrase)} maintenant."
    override fun kmTitle(k: Int) = if (k == 1) "1 kilomètre" else "$k kilomètres"
    override fun kmCall(k: Int, totalSec: Long, splitSec: Long, avgSec: Long) = buildString {
        append("${kmTitle(k)}. Temps : ${duration(totalSec)}. Dernier kilomètre : ${pace(splitSec)}.")
        if (k > 1) append(" Moyenne : ${pace(avgSec)} au kilomètre.")
    }
    private fun duration(sec: Long): String {
        val h = sec / 3600; val m = sec % 3600 / 60; val s = sec % 60
        return if (h > 0) "$h ${if (h == 1L) "heure" else "heures"} $m"
        else "$m ${if (m <= 1L) "minute" else "minutes"} $s"
    }
    private fun pace(sec: Long): String = "${sec / 60} ${if (sec / 60 <= 1L) "minute" else "minutes"}" + if (sec % 60 > 0) " ${sec % 60}" else ""
    override val lastKm = "dernier km"
    override fun toStart(distance: String) = "$distance du départ"
    override val atStartTitle = "Vous êtes au départ"
    override val atStartSub = "La course commence"
    override val searchingGps = "Recherche de votre position …"
    override val demoWalkOption = "Commencer à environ 440 m du départ"
    override val avgPaceShort = "moy. min/km"
    override val splitsTitle = "Temps au kilomètre"
    override val fastest = "le plus rapide"
    override fun kmReached(k: Int) = "KILOMÈTRE $k"
    override fun offRoute(street: String) = "Vous avez quitté le parcours. Faites demi-tour et revenez vers ${art(street)}."
    override val backOnTitle = "De retour sur le parcours"
    override val backOn = "Vous êtes de retour sur le parcours. Continuez."
    override val finishTitle = "Arrivée"
    override fun distance(m: Double) = if (m < 1000) "${roundedMetres(m)} mètres" else "${km(m)} kilomètres"
}
