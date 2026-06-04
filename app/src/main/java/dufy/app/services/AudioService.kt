package dufy.app.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

class AudioService : Service() {

    private val CHANNEL_ID = "DufyChannel"
    private val NOTIFICATION_ID = 1

    private var isMuted = false
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private lateinit var audioManager: AudioManager
    private lateinit var localPlayer: dufy.app.player.LocalPlayer

    private val spotifyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getStringExtra("id") ?: return
            val length = intent.getIntExtra("length", 0)
            val position = intent.getIntExtra("playbackPosition", 0)
            val playing = intent.getBooleanExtra("playing", false)

            if (!playing) return

            if (id.startsWith("spotify:track:")) {
                // Nueva canción — desilenciar siempre
                if (isMuted) unmute()

                // Programar silencio al acabar la canción
                handler.removeCallbacksAndMessages(null)
                val timeLeft = (length - position).toLong()
                Log.d("Dufy", "🟢 Canción | Tiempo restante: ${timeLeft}ms")
                handler.postDelayed({
                    Log.d("Dufy", "🔴 Silenciando — posible anuncio")
                    mute()
                }, timeLeft)
            }
            // Si es anuncio confirmado, simplemente silenciar y no hacer nada más
            else if (id.startsWith("spotify:ad:") || id.isEmpty()) {
                Log.d("Dufy", "🔴 Anuncio — manteniendo silencio")
                if (!isMuted) mute()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        localPlayer = dufy.app.player.LocalPlayer(this)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Dufy activo"))
        registerSpotifyReceiver()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        if (isMuted) unmute()
        unregisterReceiver(spotifyReceiver)
    }

    override fun onBind(intent: Intent): IBinder? = null

    private fun mute() {
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            AudioManager.ADJUST_MUTE,
            0
        )
        isMuted = true

        val songs = listOf("musica_sin_copyright", "musica_sin_copyright_2", "musica_sin_copyright_3")
        val randomSong = songs.random()
        val uri = android.net.Uri.parse("android.resource://${packageName}/raw/$randomSong")
        localPlayer.play(uri)
        Log.d("Dufy", "🎵 Reproduciendo: $randomSong")
    }

    private fun unmute() {
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            AudioManager.ADJUST_UNMUTE,
            0
        )
        isMuted = false
        localPlayer.stop()
        Log.d("Dufy", "🟢 Desilenciando — nueva canción")
    }

    private fun registerSpotifyReceiver() {
        val filter = IntentFilter().apply {
            addAction("com.spotify.music.playbackstatechanged")
            addAction("com.spotify.music.metadatachanged")
            addAction("com.spotify.music.queuechanged")
        }
        registerReceiver(spotifyReceiver, filter, RECEIVER_EXPORTED)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Dufy",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Dufy")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
    }
}