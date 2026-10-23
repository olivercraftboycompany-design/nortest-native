package com.olivercraftboy.nortestnative

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/* All the app's own data, kept in app-private storage: playlists, which songs are
   downloaded, the YouTube version cache, and the play history that powers Wrapped. */
object Store {

    private fun p(c: Context, name: String) = c.getSharedPreferences(name, Context.MODE_PRIVATE)

    /* ---- youtube version cache: key -> [vid,...] ---- */
    fun ytCache(c: Context, key: String): List<String> {
        val raw = p(c, "yt.v1").getString(key, null) ?: return emptyList()
        return try {
            val a = JSONArray(raw); (0 until a.length()).map { a.getString(it) }
        } catch (e: Exception) { emptyList() }
    }

    fun ytSave(c: Context, key: String, vids: List<String>) {
        val a = JSONArray(); vids.forEach { a.put(it) }
        p(c, "yt.v1").edit().putString(key, a.toString()).apply()
    }

    /* ---- playlists: name -> [ {ref,title,artist,dur,vid,lid} ] ---- */
    fun playlists(c: Context): List<String> {
        val o = try { JSONObject(p(c, "plays.v1").getString("pls", "{}")) } catch (e: Exception) { JSONObject() }
        val names = ArrayList<String>()
        val it = o.keys(); while (it.hasNext()) names.add(it.next())
        return names.sorted()
    }

    fun playlistSongs(c: Context, name: String): List<Track> {
        val o = try { JSONObject(p(c, "plays.v1").getString("pls", "{}")) } catch (e: Exception) { JSONObject() }
        val out = ArrayList<Track>()
        val a = try { o.optJSONArray(name) ?: JSONArray() } catch (e: Exception) { JSONArray() }
        for (i in 0 until a.length()) {
            val t = Track.fromJson(a.optJSONObject(i) ?: continue)
            if (t != null) out.add(t)
        }
        return out
    }

    fun savePlaylist(c: Context, name: String, tracks: List<Track>) {
        val sp = p(c, "plays.v1")
        val o = try { JSONObject(sp.getString("pls", "{}")) } catch (e: Exception) { JSONObject() }
        val a = JSONArray(); tracks.forEach { a.put(it.toJson()) }
        o.put(name, a)
        sp.edit().putString("pls", o.toString()).apply()
    }

    fun deletePlaylist(c: Context, name: String) {
        val sp = p(c, "plays.v1")
        val o = try { JSONObject(sp.getString("pls", "{}")) } catch (e: Exception) { JSONObject() }
        o.remove(name)
        sp.edit().putString("pls", o.toString()).apply()
    }

    /* ---- downloads: vid -> {title,artist,path,size} ---- */
    fun downloads(c: Context): JSONObject {
        return try { JSONObject(p(c, "dl.v1").getString("dl", "{}")) } catch (e: Exception) { JSONObject() }
    }

    fun downloaded(c: Context, vid: String): JSONObject? = downloads(c).optJSONObject(vid)

    fun setDownloaded(c: Context, vid: String, title: String, artist: String, path: String, size: Long) {
        val sp = p(c, "dl.v1")
        val o = downloads(c)
        val rec = JSONObject()
        rec.put("title", title); rec.put("artist", artist)
        rec.put("path", path); rec.put("size", size)
        o.put(vid, rec)
        sp.edit().putString("dl", o.toString()).apply()
    }

    fun removeDownload(c: Context, vid: String) {
        val sp = p(c, "dl.v1")
        val o = downloads(c); o.remove(vid)
        sp.edit().putString("dl", o.toString()).apply()
    }

    /* ---- listen history for Wrapped: [{t,a,dur,ts}] (newest last) ---- */
    fun logListen(c: Context, title: String, artist: String, dur: Long) {
        val sp = p(c, "listens.v1")
        val a = try { JSONArray(sp.getString("l", "[]")) } catch (e: Exception) { JSONArray() }
        val e = JSONObject()
        e.put("t", title); e.put("a", artist); e.put("d", dur); e.put("ts", System.currentTimeMillis())
        a.put(e)
        while (a.length() > 4000) a.remove(0)
        sp.edit().putString("l", a.toString()).apply()
    }

    fun listens(c: Context): JSONArray {
        return try { JSONArray(p(c, "listens.v1").getString("l", "[]")) } catch (e: Exception) { JSONArray() }
    }
}
