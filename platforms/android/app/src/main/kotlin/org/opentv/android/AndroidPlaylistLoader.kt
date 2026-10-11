package org.opentv.android

import org.opentv.shared.PlaylistKind
import org.opentv.shared.PlaylistSource
import org.opentv.shared.SourceError
import org.opentv.shared.SourceErrorCode
import org.opentv.shared.SourceHealth
import org.opentv.shared.parseM3u
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

/**
 * Background-safe playlist loader. Call it away from the UI thread; it never
 * proxies or caches video, and it keeps redirects inside HTTPS.
 */
class AndroidPlaylistLoader {
    fun load(source: PlaylistSource, timeoutMs: Int = 12_000): PlaylistSource {
        val now = System.currentTimeMillis()
        return runCatching {
            val (text, finalUrl) = when (source.kind) {
                PlaylistKind.LOCAL -> source.text.orEmpty() to source.url
                PlaylistKind.REMOTE -> fetchText(source.url, timeoutMs)
            }
            val parsed = parseM3u(text, source.id, finalUrl.takeIf { it.isNotBlank() })
            require(parsed.channels.isNotEmpty()) { "来源没有可用频道" }
            source.copy(
                url = finalUrl,
                text = if (source.kind == PlaylistKind.LOCAL) text else source.text,
                channels = parsed.channels,
                lastLoadedAt = now,
                error = null,
                health = if (parsed.skipped > 0) SourceHealth.DEGRADED else SourceHealth.HEALTHY,
                skippedCount = parsed.skipped,
                lastError = null
            )
        }.getOrElse { error ->
            val (code, message) = classify(error)
            source.copy(
                lastLoadedAt = now,
                error = message,
                health = SourceHealth.ERROR,
                lastError = SourceError(code, message, now)
            )
        }
    }

    private fun fetchText(initialUrl: String, timeoutMs: Int): Pair<String, String> {
        var url = requireHttps(initialUrl)
        repeat(MAX_REDIRECTS + 1) { hop ->
            val connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = timeoutMs.coerceIn(3_000, 60_000)
                readTimeout = timeoutMs.coerceIn(3_000, 60_000)
                requestMethod = "GET"
                useCaches = false
                setRequestProperty("Accept", "application/x-mpegURL, audio/x-mpegurl, text/plain")
            }
            try {
                when (val status = connection.responseCode) {
                    in 300..399 -> {
                        require(hop < MAX_REDIRECTS) { "来源重定向次数过多" }
                        url = requireHttps(connection.getHeaderField("Location"), url)
                    }
                    in 200..299 -> {
                        val text = connection.inputStream.bufferedReader().use { reader ->
                            val result = StringBuilder()
                            val buffer = CharArray(8192)
                            while (true) {
                                val count = reader.read(buffer)
                                if (count < 0) break
                                result.append(buffer, 0, count)
                                require(result.length <= MAX_SOURCE_CHARS) { "来源超过 30 MB 上限" }
                            }
                            result.toString()
                        }
                        return text to url
                    }
                    else -> throw IOException("HTTP $status")
                }
            } finally {
                connection.disconnect()
            }
        }
        error("来源重定向失败")
    }

    private fun requireHttps(value: String?, base: String? = null): String {
        val candidate = if (value?.startsWith("https://", ignoreCase = true) == true) value else {
            require(value != null && base != null) { "来源必须使用 HTTPS" }
            URI(base).resolve(value).toString()
        }
        val uri = URI(candidate)
        require(uri.scheme.equals("https", ignoreCase = true) && uri.host != null && uri.userInfo == null) { "来源必须使用无凭据 HTTPS" }
        return uri.toString()
    }

    private fun classify(error: Throwable): Pair<SourceErrorCode, String> = when (error) {
        is java.net.SocketTimeoutException -> SourceErrorCode.TIMEOUT to "来源请求超时"
        is SecurityException -> SourceErrorCode.INSECURE to "来源地址不安全"
        else -> when {
            error.message?.contains("HTTPS", ignoreCase = true) == true -> SourceErrorCode.INSECURE to "来源地址不安全"
            error.message?.contains("HTTP") == true -> SourceErrorCode.HTTP to "来源服务器不可访问"
            error.message?.contains("频道") == true -> SourceErrorCode.EMPTY to "来源没有可用频道"
            error.message?.contains("M3U") == true -> SourceErrorCode.FORMAT to "来源格式无法识别"
            else -> SourceErrorCode.NETWORK to "来源网络请求失败"
        }
    }

    private companion object {
        const val MAX_REDIRECTS = 5
        const val MAX_SOURCE_CHARS = 30 * 1024 * 1024
    }
}
