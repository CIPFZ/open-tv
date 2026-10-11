package org.opentv.android

import android.content.Context
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import org.opentv.shared.PlaybackPort

/** Media3 implementation of the shared playback boundary. Call methods on the player thread. */
class Media3PlaybackPort(context: Context) : PlaybackPort {
    val player: ExoPlayer = ExoPlayer.Builder(context).build()
    private var lastAudibleVolume = 0.7f
    private var statusListener: ((state: String, detail: String?) -> Unit)? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> statusListener?.invoke("buffering", null)
                    Player.STATE_READY -> statusListener?.invoke(if (player.isPlaying) "playing" else "paused", null)
                    Player.STATE_ENDED -> statusListener?.invoke("error", "直播已结束或连接中断")
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (player.playbackState == Player.STATE_READY) {
                    statusListener?.invoke(if (isPlaying) "playing" else "paused", null)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                statusListener?.invoke("error", "Media3 播放失败：${error.errorCodeName}")
            }
        })
    }

    fun setStatusListener(listener: (state: String, detail: String?) -> Unit) {
        statusListener = listener
    }

    override suspend fun load(url: String, autoplay: Boolean) {
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.playWhenReady = autoplay
    }

    override suspend fun play() {
        player.play()
    }

    override fun pause() {
        player.pause()
    }

    override fun setVolume(value: Int) {
        val normalized = value.coerceIn(0, 100) / 100f
        if (normalized > 0f) lastAudibleVolume = normalized
        player.volume = normalized
    }

    override fun setMuted(value: Boolean) {
        player.volume = if (value) 0f else lastAudibleVolume
    }

    override fun release() {
        player.release()
    }
}
