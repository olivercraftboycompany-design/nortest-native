package com.olivercraftboy.nortestnative

import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/* The player that keeps music going with the screen off: one ExoPlayer, a media session for
   the lock screen and notification, audio focus and "stop on unplug" included. It also keeps
   the play history that powers Wrapped (a song counts once it has played past 30 seconds). */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var loggedId: String? = null
    private val ui = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            val s = mediaSession?.player
            if (s != null && s.isPlaying) {
                val id = s.currentMediaItem?.mediaId
                if (id != null && id != loggedId && s.currentPosition > 30000) {
                    loggedId = id
                    val m = s.currentMediaItem?.mediaMetadata
                    Store.logListen(
                        this@PlaybackService,
                        m?.title?.toString() ?: "", m?.artist?.toString() ?: "", s.duration
                    )
                }
            }
            ui.postDelayed(this, 5000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        mediaSession = MediaSession.Builder(this, player).build()
        ui.post(tick)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady ||
            player.mediaItemCount == 0 || player.playbackState == Player.STATE_ENDED
        ) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        ui.removeCallbacks(tick)
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
