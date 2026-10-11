package org.opentv.android

import org.json.JSONArray
import org.json.JSONObject
import org.opentv.shared.AppConfig
import org.opentv.shared.Channel
import org.opentv.shared.EpgSource
import org.opentv.shared.PlaybackBackend
import org.opentv.shared.PlaybackPreferences
import org.opentv.shared.PlaylistKind
import org.opentv.shared.PlaylistSource
import org.opentv.shared.SourceError
import org.opentv.shared.SourceErrorCode
import org.opentv.shared.SourceHealth

/** JSON v1 codec kept field-compatible with schemas/open-tv-config.v1.schema.json. */
object AndroidConfigCodec {
    fun encode(config: AppConfig): String {
        val root = JSONObject()
            .put("schemaVersion", config.schemaVersion)
            .put("playlists", JSONArray().apply { config.playlists.forEach { put(encodePlaylist(it)) } })
            .put("activePlaylistId", config.activePlaylistId)
            .put("lastChannelId", config.lastChannelId)
            .put("recent", JSONArray().apply { config.recent.forEach(::put) })
            .put("volume", config.volume.coerceIn(0, 100))
            .put("muted", config.muted)
            .put("settings", encodeSettings(config.settings))
            .put("epg", encodeEpg(config.epg))
        return root.toString()
    }

    fun decode(raw: String, fallback: AppConfig = AppConfig()): AppConfig = runCatching {
        decodeStrict(raw, fallback)
    }.getOrDefault(fallback)

    /** Import path: reject malformed or unknown-version JSON instead of silently replacing it. */
    fun decodeStrict(raw: String, fallback: AppConfig = AppConfig()): AppConfig {
        val root = JSONObject(raw)
        val schemaVersion = root.optInt("schemaVersion", 1)
        require(schemaVersion in 0..1) { "不支持的配置版本" }
        val settings = root.optJSONObject("settings")?.let { decodeSettings(it, fallback.settings) } ?: fallback.settings
        return fallback.copy(
            schemaVersion = 1,
            playlists = root.optJSONArray("playlists")?.let { array -> (0 until array.length()).mapNotNull { decodePlaylist(array.optJSONObject(it)) } } ?: emptyList(),
            activePlaylistId = root.optString("activePlaylistId", ""),
            lastChannelId = root.optString("lastChannelId", ""),
            recent = root.optJSONArray("recent")?.strings().orEmpty(),
            volume = root.optInt("volume", fallback.volume).coerceIn(0, 100),
            muted = root.optBoolean("muted", fallback.muted),
            settings = settings,
            epg = root.optJSONObject("epg")?.let(::decodeEpg) ?: fallback.epg
        )
    }

    private fun encodeSettings(settings: PlaybackPreferences) = JSONObject()
        .put("autoPlay", settings.autoPlay)
        .put("rememberVolume", settings.rememberVolume)
        .put("autoFullscreen", settings.autoFullscreen)
        .put("backend", settings.backend.name.lowercase())
        .put("networkTimeout", settings.networkTimeout)
        .put("retryCount", settings.retryCount)
        .put("defaultPlaylistId", settings.defaultPlaylistId)
        .put("defaultChannelId", settings.defaultChannelId)

    private fun decodeSettings(value: JSONObject, fallback: PlaybackPreferences) = fallback.copy(
        autoPlay = value.optBoolean("autoPlay", fallback.autoPlay),
        rememberVolume = value.optBoolean("rememberVolume", fallback.rememberVolume),
        autoFullscreen = value.optBoolean("autoFullscreen", fallback.autoFullscreen),
        backend = enumValue(value.optString("backend", fallback.backend.name.lowercase()), fallback.backend),
        networkTimeout = value.optInt("networkTimeout", fallback.networkTimeout).coerceIn(3_000, 60_000),
        retryCount = value.optInt("retryCount", fallback.retryCount).coerceIn(0, 5),
        defaultPlaylistId = value.optString("defaultPlaylistId", fallback.defaultPlaylistId),
        defaultChannelId = value.optString("defaultChannelId", fallback.defaultChannelId)
    )

    private fun encodePlaylist(source: PlaylistSource) = JSONObject()
        .put("id", source.id)
        .put("name", source.name)
        .put("kind", source.kind.name.lowercase())
        .put("url", source.url)
        .put("enabled", source.enabled)
        .put("channels", JSONArray().apply { source.channels.forEach { put(encodeChannel(it)) } })
        .putNullable("text", source.text)
        .putNullable("lastLoadedAt", source.lastLoadedAt)
        .putNullable("error", source.error)
        .put("health", source.health.name.lowercase())
        .put("skippedCount", source.skippedCount)
        .putNullable("lastError", source.lastError?.let(::encodeError))

    private fun decodePlaylist(value: JSONObject?): PlaylistSource? {
        if (value == null) return null
        val id = value.optString("id", "")
        if (id.isBlank()) return null
        val channels = value.optJSONArray("channels")?.let { array -> (0 until array.length()).mapNotNull { decodeChannel(array.optJSONObject(it)) } }.orEmpty()
        return PlaylistSource(
            id = id,
            name = value.optString("name", id),
            kind = enumValue(value.optString("kind", "remote"), PlaylistKind.REMOTE),
            url = value.optString("url", ""),
            enabled = value.optBoolean("enabled", true),
            channels = channels,
            text = value.optNullableString("text"),
            lastLoadedAt = value.optNullableLong("lastLoadedAt"),
            error = value.optNullableString("error"),
            health = enumValue(value.optString("health", "unknown"), SourceHealth.UNKNOWN),
            skippedCount = value.optInt("skippedCount", 0).coerceAtLeast(0),
            lastError = value.optJSONObject("lastError")?.let(::decodeError)
        )
    }

    private fun encodeChannel(channel: Channel) = JSONObject()
        .put("id", channel.id)
        .put("playlistId", channel.playlistId)
        .put("number", channel.number)
        .put("name", channel.name)
        .put("url", channel.url)
        .put("group", channel.group)
        .put("tvgId", channel.tvgId)
        .put("logo", channel.logo)
        .put("language", channel.language)
        .put("country", channel.country)

    private fun decodeChannel(value: JSONObject?): Channel? {
        if (value == null || value.optString("id", "").isBlank()) return null
        return Channel(
            id = value.optString("id", ""),
            playlistId = value.optString("playlistId", ""),
            number = value.optInt("number", 0),
            name = value.optString("name", "未命名频道"),
            url = value.optString("url", ""),
            group = value.optString("group", "其他"),
            tvgId = value.optString("tvgId", ""),
            logo = value.optString("logo", ""),
            language = value.optString("language", ""),
            country = value.optString("country", "")
        )
    }

    private fun encodeError(error: SourceError) = JSONObject()
        .put("code", error.code.name.lowercase())
        .put("message", error.message)
        .put("at", error.at)

    private fun decodeError(value: JSONObject) = SourceError(
        code = enumValue(value.optString("code", "unknown"), SourceErrorCode.UNKNOWN),
        message = value.optString("message", "来源加载失败"),
        at = value.optLong("at", 0L)
    )

    private fun encodeEpg(epg: EpgSource) = JSONObject()
        .put("url", epg.url)
        .putNullable("lastLoadedAt", epg.lastLoadedAt)

    private fun decodeEpg(value: JSONObject) = EpgSource(value.optString("url", ""), value.optNullableLong("lastLoadedAt"))

    private inline fun <reified T : Enum<T>> enumValue(value: String, fallback: T): T = enumValues<T>().firstOrNull { it.name.equals(value, ignoreCase = true) } ?: fallback

    private fun JSONObject.putNullable(key: String, value: Any?): JSONObject = put(key, value ?: JSONObject.NULL)
    private fun JSONObject.optNullableString(key: String): String? = if (isNull(key)) null else optString(key).takeUnless { it == "null" }
    private fun JSONObject.optNullableLong(key: String): Long? = if (isNull(key)) null else optLong(key)
    private fun JSONArray.strings(): List<String> = (0 until length()).mapNotNull { if (isNull(it)) null else optString(it) }
}
