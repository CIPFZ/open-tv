package org.opentv.android

import android.content.Context
import org.opentv.shared.AppConfig
import org.opentv.shared.PlaybackBackend
import org.opentv.shared.PlaylistKind
import org.opentv.shared.PlaylistSource

/**
 * Small local persistence boundary for the Android client.
 *
 * The keys intentionally mirror the portable v1 configuration names. The full
 * JSON document is kept behind this boundary so the native playlist screen can
 * use the same migration path as the desktop client.
 */
class AndroidConfigStore(context: Context) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun load(): AppConfig {
        preferences.getString(KEY_CONFIG_JSON, null)?.let { return AndroidConfigCodec.decode(it, defaultConfig()) }
        val defaults = defaultConfig()
        val settings = defaults.settings.copy(
            autoPlay = preferences.getBoolean(KEY_AUTO_PLAY, defaults.settings.autoPlay),
            rememberVolume = preferences.getBoolean(KEY_REMEMBER_VOLUME, defaults.settings.rememberVolume),
            autoFullscreen = preferences.getBoolean(KEY_AUTO_FULLSCREEN, defaults.settings.autoFullscreen),
            backend = preferences.getString(KEY_BACKEND, defaults.settings.backend.name)
                ?.let { value -> runCatching { PlaybackBackend.valueOf(value) }.getOrNull() }
                ?: defaults.settings.backend,
            networkTimeout = preferences.getInt(KEY_NETWORK_TIMEOUT, defaults.settings.networkTimeout),
            retryCount = preferences.getInt(KEY_RETRY_COUNT, defaults.settings.retryCount),
            defaultPlaylistId = preferences.getString(KEY_DEFAULT_PLAYLIST, defaults.settings.defaultPlaylistId).orEmpty(),
            defaultChannelId = preferences.getString(KEY_DEFAULT_CHANNEL, defaults.settings.defaultChannelId).orEmpty()
        )
        return defaults.copy(
            activePlaylistId = preferences.getString(KEY_ACTIVE_PLAYLIST, defaults.activePlaylistId).orEmpty(),
            lastChannelId = preferences.getString(KEY_LAST_CHANNEL, defaults.lastChannelId).orEmpty(),
            volume = preferences.getInt(KEY_VOLUME, defaults.volume).coerceIn(0, 100),
            muted = preferences.getBoolean(KEY_MUTED, defaults.muted),
            settings = settings
        )
    }

    fun save(config: AppConfig) {
        preferences.edit().putString(KEY_CONFIG_JSON, AndroidConfigCodec.encode(config)).apply()
        val settings = config.settings
        preferences.edit()
            .putInt(KEY_SCHEMA_VERSION, config.schemaVersion)
            .putString(KEY_ACTIVE_PLAYLIST, config.activePlaylistId)
            .putString(KEY_LAST_CHANNEL, config.lastChannelId)
            .putInt(KEY_VOLUME, config.volume.coerceIn(0, 100))
            .putBoolean(KEY_MUTED, config.muted)
            .putBoolean(KEY_AUTO_PLAY, settings.autoPlay)
            .putBoolean(KEY_REMEMBER_VOLUME, settings.rememberVolume)
            .putBoolean(KEY_AUTO_FULLSCREEN, settings.autoFullscreen)
            .putString(KEY_BACKEND, settings.backend.name)
            .putInt(KEY_NETWORK_TIMEOUT, settings.networkTimeout)
            .putInt(KEY_RETRY_COUNT, settings.retryCount)
            .putString(KEY_DEFAULT_PLAYLIST, settings.defaultPlaylistId)
            .putString(KEY_DEFAULT_CHANNEL, settings.defaultChannelId)
            .apply()
    }

    private fun defaultConfig(): AppConfig {
        val sample = PlaylistSource(
            id = "development-cn",
            name = "研发样本 · 中国频道",
            kind = PlaylistKind.REMOTE,
            url = "https://iptv-org.github.io/iptv/countries/cn.m3u",
            enabled = true
        )
        return AppConfig(playlists = listOf(sample), activePlaylistId = sample.id)
    }

    private companion object {
        const val FILE_NAME = "open_tv_config_v1"
        const val KEY_CONFIG_JSON = "configJson"
        const val KEY_SCHEMA_VERSION = "schemaVersion"
        const val KEY_ACTIVE_PLAYLIST = "activePlaylistId"
        const val KEY_LAST_CHANNEL = "lastChannelId"
        const val KEY_VOLUME = "volume"
        const val KEY_MUTED = "muted"
        const val KEY_AUTO_PLAY = "autoPlay"
        const val KEY_REMEMBER_VOLUME = "rememberVolume"
        const val KEY_AUTO_FULLSCREEN = "autoFullscreen"
        const val KEY_BACKEND = "backend"
        const val KEY_NETWORK_TIMEOUT = "networkTimeout"
        const val KEY_RETRY_COUNT = "retryCount"
        const val KEY_DEFAULT_PLAYLIST = "defaultPlaylistId"
        const val KEY_DEFAULT_CHANNEL = "defaultChannelId"
    }
}
