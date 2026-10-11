package org.opentv.shared

import java.net.URI

data class M3uParseResult(val channels: List<Channel>, val skipped: Int)

/**
 * Android counterpart of the desktop M3U parser. It intentionally accepts only
 * public HTTPS stream URLs and never executes playlist text as markup or code.
 */
fun parseM3u(text: String, playlistId: String, baseUrl: String? = null): M3uParseResult {
    require(text.length <= 15 * 1024 * 1024) { "播放列表超过 15 MB 上限" }
    require(text.trimStart().startsWith("#EXTM3U")) { "文件不是有效的 M3U/M3U8" }
    if (text.contains("#EXT-X-TARGETDURATION") || text.contains("#EXT-X-STREAM-INF") || text.contains("#EXT-X-MEDIA-SEQUENCE")) {
        val url = requireHttps(baseUrl ?: error("这是 HLS 媒体清单，请使用远程 URL 添加"))
        return M3uParseResult(
            channels = listOf(Channel("$playlistId-${stableId(url)}", playlistId, 1, "HLS 直播", url, "直播")),
            skipped = 0
        )
    }

    val channels = mutableListOf<Channel>()
    val seen = mutableSetOf<String>()
    var info: Info? = null
    var skipped = 0

    text.lineSequence().forEach { raw ->
        val line = raw.trim()
        when {
            line.startsWith("#EXTINF:") -> {
                if (info != null) skipped++
                info = parseInfo(line)
            }
            line.isNotEmpty() && !line.startsWith("#") && info != null -> {
                val pending = info!!
                info = null
                runCatching {
                    val url = requireHttps(line, baseUrl)
                    if (seen.add(url)) {
                        val suppliedNumber = pending.attributes["tvg-chno"]?.toIntOrNull()
                        channels += Channel(
                            id = "$playlistId-${stableId(url)}",
                            playlistId = playlistId,
                            number = if (suppliedNumber != null && suppliedNumber > 0) suppliedNumber else channels.size + 1,
                            name = pending.name.ifBlank { "未命名频道" },
                            url = url,
                            group = pending.attributes["group-title"]?.takeUnless { it == "Undefined" } ?: "其他",
                            tvgId = pending.attributes["tvg-id"].orEmpty(),
                            logo = pending.attributes["tvg-logo"].orEmpty(),
                            language = pending.attributes["tvg-language"].orEmpty(),
                            country = pending.attributes["tvg-country"].orEmpty()
                        )
                    }
                }.onFailure { skipped++ }
            }
        }
    }
    if (info != null) skipped++
    return M3uParseResult(channels, skipped)
}

private data class Info(val name: String, val attributes: Map<String, String>)

private fun parseInfo(line: String): Info {
    val comma = findUnquotedComma(line)
    val metadata = line.substring(0, comma.takeIf { it >= 0 } ?: line.length)
    val attrs = Regex("""([\w-]+)=(?:"([^"]*)"|'([^']*)')""")
        .findAll(metadata)
        .associate { match -> match.groupValues[1].lowercase() to (match.groups[2]?.value ?: match.groups[3]?.value.orEmpty()) }
    val name = if (comma >= 0) line.substring(comma + 1).trim() else attrs["tvg-name"].orEmpty()
    return Info(name, attrs)
}

private fun findUnquotedComma(line: String): Int {
    var quote: Char? = null
    line.forEachIndexed { index, char ->
        if (char == '\'' || char == '"') {
            quote = if (quote == char) null else quote ?: char
        } else if (char == ',' && quote == null) return index
    }
    return -1
}

private fun requireHttps(value: String, base: String? = null): String {
    val candidate = if (value.startsWith("https://", ignoreCase = true)) value else {
        require(base != null) { "频道地址必须使用 HTTPS" }
        URI(base).resolve(value).toString()
    }
    val uri = URI(candidate)
    require(uri.scheme.equals("https", ignoreCase = true) && uri.host != null && uri.userInfo == null) { "频道地址必须使用无凭据 HTTPS" }
    return uri.toString()
}

private fun stableId(value: String): String {
    var a = 0x811c9dc5.toInt()
    var b = 5381
    value.forEach { char ->
        a = (a xor char.code) * 16777619
        b = (b * 33) xor char.code
    }
    return "${a.toUInt().toString(36)}${b.toUInt().toString(36)}"
}
