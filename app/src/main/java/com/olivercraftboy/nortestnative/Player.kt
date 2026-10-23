package com.olivercraftboy.nortestnative

import android.app.Activity
import android.content.ComponentName
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import java.io.File

/* Shared player plumbing: build the playable item for a track (local file when it was
   downloaded, the audio relay when it streams) and connect a screen to the player service. */
object Player {

    fun itemFor(c: android.content.Context, t: Track): MediaItem {
        val md = MediaMetadata.Builder()
            .setTitle(t.title).setArtist(t.artist.ifBlank { "Unknown artist" })
        if (t.vid != null) {
            md.setArtworkUri(Uri.parse("https://i.ytimg.com/vi/" + t.vid + "/mqdefault.jpg"))
        }
        val b = MediaItem.Builder()
            .setMediaId(t.ref)
            .setMediaMetadata(md.build())
        if (t.vid != null) {
            val local = Store.downloaded(c, t.vid)
            if (local != null) b.setUri(Uri.fromFile(File(local.optString("path"))))
            else b.setUri(Uri.parse(Downloader.audioUri(t.vid)))
        } else {
            b.setUri(android.content.ContentUris.withAppendedId(
                android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, t.localId ?: 0))
        }
        return b.build()
    }

    fun connect(activity: Activity, onReady: (MediaController) -> Unit) {
        val future = MediaController.Builder(
            activity, SessionToken(activity, ComponentName(activity, PlaybackService::class.java))
        ).buildAsync()
        future.addListener({
            try {
                val c = future.get()
                activity.runOnUiThread { onReady(c) }
            } catch (e: Exception) { }
        }, MoreExecutors.directExecutor())
    }

    fun play(activity: Activity, c: MediaController, tracks: List<Track>, start: Int) {
        val items = tracks.map { t -> itemFor(activity, t) }
        if (items.isEmpty()) return
        c.setMediaItems(items, start.coerceIn(0, items.size - 1), 0)
        c.prepare()
        c.play()
    }
}
