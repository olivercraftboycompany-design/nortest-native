package com.olivercraftboy.nortestnative

import android.Manifest
import android.content.ComponentName
import android.content.ContentUris
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.View
import android.widget.ImageButton
import android.widget.ListView
import android.widget.SimpleAdapter
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors

/* Version 0.1: your phone's music library in a list, tap a song to play it, and a small
   bar with play / pause / next / previous. Playback runs in a real player service, so it
   keeps going with the screen off and shows lock-screen controls. */
class MainActivity : android.app.Activity() {

    private class Song(val id: Long, val title: String, val artist: String)

    private val songs = ArrayList<Song>()
    private var controller: MediaController? = null
    private val ui = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            paintMini()
            ui.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        needPermission()
    }

    private fun needPermission() {
        val p = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
        if (checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED) start()
        else requestPermissions(arrayOf(p), 1)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            start()
        } else {
            findViewById<TextView>(R.id.heading).text =
                "Allow Nortest Native to see your music, then tap here to try again"
        }
    }

    private fun start() {
        loadLibrary()
        connect()
    }

    private fun loadLibrary() {
        songs.clear()
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
            while (c.moveToNext()) {
                songs.add(Song(c.getLong(iId), c.getString(iT) ?: "Untitled", c.getString(iA) ?: ""))
            }
        }
        val rows = ArrayList<Map<String, String>>()
        for (s in songs) {
            val m = HashMap<String, String>()
            m["t"] = s.title
            m["a"] = s.artist.ifBlank { "Unknown artist" }
            rows.add(m)
        }
        val list = findViewById<ListView>(R.id.songs)
        list.adapter = SimpleAdapter(
            this, rows, android.R.layout.simple_list_item_2,
            arrayOf("t", "a"), intArrayOf(android.R.id.text1, android.R.id.text2)
        )
        list.setOnItemClickListener { _, _, pos, _ -> playFrom(pos) }
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

    private fun playFrom(pos: Int) {
        val c = controller ?: return
        val items = songs.map { s ->
            MediaItem.Builder()
                .setMediaId(s.id.toString())
                .setUri(ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, s.id))
                .setMediaMetadata(MediaMetadata.Builder().setTitle(s.title).setArtist(s.artist).build())
                .build()
        }
        c.setMediaItems(items, pos, 0)
        c.prepare()
        c.play()
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
