package swapify.app.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.media.AudioManager
import android.os.IBinder
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.File
import kotlin.math.roundToInt

class AudioService : Service() {

    private val CHANNEL_ID = "SwapifyChannel"
    private val NOTIFICATION_ID = 1

    private var isMuted = false
    private var isMuting = false
    private var waitingForLocalSongToEnd = false
    private var originalAlarmVolume: Int = 0
    private var spotifyMusicVolume: Int = 0
    private var userAlarmVolumeWhileLocal: Int? = null
    private var alarmIndexSetOnMute: Int = -1

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private lateinit var audioManager: AudioManager
    private lateinit var localPlayer: swapify.app.player.LocalPlayer
    private lateinit var volumeObserver: ContentObserver

    private val spotifyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getStringExtra("id") ?: return
            val length = intent.getIntExtra("length", 0)
            val position = intent.getIntExtra("playbackPosition", 0)
            val playing = intent.getBooleanExtra("playing", false)

            if (!playing) return

            if (id.startsWith("spotify:track:")) {
                if (isMuted) {
                    if (localPlayer.isActive()) {
                        Log.d("Swapify", "⏸ Tu canción sigue sonando — pausando Spotify de nuevo")
                        pauseSpotify()
                        waitingForLocalSongToEnd = true
                        return
                    } else {
                        unmute()
                    }
                } else {
                    swapify.app.state.PlayerState.isSpotifyPlaying.value = true
                }

                handler.removeCallbacksAndMessages(null)
                val timeLeft = (length - position).toLong()

                // Capturar B aquí, antes del delay
                val capturedB = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                if (capturedB > 0) spotifyMusicVolume = capturedB
                Log.d("Swapify", "🟢 Canción | Tiempo restante: ${timeLeft}ms | B capturado: $spotifyMusicVolume")

                handler.postDelayed({
                    Log.d("Swapify", "🔴 Silenciando — posible anuncio")
                    mute()
                }, timeLeft)

            } else if (id.startsWith("spotify:ad:") || id.isEmpty()) {
                swapify.app.state.PlayerState.isSpotifyPlaying.value = false
                Log.d("Swapify", "🔴 Anuncio — manteniendo silencio")
                if (!isMuted) mute()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        localPlayer = swapify.app.player.LocalPlayer(this)
        swapify.app.state.PlayerState.loadSelectedFolder(this)
        swapify.app.state.PlayerState.localPlayerRef = localPlayer

        originalAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
        spotifyMusicVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        Log.d("Swapify", "📊 Inicial — A: $originalAlarmVolume | B: $spotifyMusicVolume")

        registerVolumeObserver()

        localPlayer.onSongEnded = {
            Log.d("Swapify", "🎵 Canción local terminada | C: $userAlarmVolumeWhileLocal")
            if (waitingForLocalSongToEnd) {
                waitingForLocalSongToEnd = false

                // Libera el ExoPlayer y detiene su bucle de progreso; si se queda
                // vivo tras STATE_ENDED seguiría sincronizando localVolume con el
                // volumen de alarma ya restaurado.
                localPlayer.stop()
                restoreSpotifyVolumes()
                isMuted = false
                swapify.app.state.PlayerState.isPlayingLocal.value = false
                handler.postDelayed({ playSpotify() }, 300)
                swapify.app.state.PlayerState.isSpotifyPlaying.value = true

            }
        }

        swapify.app.state.PlayerState.onPlayRequested = { file ->
            val uri = android.net.Uri.fromFile(file)
            localPlayer.play(uri, 1f)
        }
        swapify.app.state.PlayerState.onPlayRequestedWithVolume = { file, volume ->
            val uri = android.net.Uri.fromFile(file)
            localPlayer.play(uri, volume)
        }
        swapify.app.state.PlayerState.onPauseRequested = {
            localPlayer.pause()
        }
        swapify.app.state.PlayerState.onResumeRequested = {
            localPlayer.resume()
        }

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Swapify activo"))
        registerSpotifyReceiver()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        if (isMuted) unmute()
        unregisterReceiver(spotifyReceiver)
        contentResolver.unregisterContentObserver(volumeObserver)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d("Swapify", "🛑 App cerrada desde recientes — deteniendo todo")
        handler.removeCallbacksAndMessages(null)
        if (isMuted) unmute()
        localPlayer.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onBind(intent: Intent): IBinder? = null

    private fun registerVolumeObserver() {
        volumeObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                val currentMusic = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                val currentAlarm = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)

                if (!isMuted) {
                    if (currentMusic != spotifyMusicVolume) {
                        spotifyMusicVolume = currentMusic
                        Log.d("Swapify", "📊 B actualizado: $spotifyMusicVolume")
                    }
                } else if (!isMuting) {
                    if (userAlarmVolumeWhileLocal == null || currentAlarm != userAlarmVolumeWhileLocal) {
                        userAlarmVolumeWhileLocal = currentAlarm
                        Log.d("Swapify", "📊 C capturado: $userAlarmVolumeWhileLocal")
                    }
                }
            }
        }
        contentResolver.registerContentObserver(
            Settings.System.CONTENT_URI,
            true,
            volumeObserver
        )
    }

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
        Log.d("Swapify", "▶ Comando PLAY enviado a Spotify")
    }

    private fun pauseSpotify() {
        sendMediaButton(android.view.KeyEvent.KEYCODE_MEDIA_PAUSE)
        Log.d("Swapify", "⏸ Comando PAUSE enviado a Spotify")
    }

    private fun loadSongsFromFolder(): List<File> {
        val folder = swapify.app.state.PlayerState.selectedFolder.value
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
        isMuting = true
        isMuted = true
        userAlarmVolumeWhileLocal = null
        swapify.app.state.PlayerState.isSpotifyPlaying.value = false

        val maxMusic = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val maxAlarm = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val relativeVolume = spotifyMusicVolume.toFloat() / maxMusic.toFloat()

        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
        alarmIndexSetOnMute = (relativeVolume * maxAlarm).roundToInt()
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, alarmIndexSetOnMute, 0)
        Log.d("Swapify", "🔴 Muteando | B: $spotifyMusicVolume | Alarma ajustada: $alarmIndexSetOnMute")

        if (swapify.app.state.PlayerState.playlist.isEmpty()) {
            val songs = loadSongsFromFolder()
            if (songs.isNotEmpty()) {
                val randomIndex = songs.indices.random()
                swapify.app.state.PlayerState.setPlaylist(songs, randomIndex)
            }
        }

        if (swapify.app.state.PlayerState.playlist.isNotEmpty()) {
            // Con una sola canción el rango queda vacío y .random() lanzaría
            // NoSuchElementException, matando el servicio (y con él todo el
            // silenciado de anuncios hasta reabrir la app).
            val candidates = swapify.app.state.PlayerState.playlist.indices - swapify.app.state.PlayerState.currentIndex
            val newIndex = if (candidates.isNotEmpty()) candidates.random() else swapify.app.state.PlayerState.currentIndex
            swapify.app.state.PlayerState.currentIndex = newIndex
            swapify.app.state.PlayerState.playCurrentWithVolume(relativeVolume)
            Log.d("Swapify", "🎵 Reproduciendo playlist usuario | B: $spotifyMusicVolume")
        } else {
            val songs = listOf("musica_sin_copyright", "musica_sin_copyright_2", "musica_sin_copyright_3")
            val randomSong = songs.random()
            val uri = android.net.Uri.parse("android.resource://${packageName}/raw/$randomSong")
            localPlayer.play(uri, relativeVolume)
            Log.d("Swapify", "🎵 Reproduciendo fallback: $randomSong")
        }

        isMuting = false
    }

    private fun unmute() {
        if (!isMuted) return
        isMuted = false
        localPlayer.stop()
        swapify.app.state.PlayerState.isPlayingLocal.value = false

        restoreSpotifyVolumes()
        swapify.app.state.PlayerState.isSpotifyPlaying.value = true

    }

    private fun restoreSpotifyVolumes() {
        val maxAlarm = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val maxMusic = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

        // El ContentObserver también captura como C el ajuste de alarma que hace
        // el propio mute(); si C no lo cambió el usuario, restauramos B tal cual.
        // El ida-y-vuelta por la escala de alarma (menos pasos que la de música)
        // truncaba el volumen de Spotify un poco más en cada ciclo de anuncio.
        val c = userAlarmVolumeWhileLocal
        val newMusicVolume = if (c != null && c != alarmIndexSetOnMute && maxAlarm > 0) {
            ((c.toFloat() / maxAlarm.toFloat()) * maxMusic).roundToInt()
        } else {
            spotifyMusicVolume
        }

        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newMusicVolume, 0)
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, originalAlarmVolume, 0)
        userAlarmVolumeWhileLocal = null

        Log.d("Swapify", "🟢 Restaurando | Música: $newMusicVolume | Alarma: $originalAlarmVolume")
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
            "Swapify",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Swapify")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
    }
}