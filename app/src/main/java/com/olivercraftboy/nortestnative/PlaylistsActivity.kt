package com.olivercraftboy.nortestnative

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.SimpleAdapter
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.media3.session.MediaController

/* Playlists. Without an extra: the list of playlists (tap one to open it, long-press to
   delete). With "pl": the songs of one playlist (tap to play from there, long-press to
   remove it from the playlist), plus download-everything-in-this-playlist. */
class PlaylistsActivity : android.app.Activity() {

    private var controller: MediaController? = null
    private var plName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        plName = intent.getStringExtra("pl")
        if (plName == null) setContentView(R.layout.activity_playlists) else setContentView(R.layout.activity_detail)
        findViewById<View>(R.id.backBtn).setOnClickListener { finish() }
        Player.connect(this) { c -> controller = c }
        if (plName == null) showPlaylists() else showSongs(plName!!)
    }

    private fun setHeading(t: String) { findViewById<TextView>(R.id.dHeading).text = t }

    private fun showPlaylists() {
        setHeading("Playlists")
        val names = Store.playlists(this)
        val rows = ArrayList<Map<String, String>>()
        for (n in names) {
            val m = HashMap<String, String>()
            m["t"] = n
            m["a"] = Store.playlistSongs(this, n).size.toString() + " songs"
            rows.add(m)
        }
        val list = findViewById<ListView>(R.id.dList)
        list.adapter = SimpleAdapter(this, rows, android.R.layout.simple_list_item_2,
            arrayOf("t", "a"), intArrayOf(android.R.id.text1, android.R.id.text2))
        list.setOnItemClickListener { _, _, pos, _ ->
            startActivity(Intent(this, PlaylistsActivity::class.java).putExtra("pl", names[pos]))
        }
        list.setOnItemLongClickListener { _, _, pos, _ ->
            AlertDialog.Builder(this)
                .setTitle(names[pos])
                .setItems(arrayOf("Delete playlist")) { _, w ->
                    Store.deletePlaylist(this, names[pos])
                    showPlaylists()
                    Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show()
                }
                .show()
            true
        }
        findViewById<View>(R.id.dAction).setOnClickListener {
            val box = EditText(this)
            AlertDialog.Builder(this)
                .setTitle("New playlist")
                .setView(box)
                .setPositiveButton("Create") { _, _ ->
                    val name = box.text.toString().trim()
                    if (name.isNotEmpty() && !Store.playlists(this).contains(name)) {
                        Store.savePlaylist(this, name, ArrayList())
                        showPlaylists()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        findViewById<Button>(R.id.dAction).text = "New playlist"
    }

    private fun showSongs(name: String) {
        setHeading(name)
        val songs = Store.playlistSongs(this, name)
        val rows = ArrayList<Map<String, String>>()
        for (t in songs) {
            val m = HashMap<String, String>()
            m["t"] = t.title
            val off = t.vid != null && Store.downloaded(this, t.vid) != null
            m["a"] = t.artist.ifBlank { "Unknown artist" } + if (off) "  ·  offline" else ""
            rows.add(m)
        }
        val list = findViewById<ListView>(R.id.dList)
        list.adapter = SimpleAdapter(this, rows, android.R.layout.simple_list_item_2,
            arrayOf("t", "a"), intArrayOf(android.R.id.text1, android.R.id.text2))
        list.setOnItemClickListener { _, _, pos, _ ->
            val c = controller ?: return@setOnItemClickListener
            Player.play(this, c, songs, pos)
            startActivity(Intent(this, NowPlayingActivity::class.java))
        }
        list.setOnItemLongClickListener { _, _, pos, _ ->
            val t = songs[pos]
            AlertDialog.Builder(this)
                .setTitle(t.title)
                .setItems(arrayOf("Remove from playlist", "Download for offline")) { _, w ->
                    if (w == 0) {
                        val cur = Store.playlistSongs(this, name).toMutableList()
                        cur.removeAll { it.ref == t.ref }
                        Store.savePlaylist(this, name, cur)
                        showSongs(name)
                    } else if (w == 1 && t.vid != null) {
                        downloadAll(songs)
                    }
                }
                .show()
            true
        }
        findViewById<View>(R.id.dAction).setOnClickListener { downloadAll(songs) }
        findViewById<Button>(R.id.dAction).text = "Download all"
    }

    private fun downloadAll(songs: List<Track>) {
        val need = songs.filter { it.vid != null && Store.downloaded(this, it.vid!!) == null }
        if (need.isEmpty()) { Toast.makeText(this, "Everything here is already offline", Toast.LENGTH_SHORT).show(); return }
        Toast.makeText(this, "Downloading " + need.size + " songs…", Toast.LENGTH_SHORT).show()
        Thread {
            var ok = 0
            for (t in need) {
                if (Downloader.download(this, t.vid!!, t.title, t.artist) > 0) ok++
            }
            val done = ok
            runOnUiThread {
                Toast.makeText(this, done.toString() + " of " + need.size + " saved for offline", Toast.LENGTH_SHORT).show()
                if (done > 0) showSongs(plName!!)
            }
        }.start()
    }
}
