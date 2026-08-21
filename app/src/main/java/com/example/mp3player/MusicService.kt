package com.example.mp3player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.media.app.NotificationCompat.MediaStyle

class MusicService : Service() {

    companion object {
        const val ACTION_PREVIOUS = "com.example.mp3player.ACTION_PREVIOUS"
        const val ACTION_PLAY_PAUSE = "com.example.mp3player.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.example.mp3player.ACTION_NEXT"
    }

    private lateinit var mediaSession: MediaSessionCompat

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        mediaSession = MediaSessionCompat(this, "MusicServiceSession").apply {
            isActive = true
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { MusicActionHandler.onTogglePlayPause?.invoke() }
                override fun onPause() { MusicActionHandler.onTogglePlayPause?.invoke() }
                override fun onSkipToNext() { MusicActionHandler.onNext?.invoke() }
                override fun onSkipToPrevious() { MusicActionHandler.onPrevious?.invoke() }
                override fun onSeekTo(pos: Long) { MusicActionHandler.onSeekTo?.invoke(pos) }
            })
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PREVIOUS -> MusicActionHandler.onPrevious?.invoke()
            ACTION_PLAY_PAUSE -> MusicActionHandler.onTogglePlayPause?.invoke()
            ACTION_NEXT -> MusicActionHandler.onNext?.invoke()
        }

        val title = intent?.getStringExtra("TITLE") ?: "Ismeretlen szám"
        val artist = intent?.getStringExtra("ARTIST") ?: "Ismeretlen előadó"
        val isPlaying = intent?.getBooleanExtra("IS_PLAYING", true) ?: true
        val uriString = intent?.getStringExtra("URI_STRING")
        val duration = intent?.getLongExtra("DURATION", 0L) ?: 0L
        val position = intent?.getLongExtra("POSITION", 0L) ?: 0L

        val coverBitmap = getCoverBitmap(this, uriString)

        val metadataBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)

        if (coverBitmap != null) {
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, coverBitmap)
        }
        mediaSession.setMetadata(metadataBuilder.build())

        val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_SEEK_TO
            )
            .setState(state, position, 1.0f)
            .build()
        mediaSession.setPlaybackState(playbackState)

        val prevPendingIntent = createPendingIntent(ACTION_PREVIOUS, 1)
        val playPausePendingIntent = createPendingIntent(ACTION_PLAY_PAUSE, 2)
        val nextPendingIntent = createPendingIntent(ACTION_NEXT, 3)

        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play

        val builder = NotificationCompat.Builder(this, "default_channel_id")
            .setContentTitle(title)
            .setContentText(artist)
            .setSmallIcon(R.drawable.logo)
            .setLargeIcon(coverBitmap)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_media_previous, "Előző", prevPendingIntent)
            .addAction(playPauseIcon, "Lejátszás/Szünet", playPausePendingIntent)
            .addAction(android.R.drawable.ic_media_next, "Következő", nextPendingIntent)
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(isPlaying)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(101, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(101, builder.build())
        }

        return START_STICKY
    }

    private fun createPendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, MusicService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getCoverBitmap(context: Context, uriString: String?): Bitmap? {
        val defaultBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.logo)
        if (uriString == null) return defaultBitmap
        return try {
            val mmr = MediaMetadataRetriever()
            mmr.setDataSource(context, uriString.toUri())
            val art = mmr.embeddedPicture
            mmr.release()
            if (art != null) {
                BitmapFactory.decodeByteArray(art, 0, art.size)
            } else defaultBitmap
        } catch (_: Exception) {
            defaultBitmap
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "default_channel_id",
                "Zenelejátszó Vezérlő",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaSession.release()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}