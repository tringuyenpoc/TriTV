package vn.tri.tv

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class Channel(
    val name: String,
    val url: String,
    val logo: String = "",
    val group: String = "Khác",
    val userAgent: String = "",
    val referer: String = "",
) {
    fun toJson(): JSONObject = JSONObject()
        .put("name", name).put("url", url).put("logo", logo).put("group", group)
        .put("ua", userAgent).put("ref", referer)

    companion object {
        fun fromJson(o: JSONObject) = Channel(
            o.optString("name"), o.optString("url"), o.optString("logo"),
            o.optString("group", "Khác"), o.optString("ua"), o.optString("ref"),
        )
    }
}

data class Row(val title: String, val items: List<Channel>)

object M3uParser {
    private val attrRegex = Regex("""([\w-]+)="([^"]*)"""")

    fun parse(text: String): List<Channel> {
        val out = ArrayList<Channel>()
        var name: String? = null
        var logo = ""
        var group = ""
        var ua = ""
        var ref = ""
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    val attrs = attrRegex.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    logo = attrs["tvg-logo"] ?: ""
                    group = attrs["group-title"] ?: group.ifEmpty { "" }
                    name = nameAfterComma(line).ifEmpty { attrs["tvg-name"] ?: "" }
                    attrs["user-agent"]?.let { ua = it }
                }
                line.startsWith("#EXTGRP:", ignoreCase = true) -> group = line.substringAfter(":").trim()
                line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                    val opt = line.substringAfter(":")
                    when {
                        opt.startsWith("http-user-agent=") -> ua = opt.substringAfter("=")
                        opt.startsWith("http-referrer=") -> ref = opt.substringAfter("=")
                    }
                }
                line.startsWith("#") -> Unit
                else -> {
                    out.add(Channel(
                        name = name?.takeIf { it.isNotBlank() } ?: line.substringAfterLast('/'),
                        url = line, logo = logo,
                        group = group.ifBlank { "Khác" }, userAgent = ua, referer = ref,
                    ))
                    name = null; logo = ""; group = ""; ua = ""; ref = ""
                }
            }
        }
        return out
    }

    /** Tên kênh nằm sau dấu phẩy đầu tiên không nằm trong ngoặc kép. */
    private fun nameAfterComma(line: String): String {
        var inQuote = false
        for (i in line.indices) {
            val c = line[i]
            if (c == '"') inQuote = !inQuote
            else if (c == ',' && !inQuote) return line.substring(i + 1).trim()
        }
        return ""
    }
}

object Store {
    private const val PREFS = "tritv"
    var channels: List<Channel> = emptyList()

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun playlistUrl(c: Context): String = prefs(c).getString("playlist", "") ?: ""
    fun setPlaylistUrl(c: Context, url: String) = prefs(c).edit().putString("playlist", url).apply()

    private fun cacheFile(c: Context) = File(c.filesDir, "playlist.m3u")

    fun loadCached(c: Context): List<Channel> {
        val f = cacheFile(c)
        return if (f.exists()) M3uParser.parse(f.readText()) else emptyList()
    }

    /** Chạy trên luồng nền. */
    fun download(c: Context, url: String): List<Channel> {
        val text = if (url.startsWith("http", ignoreCase = true)) {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "TriTV/0.1")
            try {
                if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
                conn.inputStream.bufferedReader().use { it.readText() }
            } finally { conn.disconnect() }
        } else File(url).readText()
        val list = M3uParser.parse(text)
        if (list.isEmpty()) error("Playlist không có kênh nào")
        cacheFile(c).writeText(text)
        return list
    }

    // ---- Xem gần đây ----
    fun recents(c: Context): List<Channel> {
        val arr = JSONArray(prefs(c).getString("recent", "[]"))
        return (0 until arr.length()).map { Channel.fromJson(arr.getJSONObject(it)) }
    }

    fun addRecent(c: Context, ch: Channel) {
        val list = listOf(ch) + recents(c).filter { it.url != ch.url }
        val arr = JSONArray()
        list.take(20).forEach { arr.put(it.toJson()) }
        prefs(c).edit().putString("recent", arr.toString()).apply()
    }

    // ---- Vị trí xem dở (chỉ cho phim/VOD) ----
    fun position(c: Context, url: String): Long = prefs(c).getLong("pos_" + url.hashCode(), 0L)
    fun setPosition(c: Context, url: String, ms: Long) {
        val e = prefs(c).edit()
        if (ms > 0) e.putLong("pos_" + url.hashCode(), ms) else e.remove("pos_" + url.hashCode())
        e.apply()
    }
}
