package com.olivercraftboy.nortestnative

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/* Saves imported songs onto the phone so they play with no internet. */
object Downloader {
    const val BASE = "https://carter-player-audio.sites.tab.bot"

    fun audioUri(vid: String): String = "$BASE/audio?v=$vid"

    fun downloadUri(vid: String, title: String, artist: String): String =
        "$BASE/download?v=" + Net.enc(vid) + "&title=" + Net.enc(title) + "&artist=" + Net.enc(artist) + "&album="

    fun fileFor(c: Context, vid: String): File {
        val dir = c.getExternalFilesDir("music") ?: c.filesDir
        dir.mkdirs()
        return File(dir, "$vid.m4a")
    }

    /* Downloads one song. Returns the saved size in bytes, or -1 when it failed or
       came back looking wrong (too small, or not an audio file at all). */
    fun download(c: Context, vid: String, title: String, artist: String): Long {
        val out = fileFor(c, vid)
        if (out.exists() && out.length() > 50000) return out.length()
        val tmp = File(out.parentFile, "$vid.part")
        try {
            val conn = URL(downloadUri(vid, title, artist)).openConnection() as HttpURLConnection
            conn.connectTimeout = 20000; conn.readTimeout = 60000
            conn.connect()
            if (conn.responseCode !in 200..299) { conn.disconnect(); return -1 }
            val ins = conn.inputStream
            val fos = FileOutputStream(tmp)
            val buf = ByteArray(65536)
            var got = 0L
            while (true) {
                val n = ins.read(buf)
                if (n < 0) break
                fos.write(buf, 0, n); got += n
            }
            fos.close(); ins.close(); conn.disconnect()
            if (got < 50000) { tmp.delete(); return -1 }
            val head = ByteArray(12)
            val fi = FileInputStream(tmp); fi.read(head); fi.close()
            val magic = String(head, Charsets.ISO_8859_1)
            if (magic.length >= 8 && magic.substring(4, 8) != "ftyp") { tmp.delete(); return -1 }
            if (!tmp.renameTo(out)) { tmp.delete(); return -1 }
            Store.setDownloaded(c, vid, title, artist, out.absolutePath, got)
            return got
        } catch (e: Exception) {
            tmp.delete()
            return -1
        }
    }
}
