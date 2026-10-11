package org.opentv.android

import org.opentv.shared.Program
import org.opentv.shared.parseXmltv
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

/** Fetches an explicitly configured XMLTV source without proxying or caching it. */
class AndroidEpgLoader {
    fun load(url: String, timeoutMs: Int = 12_000): List<Program> {
        val (xml, _) = fetchText(url, timeoutMs)
        return parseXmltv(xml).programs
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
                setRequestProperty("Accept", "application/xml, text/xml, text/plain")
            }
            try {
                when (val status = connection.responseCode) {
                    in 300..399 -> {
                        require(hop < MAX_REDIRECTS) { "节目单重定向次数过多" }
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
                                require(result.length <= MAX_SOURCE_CHARS) { "节目单超过 30 MB 上限" }
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
        error("节目单重定向失败")
    }

    private fun requireHttps(value: String?, base: String? = null): String {
        val candidate = if (value?.startsWith("https://", ignoreCase = true) == true) value else {
            require(value != null && base != null) { "节目单必须使用 HTTPS" }
            URI(base).resolve(value).toString()
        }
        val uri = URI(candidate)
        require(uri.scheme.equals("https", ignoreCase = true) && uri.host != null && uri.userInfo == null) {
            "节目单必须使用无凭据 HTTPS"
        }
        return uri.toString()
    }

    private companion object {
        const val MAX_REDIRECTS = 5
        const val MAX_SOURCE_CHARS = 30 * 1024 * 1024
    }
}
