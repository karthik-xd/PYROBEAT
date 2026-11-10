package com.skullbeat.app.player.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.palette.graphics.Palette
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import coil.ImageLoader
import coil.request.ImageRequest
import com.skullbeat.app.MainActivity
import com.skullbeat.app.R
import com.skullbeat.app.domain.models.Track
import com.skullbeat.app.player.manager.PlayerManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject
    lateinit var playerManager: PlayerManager

    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var lastTrackId: String? = null
    private var lastBitmap: Bitmap? = null

    companion object {
        const val CHANNEL_ID = "skullbeat_playback_channel_v2"
        const val NOTIFICATION_ID = 1001

        const val ACTION_SHUFFLE = "com.skullbeat.app.ACTION_SHUFFLE"
        const val ACTION_PREVIOUS = "com.skullbeat.app.ACTION_PREVIOUS"
        const val ACTION_PLAY_PAUSE = "com.skullbeat.app.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.skullbeat.app.ACTION_NEXT"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val forwardingPlayer = object : ForwardingPlayer(playerManager.exoPlayer) {
            override fun isCommandAvailable(command: Int): Boolean {
                return when (command) {
                    Player.COMMAND_SEEK_TO_NEXT,
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                    Player.COMMAND_SEEK_TO_PREVIOUS,
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> true
                    else -> super.isCommandAvailable(command)
                }
            }

            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands().buildUpon()
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .build()
            }

            override fun seekToNext() {
                playerManager.next()
            }

            override fun seekToNextMediaItem() {
                playerManager.next()
            }

            override fun seekToPrevious() {
                playerManager.previous()
            }

            override fun seekToPreviousMediaItem() {
                playerManager.previous()
            }
        }

        val sessionCallback = object : MediaSession.Callback {
            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle
            ): ListenableFuture<SessionResult> {
                if (customCommand.customAction == ACTION_SHUFFLE) {
                    playerManager.toggleShuffle()
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                return super.onCustomCommand(session, controller, customCommand, args)
            }
        }

        mediaSession = MediaSession.Builder(this, forwardingPlayer)
            .setSessionActivity(pendingIntent)
            .setCallback(sessionCallback)
            .build()

        serviceScope.launch {
            playerManager.playbackState.collectLatest { state ->
                val track = state.currentTrack
                if (track != null) {
                    updateNotification(track, state.isPlaying)
                } else {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHUFFLE -> playerManager.toggleShuffle()
            ACTION_PREVIOUS -> playerManager.previous()
            ACTION_PLAY_PAUSE -> playerManager.togglePlayPause()
            ACTION_NEXT -> playerManager.next()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        val track = playerManager.playbackState.value.currentTrack
        if (track != null) {
            serviceScope.launch {
                updateNotification(track, playerManager.playbackState.value.isPlaying)
            }
        }
    }

    private suspend fun updateNotification(track: Track, isPlaying: Boolean) {
        val artwork = getOrLoadArtwork(track)

        val palette = withContext(Dispatchers.Default) {
            Palette.from(artwork).generate()
        }
        val dominantRgb = palette.dominantSwatch?.rgb ?: 0xFF1E1035.toInt()
        val vibrantRgb = palette.vibrantSwatch?.rgb
            ?: palette.lightVibrantSwatch?.rgb
            ?: palette.darkVibrantSwatch?.rgb
            ?: palette.mutedSwatch?.rgb
            ?: 0xFF00E5FF.toInt()

        // Vibrant accent color for high-visibility notification tinting
        val primaryColorInt = vibrantRgb
        val secondaryColorInt = dominantRgb

        playerManager.updateColors(
            primary = primaryColorInt.toLong() and 0xFFFFFFFFL,
            secondary = secondaryColorInt.toLong() and 0xFFFFFFFFL
        )

        val artworkBytes = withContext(Dispatchers.Default) {
            val stream = java.io.ByteArrayOutputStream()
            artwork.compress(Bitmap.CompressFormat.JPEG, 90, stream)
            stream.toByteArray()
        }
        playerManager.updateArtworkData(artworkBytes)

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val isShuffle = playerManager.playbackState.value.isShuffle
        val shuffleCommand = CommandButton.Builder()
            .setDisplayName(if (isShuffle) "Shuffle On" else "Shuffle Off")
            .setIconResId(R.drawable.ic_shuffle)
            .setSessionCommand(SessionCommand(ACTION_SHUFFLE, Bundle.EMPTY))
            .setEnabled(true)
            .build()
        mediaSession?.setCustomLayout(listOf(shuffleCommand))

        val shuffleIntent = PendingIntent.getService(
            this,
            4,
            Intent(this, PlaybackService::class.java).apply { action = ACTION_SHUFFLE },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val shuffleAction = NotificationCompat.Action.Builder(
            R.drawable.ic_shuffle,
            if (isShuffle) "Shuffle On" else "Shuffle Off",
            shuffleIntent
        ).build()

        val prevIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, PlaybackService::class.java).apply { action = ACTION_PREVIOUS },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val prevAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_media_previous,
            "Previous",
            prevIntent
        ).build()

        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseTitle = if (isPlaying) "Pause" else "Play"
        val playPauseIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, PlaybackService::class.java).apply { action = ACTION_PLAY_PAUSE },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val playPauseAction = NotificationCompat.Action.Builder(
            playPauseIcon,
            playPauseTitle,
            playPauseIntent
        ).build()

        val nextIntent = PendingIntent.getService(
            this,
            3,
            Intent(this, PlaybackService::class.java).apply { action = ACTION_NEXT },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val nextAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_media_next,
            "Next",
            nextIntent
        ).build()

        val mediaStyle = MediaStyle()
            .setShowActionsInCompactView(0, 2, 3)

        val sessionCompat = mediaSession?.sessionCompatToken
        if (sessionCompat != null) {
            mediaStyle.setMediaSession(sessionCompat)
        }

        val themedArtwork = createThemedArtwork(artwork, primaryColorInt, secondaryColorInt)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(track.title)
            .setContentText(track.artist)
            .setSubText(track.album.ifBlank { "SkullBeat" })
            .setLargeIcon(themedArtwork)
            .setContentIntent(contentIntent)
            .setColor(primaryColorInt)
            .setColorized(true)
            .addAction(shuffleAction)
            .addAction(prevAction)
            .addAction(playPauseAction)
            .addAction(nextAction)
            .setStyle(mediaStyle)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .setSilent(true)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (_: Exception) {
            val manager = getSystemService(NotificationManager::class.java)
            manager?.notify(NOTIFICATION_ID, notification)
        }
    }

    private suspend fun getOrLoadArtwork(track: Track): Bitmap {
        if (track.videoId == lastTrackId && lastBitmap != null) {
            return lastBitmap!!
        }

        val loaded = withContext(Dispatchers.IO) {
            try {
                if (track.thumbnailUrl.isNotBlank()) {
                    val loader = ImageLoader(this@PlaybackService)
                    val request = ImageRequest.Builder(this@PlaybackService)
                        .data(track.thumbnailUrl)
                        .allowHardware(false)
                        .build()
                    val result = loader.execute(request)
                    (result.drawable as? BitmapDrawable)?.bitmap
                } else null
            } catch (_: Exception) {
                null
            }
        }

        val finalBitmap = loaded ?: BitmapFactory.decodeResource(resources, R.drawable.logo)
        lastTrackId = track.videoId
        lastBitmap = finalBitmap
        return finalBitmap
    }

    private fun createThemedArtwork(original: Bitmap, color1: Int, color2: Int): Bitmap {
        return try {
            val size = 512
            val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)

            val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, 0f, size.toFloat(), size.toFloat(),
                    color1, color2,
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRoundRect(0f, 0f, size.toFloat(), size.toFloat(), 48f, 48f, gradientPaint)

            val inset = 44f
            val artRect = RectF(inset, inset, size - inset, size - inset)
            val scaledArt = Bitmap.createScaledBitmap(original, (size - inset * 2).toInt(), (size - inset * 2).toInt(), true)

            val clipPath = android.graphics.Path().apply {
                addRoundRect(artRect, 32f, 32f, android.graphics.Path.Direction.CW)
            }
            canvas.save()
            canvas.clipPath(clipPath)
            canvas.drawBitmap(scaledArt, inset, inset, Paint(Paint.ANTI_ALIAS_FLAG))
            canvas.restore()

            output
        } catch (_: Exception) {
            original
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Playback Controls",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "SkullBeat media playback notification and controls"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        mediaSession?.run {
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
