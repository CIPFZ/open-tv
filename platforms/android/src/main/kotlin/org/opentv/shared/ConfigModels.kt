package org.opentv.shared

/** Portable configuration model. Keep field names aligned with open-tv-config.v1.schema.json. */
data class AppConfig(
    val schemaVersion: Int = 1,
    val playlists: List<PlaylistSource> = emptyList(),
    val activePlaylistId: String = "",
    val lastChannelId: String = "",
    /** Legacy compatibility only; the TV UI does not display or update this list. */
    val recent: List<String> = emptyList(),
    val volume: Int = 70,
    val muted: Boolean = false,
    val settings: PlaybackPreferences = PlaybackPreferences(),
    val epg: EpgSource = EpgSource()
)

data class PlaylistSource(
    val id: String,
    val name: String,
    val kind: PlaylistKind,
    val url: String = "",
    val enabled: Boolean = true,
    val channels: List<Channel> = emptyList(),
    val text: String? = null,
    val lastLoadedAt: Long? = null,
    val error: String? = null,
    val health: SourceHealth = SourceHealth.UNKNOWN,
    val skippedCount: Int = 0,
    val lastError: SourceError? = null
)

enum class PlaylistKind { REMOTE, LOCAL }
enum class SourceHealth { UNKNOWN, LOADING, HEALTHY, DEGRADED, ERROR }

data class Channel(
    val id: String,
    val playlistId: String,
    val number: Int,
    val name: String,
    val url: String,
    val group: String = "其他",
    val tvgId: String = "",
    val logo: String = "",
    val language: String = "",
    val country: String = ""
)

data class SourceError(
    val code: SourceErrorCode,
    val message: String,
    val at: Long
)

enum class SourceErrorCode { NETWORK, TIMEOUT, RESTRICTED, HTTP, FORMAT, EMPTY, INSECURE, UNKNOWN }

data class PlaybackPreferences(
    val autoPlay: Boolean = true,
    val rememberVolume: Boolean = true,
    val autoFullscreen: Boolean = false,
    val backend: PlaybackBackend = PlaybackBackend.AUTO,
    val networkTimeout: Int = 12_000,
    val retryCount: Int = 2,
    /** Retained for migration compatibility; the current TV UI does not edit these. */
    val defaultPlaylistId: String = "",
    val defaultChannelId: String = ""
)

enum class PlaybackBackend { AUTO, NATIVE, HLS }

data class EpgSource(
    val url: String = "",
    val lastLoadedAt: Long? = null
)

data class Program(
    val channelId: String,
    val startAt: Long,
    val endAt: Long,
    val title: String,
    val description: String = ""
)
