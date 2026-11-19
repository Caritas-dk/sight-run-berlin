package dk.berlinsightrun.audio

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager

/**
 * Beder om midlertidig lydfokus med dæmpning, mens appen taler. Musikappen (Spotify,
 * YouTube Music osv.) sænker selv lyden og skruer op igen, når fokus slippes.
 */
class VoiceAudioFocus(private val audioManager: AudioManager) {

    val attributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .setWillPauseWhenDucked(false)
        .setOnAudioFocusChangeListener { }
        .build()

    var held = false
        private set

    fun acquire(): Boolean {
        if (held) return true
        held = audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return held
    }

    fun release() {
        if (!held) return
        audioManager.abandonAudioFocusRequest(request)
        held = false
    }
}
