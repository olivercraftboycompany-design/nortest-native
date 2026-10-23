package com.olivercraftboy.nortestnative

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.media3.session.MediaController
import java.net.URL

/* The full player screen: big art, seek bar, shuffle and repeat. Works for songs on the
   phone (their own embedded art) and for streaming songs (the video's thumbnail). */
class NowPlayingActivity : android.app.Activity() {

    private var controller: MediaController? = null
    private var lastId: String? = null
    private var seeking = false
    private val ui = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() { paint(); ui.postDelayed(this, 500) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_now)
        Player.connect(this) { c ->
            controller = c
            setup(c)
            ui.post(tick)
        }
    }

    private fun setup(c: MediaController) {
        findViewById<ImageButton>(R.id.nPlay).setOnClickListener { if (c.isPlaying) c.pause() else c.play() }
        findViewById<ImageButton>(R.id.nNext).setOnClickListener { c.seekToNextMediaItem() }
        findViewById<ImageButton>(R.id.nPrev).setOnClickListener { c.seekToPreviousMediaItem() }
        findViewById<ImageButton>(R.id.nShuffle).setOnClickListener {
            c.shuffleModeEnabled = !c.shuffleModeEnabled
            Toast.makeText(this, if (c.shuffleModeEnabled) "Shuffle on" else "Shuffle off", Toast.LENGTH_SHORT).show()
        }
        findViewById<ImageButton>(R.id.nRepeat).setOnClickListener {
            c.repeatMode = when (c.repeatMode) {
                androidx.media3.common.Player.REPEAT_MODE_OFF -> androidx.media3.common.Player.REPEAT_MODE_ALL
                androidx.media3.common.Player.REPEAT_MODE_ALL -> androidx.media3.common.Player.REPEAT_MODE_ONE
                else -> androidx.media3.common.Player.REPEAT_MODE_OFF
            }
            Toast.makeText(this, when (c.repeatMode) {
                androidx.media3.common.Player.REPEAT_MODE_ALL -> "Repeat all"
                androidx.media3.common.Player.REPEAT_MODE_ONE -> "Repeat this song"
                else -> "Repeat off"
            }, Toast.LENGTH_SHORT).show()
        }
        val sk = findViewById<SeekBar>(R.id.nSeek)
        sk.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                if (fromUser) findViewById<TextView>(R.id.nPos).text = fmt(p.toLong())
            }
            override fun onStartTrackingTouch(s: SeekBar) { seeking = true }
            override fun onStopTrackingTouch(s: SeekBar) {
                seeking = false
                c.seekTo(s.progress.toLong())
            }
        })
        paint()
    }

    @SuppressLint("SetTextI18n")
    private fun paint() {
        val c = controller ?: return
        val item = c.currentMediaItem ?: return
        val id = item.mediaId
        if (id != lastId) {
            lastId = id
            findViewById<TextView>(R.id.nTitle).text = item.mediaMetadata.title ?: ""
            findViewById<TextView>(R.id.nArtist).text = item.mediaMetadata.artist ?: ""
            loadArt(id, item)
        }
        findViewById<ImageButton>(R.id.nPlay)
            .setImageResource(if (c.isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
        findViewById<ImageButton>(R.id.nShuffle)
            .setColorFilter(if (c.shuffleModeEnabled) 0xFF30C859.toInt() else 0x00FFFFFF)
        findViewById<ImageButton>(R.id.nRepeat)
            .setColorFilter(if (c.repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF) 0xFF30C859.toInt() else 0x00FFFFFF)
        val sk = findViewById<SeekBar>(R.id.nSeek)
        if (!seeking) {
            val d = (c.duration.coerceAtLeast(0)).toInt()
            sk.max = d
            sk.progress = c.currentPosition.coerceIn(0, d.toLong()).toInt()
            findViewById<TextView>(R.id.nDur).text = fmt(c.duration.coerceAtLeast(0))
        }
        findViewById<TextView>(R.id.nPos).text = fmt(c.currentPosition.coerceAtLeast(0))
    }

    private fun loadArt(id: String, item: androidx.media3.common.MediaItem) {
        val iv = findViewById<ImageView>(R.id.nArt)
        if (id.startsWith("Y:")) {
            val vid = id.substring(2)
            Thread {
                val bmp = try {
                    val conn = URL("https://i.ytimg.com/vi/" + vid + "/mqdefault.jpg").openConnection()
                    conn.connectTimeout = 10000; conn.readTimeout = 10000
                    val s = conn.getInputStream()
                    val b = BitmapFactory.decodeStream(s); s.close(); b
                } catch (e: Exception) { null }
                if (bmp != null) runOnUiThread { iv.setImageBitmap(bmp) }
            }.start()
        } else {
            val uri = item.localConfiguration?.uri
            Thread {
                var bmp: Bitmap? = null
                if (uri != null) {
                    try {
                        val r = MediaMetadataRetriever()
                        r.setDataSource(this, uri)
                        val bytes = r.embeddedPicture
                        r.release()
                        if (bytes != null) bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    } catch (e: Exception) { }
                }
                if (bmp == null) {
                    try {
                        val q = "https://itunes.apple.com/search?term=" + Net.enc(
                            (item.mediaMetadata.title?.toString() ?: "") + " " + (item.mediaMetadata.artist?.toString() ?: "")
                        ) + "&entity=song&limit=1"
                        val j = Net.getJson(q)
                        val url = j?.optJSONArray("results")?.optJSONObject(0)?.optString("artworkUrl100", "")
                        if (!url.isNullOrEmpty()) {
                            val conn = URL(url.replace("100x100", "600x600")).openConnection()
                            conn.connectTimeout = 10000; conn.readTimeout = 10000
                            val s = conn.getInputStream()
                            bmp = BitmapFactory.decodeStream(s); s.close()
                        }
                    } catch (e: Exception) { }
                }
                if (bmp != null) runOnUiThread { iv.setImageBitmap(bmp) }
            }.start()
        }
    }

    private fun fmt(ms: Long): String {
        val s = ms / 1000
        return (s / 60).toString() + ":" + ((s % 60).toString().padStart(2, '0'))
    }

    override fun onDestroy() {
        ui.removeCallbacks(tick)
        controller?.release()
        controller = null
        super.onDestroy()
    }
}
