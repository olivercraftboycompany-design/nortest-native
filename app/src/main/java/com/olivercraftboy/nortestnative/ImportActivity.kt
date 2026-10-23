package com.olivercraftboy.nortestnative

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ListView
import android.widget.SimpleAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.media3.session.MediaController
import org.json.JSONObject

/* Import: paste a Spotify playlist / album / song link, a YouTube link, or plain lines
   like "Song - Artist" or "Song by Artist". Nortest finds each song on YouTube, then you
   can play it all, save it as a playlist, or download it all for offline. */
class ImportActivity : android.app.Activity() {

    private var controller: MediaController? = null
    private var tracks: MutableList<Track> = ArrayList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_import)
        findViewById<View>(R.id.backBtn).setOnClickListener { finish() }
        findViewById<TextView>(R.id.dHeading).text = "Import"
        Player.connect(this) { c -> controller = c }
        findViewById<View>(R.id.dAction).setOnClickListener { load() }
        findViewById<View>(R.id.btnPlayAll).setOnClickListener { playAll() }
        findViewById<View>(R.id.btnSavePl).setOnClickListener { savePl() }
        findViewById<View>(R.id.btnDlAll).setOnClickListener { dlAll() }
    }

    private fun load() {
        val txt = findViewById<EditText>(R.id.pasteBox).text.toString().trim()
        if (txt.isEmpty()) { Toast.makeText(this, "Paste something first", Toast.LENGTH_SHORT).show(); return }
        val status = findViewById<TextView>(R.id.iStatus)
        status.text = "Looking it up…"
        val isLink = txt.startsWith("http://") || txt.startsWith("https://")
        Thread {
            val result = ArrayList<Track>()
            if (isLink) {
                val j = Net.getJson(Net.SPOTIFY_API + Net.enc(txt))
                val ok = j != null && j.optBoolean("ok")
                if (ok && j != null) {
                    val arr = j.optJSONArray("tracks")
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val s = arr.optJSONObject(i) ?: continue
                            val title = s.optString("t", "")
                            val artist = s.optString("a", "")
                            if (title.isEmpty()) continue
                            var vid = s.optString("yt", "")
                            if (vid.isEmpty()) vid = findVid(title, artist, s.optLong("dur", 0))
                            if (vid.isNotEmpty()) result.add(Track.yt(vid, title, artist, s.optLong("dur", 0)))
                        }
                    }
                }
            } else {
                val lines = txt.split("\n").map { it.trim() }.filter { it.isNotEmpty() }.take(1000)
                for (raw in lines) {
                    var line = raw.replace(Regex("^\\s*(\\d{1,3}[.)\\-:]?\\s+|[-*•]\\s+)"), "")
                    var title = line; var artist = ""; var alt = false
                    var m = line.split(Regex("\\s+-\\s+"))
                    if (m.size >= 2) { title = m[0].trim(); artist = m.drop(1).joinToString(" - ").trim(); alt = true }
                    else {
                        m = line.split(Regex("\\s+by\\s+", RegexOption.IGNORE_CASE))
                        if (m.size == 2) { title = m[0].trim(); artist = m[1].trim() }
                    }
                    var vid = findVid(title, artist, 0)
                    if (vid.isEmpty() && alt) vid = findVid(artist, title, 0)
                    if (vid.isNotEmpty()) {
                        val t = if (vid.isNotEmpty() && alt && title.isEmpty()) title else title
                        result.add(Track.yt(vid, t, artist, 0))
                    }
                }
            }
            runOnUiThread {
                tracks = result
                if (tracks.isEmpty()) status.text = "Couldn't find any songs. Check the link or try Song - Artist lines."
                else status.text = tracks.size.toString() + " songs ready"
                paint()
            }
        }.start()
    }

    /* Finds the right YouTube video for a title + artist, remembering your picks. */
    private fun findVid(title: String, artist: String, dur: Long): String {
        val key = title.lowercase() + "|" + artist.lowercase()
        val cached = Store.ytCache(this, key)
        if (cached.isNotEmpty()) return cached[0]
        val url = Net.YT_API + "t=" + Net.enc(title) + "&a=" + Net.enc(artist) + "&d=" + dur
        val j = Net.getJson(url) ?: return ""
        val vids = j.optJSONArray("videos") ?: return ""
        val all = ArrayList<String>()
        for (i in 0 until vids.length()) {
            val v = vids.optJSONObject(i)?.optString("id", "") ?: ""
            if (v.isNotEmpty()) all.add(v)
        }
        if (all.isNotEmpty()) Store.ytSave(this, key, all)
        return all.firstOrNull() ?: ""
    }

    private fun paint() {
        val rows = ArrayList<Map<String, String>>()
        for (t in tracks) {
            val m = HashMap<String, String>()
            m["t"] = t.title
            val off = t.vid != null && Store.downloaded(this, t.vid) != null
            m["a"] = t.artist.ifBlank { "Unknown artist" } + if (off) "  ·  offline" else ""
            rows.add(m)
        }
        findViewById<ListView>(R.id.dList).adapter = SimpleAdapter(
            this, rows, android.R.layout.simple_list_item_2,
            arrayOf("t", "a"), intArrayOf(android.R.id.text1, android.R.id.text2)
        )
    }

    private fun playAll() {
        val c = controller ?: return
        if (tracks.isEmpty()) { Toast.makeText(this, "Load some songs first", Toast.LENGTH_SHORT).show(); return }
        Player.play(this, c, tracks, 0)
        startActivity(Intent(this, NowPlayingActivity::class.java))
    }

    private fun savePl() {
        if (tracks.isEmpty()) { Toast.makeText(this, "Load some songs first", Toast.LENGTH_SHORT).show(); return }
        val box = EditText(this)
        AlertDialog.Builder(this)
            .setTitle("Save as playlist")
            .setView(box)
            .setPositiveButton("Save") { _, _ ->
                val name = box.text.toString().trim()
                if (name.isNotEmpty()) {
                    Store.savePlaylist(this, name, tracks)
                    Toast.makeText(this, "Saved " + tracks.size + " songs to " + name, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun dlAll() {
        val need = tracks.filter { it.vid != null && Store.downloaded(this, it.vid!!) == null }
        if (need.isEmpty()) { Toast.makeText(this, "Nothing new to download", Toast.LENGTH_SHORT).show(); return }
        Toast.makeText(this, "Downloading " + need.size + " songs…", Toast.LENGTH_SHORT).show()
        Thread {
            var ok = 0
            for (t in need) if (Downloader.download(this, t.vid!!, t.title, t.artist) > 0) ok++
            val done = ok
            runOnUiThread {
                Toast.makeText(this, done.toString() + " of " + need.size + " saved for offline", Toast.LENGTH_SHORT).show()
                paint()
            }
        }.start()
    }
}
