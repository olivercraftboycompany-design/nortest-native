package com.olivercraftboy.nortestnative

import android.Manifest
import android.content.ComponentName
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ListView
import android.widget.SimpleAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import java.io.File

/* The library screen: every song on this phone (plus the imported ones that were downloaded
   offline), search, the small bar at the bottom, and long-press a song for playlists and
   downloads. The buttons up top open Playlists, Import and Wrapped. */
class MainActivity : android.app.Activity() {

    private var controller: MediaController? = null
    private var all: MutableList<Track> = ArrayList()
    private var shown: MutableList<Track> = ArrayList()
    private val ui = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() { paintMini(); ui.postDelayed(this, 500) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        needPermission()
    }

    override fun onResume() {
        super.onResume()
        if (controller != null) loadLibrary()
    }

    private fun needPermission() {
        val p = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
        if (checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED) start()
        else requestPermissions(arrayOf(p), 1)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) start()
        else {
            findViewById<TextView>(R.id.heading).text =
                "Allow Nortest Native to see your music, then tap here to try again"
        }
    }

    private fun start() {
        loadLibrary()
        connect()
        findViewById<EditText>(R.id.search).addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(e: Editable) { filterList(e.toString()) }
            override fun beforeTextChanged(s: CharSequence, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence, a: Int, b: Int, c: Int) {}
        })
        findViewById<View>(R.id.btnPls).setOnClickListener { startActivity(Intent(this, PlaylistsActivity::class.java)) }
        findViewById<View>(R.id.btnImport).setOnClickListener { startActivity(Intent(this, ImportActivity::class.java)) }
        findViewById<View>(R.id.btnWrapped).setOnClickListener { startActivity(Intent(this, WrappedActivity::class.java)) }
        findViewById<View>(R.id.mini).setOnClickListener { startActivity(Intent(this, NowPlayingActivity::class.java)) }
    }

    private fun loadLibrary() {
        all = ArrayList()
        contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DURATION
            ),
            MediaStore.Audio.Media.DURATION + " > 30000", null,
            MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC"
        )?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val iT = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val iA = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val iD = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            while (c.moveToNext()) {
                all.add(Track.local(c.getLong(iId), c.getString(iT) ?: "Untitled", c.getString(iA) ?: "", c.getLong(iD)))
            }
        }
        val dls = Store.downloads(this)
        val it = dls.keys()
        while (it.hasNext()) {
            val vid = it.next()
            val d = dls.optJSONObject(vid) ?: continue
            all.add(Track.yt(vid, d.optString("title", "Untitled"), d.optString("artist", ""), 0))
        }
        filterList(findViewById<EditText>(R.id.search).text.toString())
    }

    private fun filterList(q: String) {
        val needle = q.trim().lowercase()
        shown = if (needle.isEmpty()) ArrayList(all) else ArrayList(all.filter {
            it.title.lowercase().contains(needle) || it.artist.lowercase().contains(needle)
        })
        val rows = ArrayList<Map<String, String>>()
        for (t in shown) {
            val m = HashMap<String, String>()
            m["t"] = t.title
            val off = t.vid != null && Store.downloaded(this, t.vid) != null
            m["a"] = t.artist.ifBlank { "Unknown artist" } + if (off) "  ·  offline" else ""
            rows.add(m)
        }
        val list = findViewById<ListView>(R.id.songs)
        list.adapter = SimpleAdapter(
            this, rows, android.R.layout.simple_list_item_2,
            arrayOf("t", "a"), intArrayOf(android.R.id.text1, android.R.id.text2)
        )
        list.setOnItemClickListener { _, _, pos, _ -> playFrom(pos) }
        list.setOnItemLongClickListener { _, _, pos, _ ->
            songMenu(shown[pos]); true
        }
    }

    private fun songMenu(t: Track) {
        val opts = ArrayList<String>()
        opts.add("Add to playlist")
        if (t.vid != null && Store.downloaded(this, t.vid) == null) opts.add("Download for offline")
        if (t.vid != null && Store.downloaded(this, t.vid) != null) opts.add("Remove download")
        val arr = opts.toTypedArray()
        android.app.AlertDialog.Builder(this)
            .setTitle(t.title)
            .setItems(arr) { _, which ->
                if (arr[which] == "Add to playlist") pickPlaylist(t)
                else if (arr[which] == "Download for offline") downloadOne(t)
                else if (arr[which] == "Remove download" && t.vid != null) {
                    Downloader.fileFor(this, t.vid).delete()
                    Store.removeDownload(this, t.vid)
                    loadLibrary()
                    Toast.makeText(this, "Removed", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun pickPlaylist(t: Track) {
        val names = Store.playlists(this).toMutableList()
        names.add("New playlist…")
        android.app.AlertDialog.Builder(this)
            .setTitle("Add to playlist")
            .setItems(names.toTypedArray()) { _, which ->
                if (which == names.size - 1) {
                    val box = EditText(this)
                    android.app.AlertDialog.Builder(this)
                        .setTitle("New playlist")
                        .setView(box)
                        .setPositiveButton("Save") { _, _ ->
                            val name = box.text.toString().trim()
                            if (name.isNotEmpty()) {
                                Store.savePlaylist(this, name, listOf(t))
                                Toast.makeText(this, "Saved to $name", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                } else {
                    val name = names[which]
                    val cur = Store.playlistSongs(this, name).toMutableList()
                    cur.removeAll { it.ref == t.ref }
                    cur.add(t)
                    Store.savePlaylist(this, name, cur)
                    Toast.makeText(this, "Saved to $name", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun downloadOne(t: Track) {
        val vid = t.vid ?: return
        Toast.makeText(this, "Downloading " + t.title + "…", Toast.LENGTH_SHORT).show()
        Thread {
            val ok = Downloader.download(this, vid, t.title, t.artist) > 0
            runOnUiThread {
                if (ok) { loadLibrary(); Toast.makeText(this, "Downloaded, it plays offline now", Toast.LENGTH_SHORT).show() }
                else Toast.makeText(this, "Couldn't download that one", Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    private fun connect() {
        val future = MediaController.Builder(
            this, SessionToken(this, ComponentName(this, PlaybackService::class.java))
        ).buildAsync()
        future.addListener({
            try { controller = future.get() } catch (e: Exception) { return@addListener }
            runOnUiThread { setup() }
        }, MoreExecutors.directExecutor())
    }

    private fun setup() {
        val c = controller ?: return
        findViewById<View>(R.id.mini).visibility = View.VISIBLE
        findViewById<ImageButton>(R.id.btnPlay).setOnClickListener { if (c.isPlaying) c.pause() else c.play() }
        findViewById<ImageButton>(R.id.btnNext).setOnClickListener { c.seekToNextMediaItem() }
        findViewById<ImageButton>(R.id.btnPrev).setOnClickListener { c.seekToPreviousMediaItem() }
        ui.post(tick)
    }

    fun play(tracks: List<Track>, start: Int) {
        val c = controller ?: return
        val items = tracks.map { t -> itemFor(t) }
        c.setMediaItems(items, start.coerceIn(0, (items.size - 1).coerceAtLeast(0)), 0)
        c.prepare()
        c.play()
        startActivity(Intent(this, NowPlayingActivity::class.java))
    }

    private fun playFrom(pos: Int) {
        val c = controller ?: return
        val cur = c.currentMediaItem
        if (cur != null && shown.size > pos && cur.mediaId == shown[pos].ref && c.isPlaying) {
            startActivity(Intent(this, NowPlayingActivity::class.java)); return
        }
        play(shown, pos)
    }

    fun itemFor(t: Track): MediaItem {
        val md = MediaMetadata.Builder()
            .setTitle(t.title).setArtist(t.artist.ifBlank { "Unknown artist" })
        if (t.vid != null) {
            md.setArtworkUri(android.net.Uri.parse("https://i.ytimg.com/vi/" + t.vid + "/mqdefault.jpg"))
        }
        val b = MediaItem.Builder()
            .setMediaId(t.ref)
            .setMediaMetadata(md.build())
        if (t.vid != null) {
            val local = Store.downloaded(this, t.vid)
            if (local != null) b.setUri(android.net.Uri.fromFile(File(local.optString("path"))))
            else b.setUri(android.net.Uri.parse(Downloader.audioUri(t.vid)))
        } else {
            b.setUri(ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, t.localId ?: 0))
        }
        return b.build()
    }

    private fun paintMini() {
        val c = controller ?: return
        val item = c.currentMediaItem ?: return
        findViewById<TextView>(R.id.nowTitle).text = item.mediaMetadata.title ?: ""
        findViewById<ImageButton>(R.id.btnPlay)
            .setImageResource(if (c.isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
    }

    override fun onDestroy() {
        ui.removeCallbacks(tick)
        controller?.release()
        controller = null
        super.onDestroy()
    }
}
