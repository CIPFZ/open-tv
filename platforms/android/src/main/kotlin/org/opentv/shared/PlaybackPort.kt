package org.opentv.shared

/**
 * Native playback boundary. Media3/ExoPlayer implements this on Android and
 * Android TV; UI state, channel switching, and persistence stay above it.
 */
interface PlaybackPort {
    suspend fun load(url: String, autoplay: Boolean = true)
    suspend fun play()
    fun pause()
    fun setVolume(value: Int)
    fun setMuted(value: Boolean)
    fun release()
}
