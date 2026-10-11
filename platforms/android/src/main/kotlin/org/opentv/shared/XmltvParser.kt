package org.opentv.shared

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class XmltvParseResult(val programs: List<Program>, val skipped: Int)

/** XMLTV counterpart of the desktop parser. It never resolves external entities. */
fun parseXmltv(text: String, fallbackTimeZone: TimeZone = TimeZone.getDefault()): XmltvParseResult {
    require(text.length <= 30 * 1024 * 1024) { "节目单超过 30 MB 上限" }
    require(!text.contains("<!DOCTYPE", ignoreCase = true) && !text.contains("<!ENTITY", ignoreCase = true)) {
        "节目单包含不安全的实体声明"
    }
    val parser = Xml.newPullParser().apply {
        runCatching { setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false) }
        setInput(StringReader(text))
    }
    val result = mutableListOf<Program>()
    var skipped = 0
    var currentChannel = ""
    var currentStart = 0L
    var currentEnd = 0L
    var currentTitle = ""
    var currentDescription = ""
    var currentField: String? = null
    val buffer = StringBuilder()
    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
        when (event) {
            XmlPullParser.START_TAG -> when (parser.name.lowercase(Locale.ROOT)) {
                "programme" -> {
                    currentChannel = parser.getAttributeValue(null, "channel").orEmpty()
                    currentStart = parseXmltvTime(parser.getAttributeValue(null, "start"), fallbackTimeZone)
                    currentEnd = parseXmltvTime(parser.getAttributeValue(null, "stop"), fallbackTimeZone)
                    currentTitle = ""
                    currentDescription = ""
                }
                "title", "desc" -> if (currentChannel.isNotBlank()) {
                    currentField = parser.name.lowercase(Locale.ROOT)
                    buffer.clear()
                }
            }
            XmlPullParser.TEXT -> if (currentField != null) buffer.append(parser.text)
            XmlPullParser.END_TAG -> when (parser.name.lowercase(Locale.ROOT)) {
                "title" -> if (currentField == "title") {
                    currentTitle = buffer.toString().trim()
                    currentField = null
                }
                "desc" -> if (currentField == "desc") {
                    currentDescription = buffer.toString().trim()
                    currentField = null
                }
                "programme" -> {
                    if (currentChannel.isNotBlank() && currentStart > 0 && currentEnd > currentStart && currentTitle.isNotBlank()) {
                        result += Program(currentChannel, currentStart, currentEnd, currentTitle, currentDescription)
                    } else skipped++
                    currentChannel = ""
                    currentField = null
                }
            }
        }
        event = parser.next()
    }
    return XmltvParseResult(result, skipped)
}

fun parseXmltvTime(value: String?, fallbackTimeZone: TimeZone = TimeZone.getDefault()): Long {
    if (value.isNullOrBlank()) return 0L
    val match = Regex("""^(\d{14})(?:\s*([+-]\d{4}))?""").find(value.trim()) ?: return 0L
    val zone = match.groupValues[2].takeUnless { it.isBlank() }?.let { offset ->
        TimeZone.getTimeZone("GMT${offset.substring(0, 3)}:${offset.substring(3)}")
    } ?: fallbackTimeZone
    return runCatching {
        SimpleDateFormat("yyyyMMddHHmmss", Locale.ROOT).apply { timeZone = zone }
            .parse(match.groupValues[1])?.time ?: 0L
    }.getOrDefault(0L)
}
