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
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.File

class AudioService : Service() {

    private val CHANNEL_ID = "DufyChannel"
    private val NOTIFICATION_ID = 1

    private var isMuted = false
    private var previousVolume: Int = 0
    private var previousAlarmVolume: Int = 0
    private var waitingForLocalSongToEnd = false
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
                if (isMuted) {
                    if (localPlayer.isPlaying()) {
                        Log.d("Dufy", "⏸ Tu canción sigue sonando — pausando Spotify de nuevo | Alarma actual: ${audioManager.getStreamVolume(AudioManager.STREAM_ALARM)}")
                        pauseSpotify()
                        waitingForLocalSongToEnd = true
                        return
                    } else {
                        unmute()
                    }
                }

                handler.removeCallbacksAndMessages(null)
                val timeLeft = (length - position).toLong()
                Log.d("Dufy", "🟢 Canción | Tiempo restante: ${timeLeft}ms")
                handler.postDelayed({
                    Log.d("Dufy", "🔴 Silenciando — posible anuncio")
                    previousVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    previousAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
                    Log.d("Dufy", "💾 Volumen guardado | Música: $previousVolume | Alarma: $previousAlarmVolume")
                    mute()
                }, timeLeft)
            } else if (id.startsWith("spotify:ad:") || id.isEmpty()) {
                Log.d("Dufy", "🔴 Anuncio — manteniendo silencio")
                if (!isMuted) mute()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        localPlayer = dufy.app.player.LocalPlayer(this)
        dufy.app.state.PlayerState.loadSelectedFolder(this)
        dufy.app.state.PlayerState.localPlayerRef = localPlayer

        localPlayer.onSongEnded = {
            Log.d("Dufy", "🎵 Canción local terminada | isMuted=$isMuted | waitingForLocalSongToEnd=$waitingForLocalSongToEnd | previousVolume=$previousVolume")
            if (waitingForLocalSongToEnd) {
                waitingForLocalSongToEnd = false
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, previousVolume, 0)
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, previousAlarmVolume, 0)
                Log.d("Dufy", "🟢 Volumen restaurado | Música actual: ${audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)} | Alarma actual: ${audioManager.getStreamVolume(AudioManager.STREAM_ALARM)}")
                isMuted = false
                dufy.app.state.PlayerState.isPlayingLocal.value = false
                handler.postDelayed({
                    playSpotify()
                }, 300)
            }
        }

        dufy.app.state.PlayerState.onPlayRequested = { file ->
            val uri = android.net.Uri.fromFile(file)
            localPlayer.play(uri, 1f)
        }
        dufy.app.state.PlayerState.onPlayRequestedWithVolume = { file, volume ->
            val uri = android.net.Uri.fromFile(file)
            localPlayer.play(uri, volume)
        }
        dufy.app.state.PlayerState.onPauseRequested = {
            localPlayer.stop()
        }

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

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d("Dufy", "🛑 App cerrada desde recientes — deteniendo todo")
        handler.removeCallbacksAndMessages(null)
        if (isMuted) unmute()
        localPlayer.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onBind(intent: Intent): IBinder? = null

    private fun sendMediaButton(keyCode: Int) {
        val downIntent = Intent(Intent.ACTION_MEDIA_BUTTON)
        downIntent.putExtra(
            Intent.EXTRA_KEY_EVENT,
            android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode)
        )
        downIntent.setPackage("com.spotify.music")
        sendOrderedBroadcast(downIntent, null)

        val upIntent = Intent(Intent.ACTION_MEDIA_BUTTON)
        upIntent.putExtra(
            Intent.EXTRA_KEY_EVENT,
            android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, keyCode)
        )
        upIntent.setPackage("com.spotify.music")
        sendOrderedBroadcast(upIntent, null)
    }

    private fun playSpotify() {
        sendMediaButton(android.view.KeyEvent.KEYCODE_MEDIA_PLAY)
        Log.d("Dufy", "▶ Comando PLAY enviado a Spotify")
    }

    private fun pauseSpotify() {
        sendMediaButton(android.view.KeyEvent.KEYCODE_MEDIA_PAUSE)
        Log.d("Dufy", "⏸ Comando PAUSE enviado a Spotify")
    }

    private fun loadSongsFromFolder(): List<File> {
        val folder = dufy.app.state.PlayerState.selectedFolder.value
        val songs = mutableListOf<File>()
        val projection = arrayOf(MediaStore.Audio.Media.DATA)
        val cursor = contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null,
            null
        )
        cursor?.use {
            val pathCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            while (it.moveToNext()) {
                val path = it.getString(pathCol)
                if (folder.isEmpty() || path.substringBeforeLast("/") == folder) {
                    songs.add(File(path))
                }
            }
        }
        return songs
    }

    private fun mute() {
        isMuted = true
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val relativeVolume = previousVolume.toFloat() / maxVolume.toFloat()

        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
        audioManager.setStreamVolume(
            AudioManager.STREAM_ALARM,
            (relativeVolume * audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)).toInt(),
            0
        )
        Log.d("Dufy", "📊 MUTEANDO | Música: $previousVolume | Alarma: $previousAlarmVolume | RelativeVolume: $relativeVolume")

        if (dufy.app.state.PlayerState.playlist.isEmpty()) {
            val songs = loadSongsFromFolder()
            if (songs.isNotEmpty()) {
                val randomIndex = songs.indices.random()
                dufy.app.state.PlayerState.setPlaylist(songs, randomIndex)
            }
        }

        if (dufy.app.state.PlayerState.playlist.isNotEmpty()) {
            val newIndex = (dufy.app.state.PlayerState.playlist.indices - dufy.app.state.PlayerState.currentIndex).random()
            dufy.app.state.PlayerState.currentIndex = newIndex
            dufy.app.state.PlayerState.playCurrentWithVolume(1f)
            Log.d("Dufy", "🎵 Reproduciendo playlist usuario | Volumen guardado: $previousVolume")
        } else {
            val songs = listOf("musica_sin_copyright", "musica_sin_copyright_2", "musica_sin_copyright_3")
            val randomSong = songs.random()
            val uri = android.net.Uri.parse("android.resource://${packageName}/raw/$randomSong")
            localPlayer.play(uri, 1f)
            Log.d("Dufy", "🎵 Reproduciendo fallback: $randomSong")
        }
    }

    private fun unmute() {
        if (!isMuted) return
        isMuted = false
        localPlayer.stop()
        dufy.app.state.PlayerState.isPlayingLocal.value = false
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, previousVolume, 0)
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, previousAlarmVolume, 0)
        Log.d("Dufy", "🟢 Desilenciando | Música: $previousVolume | Alarma: $previousAlarmVolume")
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