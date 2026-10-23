package com.olivercraftboy.nortestnative

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object Net {
    val SPOTIFY_API = "https://nortest-spotify.sites.tab.bot/api?u="
    val YT_API = "https://nortest-spotify.sites.tab.bot/yt?"

    fun get(url: String, timeoutMs: Int = 20000): String? {
        return try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = timeoutMs; c.readTimeout = timeoutMs
            c.setRequestProperty("User-Agent", "NortestNative/0.2")
            c.connect()
            if (c.responseCode !in 200..299) { c.disconnect(); return null }
            val ins = c.inputStream
            val bo = ByteArrayOutputStream()
            val buf = ByteArray(16384)
            while (true) { val n = ins.read(buf); if (n < 0) break; bo.write(buf, 0, n) }
            ins.close(); c.disconnect()
            bo.toString("UTF-8")
        } catch (e: Exception) { null }
    }

    fun getJson(url: String): JSONObject? {
        val s = get(url) ?: return null
        return try { JSONObject(s) } catch (e: Exception) { null }
    }

    fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}
