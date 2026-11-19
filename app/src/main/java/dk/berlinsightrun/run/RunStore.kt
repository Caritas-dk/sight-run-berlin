package dk.berlinsightrun.run

import dk.berlinsightrun.audio.Kind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class Phase { IDLE, RUNNING, FINISHED }

data class Speaking(
    val kind: Kind,
    val title: String,
    val line: String,
    val part: Int,
    val parts: Int,
    val poiNumber: Int?,
)

data class RunUiState(
    val phase: Phase = Phase.IDLE,
    val demo: Boolean = false,
    val demoSpeed: Int = 10,
    val paused: Boolean = false,
    val progressM: Double = 0.0,
    val totalM: Double = 21_097.5,
    val elapsedSec: Long = 0,
    val lat: Double? = null,
    val lon: Double? = null,
    val distanceToStartM: Double? = null,
    val atStart: Boolean = false,
    val turnDistanceM: Double? = null,
    val turnText: String = "",
    val turnDir: Int = 0,
    val turnSharp: Boolean = false,
    val offRoute: Boolean = false,
    val speaking: Speaking? = null,
    val nextPoiName: String? = null,
    val nextPoiDistanceM: Double? = null,
    val playedPois: Set<Int> = emptySet(),
    val heard: Int = 0,
    val notice: String? = null,
    val splits: List<KmSplit> = emptyList(),
) {
    val lastSplit: KmSplit? get() = splits.lastOrNull()
    val fastestSplit: KmSplit? get() = splits.minByOrNull { it.splitSec }
    /** Sekunder pr. km. */
    val paceSecPerKm: Long? get() = if (progressM > 200) (elapsedSec / (progressM / 1000.0)).toLong() else null
}

/** Delt tilstand mellem tjenesten og skærmene. */
object RunStore {
    private val _state = MutableStateFlow(RunUiState())
    val state: StateFlow<RunUiState> = _state.asStateFlow()

    fun update(f: (RunUiState) -> RunUiState) {
        _state.value = f(_state.value)
    }

    fun reset() {
        _state.value = RunUiState()
    }
}

object Format {
    fun time(sec: Long): String {
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    fun pace(secPerKm: Long?): String = secPerKm?.let { "%d:%02d".format(it / 60, it % 60) } ?: "–:––"

    fun km(m: Double): String = "%.1f".format(m / 1000.0).replace('.', ',')

    fun shortDistance(m: Double): String = if (m < 1000) "${(m / 10).toInt() * 10} m" else "${km(m)} km"
}
