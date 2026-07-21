package swapifi.app.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.IBinder
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import java.io.File
import kotlin.math.roundToInt

class AudioService : Service() {

    private val CHANNEL_ID = "SwapifiChannel"
    private val NOTIFICATION_ID = 1

    companion object {
        private const val ACTION_PLAY = "swapifi.app.action.PLAY"
        private const val ACTION_PAUSE = "swapifi.app.action.PAUSE"
        private const val ACTION_NEXT = "swapifi.app.action.NEXT"
        private const val ACTION_PREVIOUS = "swapifi.app.action.PREVIOUS"

        // Los broadcasts de la siguiente canción suelen llegar justo en la
        // transición; disparar un poco después del final previsto deja que el
        // removeCallbacks del receiver cancele el temporizador en la mayoría de
        // transiciones normales. Coste: un anuncio real suena este margen antes
        // de silenciarse.
        private const val MUTE_MARGIN_MS = 300L
        // Ventana de confirmación de anuncio: Spotify emite el broadcast de la
        // siguiente canción en <1 s normalmente; si no llega en este margen,
        // era un anuncio de verdad.
        private const val AD_CONFIRM_WINDOW_MS = 1300L

        // Reintento tras restaurar A: el sistema a veces empuja el volumen de
        // alarma mientras el stream aún se está desactivando; con el stream ya
        // parado del todo, una segunda escritura sí se mantiene (ver
        // alarmReassertRunnable).
        private const val ALARM_REASSERT_DELAY_MS = 1500L
        // Ventana de eco de nuestras propias escrituras de alarma. DEBE cubrir más
        // allá de ALARM_REASSERT_DELAY_MS: si terminara antes (como ocurría con un
        // valor fijo de 1000ms < 1500ms de reintento), un empujón del sistema en esa
        // brecha se leía como un cambio genuino del usuario y corrompía A de forma
        // permanente y acumulativa ciclo a ciclo (bug confirmado — ver CONTEXT.md,
        // "Compensación de volumen en auriculares Bluetooth"). Se deriva del
        // reintento en vez de fijarse por separado, precisamente para que esta
        // brecha no pueda reabrirse si alguno de los dos valores cambia sin el otro.
        // +500ms de margen para la latencia/jitter del propio reintento y de la
        // entrega del broadcast.
        private const val ALARM_ECHO_GUARD_MS = ALARM_REASSERT_DELAY_MS + 500L
    }

    private var isMuted = false
    private var isMuting = false
    // Fase 1 del mute en dos fases: Spotify ya está a 0 pero la música local
    // aún no ha arrancado, a la espera de confirmar que es un anuncio.
    // Invariante: nunca true a la vez que isMuted.
    private var pendingAdConfirmation = false
    private var waitingForLocalSongToEnd = false
    private var originalAlarmVolume: Int = 0
    private var spotifyMusicVolume: Int = 0
    private var userAlarmVolumeWhileLocal: Int? = null
    private var alarmIndexSetOnMute: Int = -1
    private var lastOwnAlarmWriteAt = 0L
    private var lastSpotifyBroadcastAt = 0L

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val muteRunnable = Runnable {
        Log.d("Swapifi", "🟠 Fin de canción previsto — silenciando y esperando confirmación")
        startPendingMute()
    }

    // Fase 2: si en toda la ventana no llegó el broadcast de la siguiente
    // canción, era un anuncio de verdad — mute completo con música local.
    private val confirmAdRunnable = Runnable {
        if (!pendingAdConfirmation) return@Runnable
        pendingAdConfirmation = false
        // Fin de cola: no hay anuncio ni canción sonando, así que arrancar la
        // música local dejaría al usuario con ella indefinidamente (nada la
        // desmutearía). isMusicActive no depende del volumen, así que un
        // anuncio activo con el stream a 0 no se cuela por aquí.
        if (!audioManager.isMusicActive) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, spotifyMusicVolume, 0)
            Log.d("Swapifi", "🔎 Nada sonando tras la ventana — cola terminada, sin música local")
            return@Runnable
        }
        Log.d("Swapifi", "🔴 Sin canción nueva tras la ventana — anuncio confirmado")
        mute()
    }

    // El sistema empuja el volumen de alarma (p. ej. a 3) justo después de que
    // la restauración escriba un valor bajo, mientras el stream aún se está
    // desactivando. Con el stream ya parado del todo, una segunda escritura sí
    // se mantiene, así que reintentamos una única vez pasado un margen.
    private val alarmReassertRunnable = Runnable {
        if (isMuted) return@Runnable
        val current = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
        if (current != originalAlarmVolume) {
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, originalAlarmVolume, 0)
            lastOwnAlarmWriteAt = android.os.SystemClock.elapsedRealtime()
            Log.d("Swapifi", "🔁 Sistema dejó la alarma en $current — reintentando A: $originalAlarmVolume")
        }
    }
    private lateinit var audioManager: AudioManager
    private lateinit var localPlayer: swapifi.app.player.LocalPlayer
    private lateinit var volumeObserver: ContentObserver
    private lateinit var mediaSession: MediaSession

    private val spotifyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Marca de vida de Spotify: la sonda de arranque la usa para saber
            // si el audio activo era suyo antes de devolverle el PLAY.
            lastSpotifyBroadcastAt = android.os.SystemClock.elapsedRealtime()
            val id = intent.getStringExtra("id") ?: return
            val length = intent.getIntExtra("length", 0)
            val position = intent.getIntExtra("playbackPosition", 0)
            val playing = intent.getBooleanExtra("playing", false)

            if (!playing) {
                // Pausa manual de Spotify a mitad de canción: sin esto, el mute
                // programado al llegar la canción seguiría armado y dispararía
                // la música local a la hora en que la canción habría terminado.
                // Solo se cancela lejos del final (>3 s) y con posición real
                // (>1 s): un playing=false pegado al final o con posición 0 es
                // indistinguible de la transición canción→anuncio, y ahí el
                // temporizador debe seguir vivo para silenciar el anuncio.
                if (!isMuted && id.startsWith("spotify:track:") &&
                    length > 0 && position > 1000 && length - position > 3000
                ) {
                    handler.removeCallbacks(muteRunnable)
                    cancelPendingMute()
                    swapifi.app.state.PlayerState.isSpotifyPlaying.value = false
                    Log.d("Swapifi", "⏸ Pausa manual de Spotify — mute cancelado (pos=$position/$length)")
                }
                return
            }

            if (id.startsWith("spotify:track:")) {
                // La "siguiente canción" llegó dentro de la ventana: era una
                // transición normal, no un anuncio. Restaurar volumen y seguir
                // el flujo normal.
                cancelPendingMute()
                if (isMuted) {
                    if (localPlayer.isActive()) {
                        Log.d("Swapifi", "⏸ Tu canción sigue sonando — pausando Spotify de nuevo")
                        pauseSpotify()
                        waitingForLocalSongToEnd = true
                        return
                    } else {
                        unmute()
                    }
                } else {
                    swapifi.app.state.PlayerState.isSpotifyPlaying.value = true
                }

                // Cancela solo el mute pendiente: un removeCallbacksAndMessages(null)
                // aquí borraría también las notificaciones pendientes del
                // ContentObserver (B/C) y el playSpotify() diferido de 300 ms.
                handler.removeCallbacks(muteRunnable)

                // Estos broadcasts no están protegidos: cualquier app puede
                // falsificar los extras. Sin esta validación, un length/position
                // manipulado daría un timeLeft negativo y postDelayed ejecutaría
                // mute() inmediatamente durante la reproducción normal.
                if (length <= 0 || position < 0 || position > length) {
                    Log.d("Swapifi", "⚠ Extras inválidos (length=$length, position=$position) — mute no programado")
                    return
                }
                val timeLeft = (length - position).toLong()

                // Capturar B aquí, antes del delay
                val capturedB = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                if (capturedB > 0) spotifyMusicVolume = capturedB
                Log.d("Swapifi", "🟢 Canción | Tiempo restante: ${timeLeft}ms | B capturado: $spotifyMusicVolume")

                handler.postDelayed(muteRunnable, timeLeft + MUTE_MARGIN_MS)

            } else if (id.startsWith("spotify:ad:")) {
                swapifi.app.state.PlayerState.isSpotifyPlaying.value = false
                Log.d("Swapifi", "🔴 Anuncio — manteniendo silencio")
                handler.removeCallbacks(muteRunnable)
                // Limpiar la ventana sin restaurar volumen: mute() vuelve a
                // dejar el stream a 0 y una restauración intermedia haría
                // sonar el anuncio un instante.
                handler.removeCallbacks(confirmAdRunnable)
                pendingAdConfirmation = false
                if (!isMuted) mute()
            } else if (id.isEmpty()) {
                // id vacío es ambiguo (puede venir de metadatos incompletos en
                // cualquiera de los tres broadcasts): en vez de mute inmediato,
                // pasa por la ventana de confirmación.
                Log.d("Swapifi", "🟠 id vacío — posible anuncio, ventana de confirmación")
                if (!isMuted) startPendingMute()
            }
        }
    }

    // A diferencia del ContentObserver (que se dispara por cualquier ajuste del
    // sistema y RELEE los volúmenes, pudiendo pillar lecturas transitorias justo
    // al desactivarse el stream de alarma), este broadcast trae el stream y el
    // valor exactos del cambio real, así que A no puede corromperse con valores
    // fantasma.
    private val volumeChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val stream = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1)
            if (stream != AudioManager.STREAM_ALARM) return
            val value = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_VALUE", -1)
            if (value < 0) return
            // Justo tras escribir nosotros la alarma, el sistema puede reajustarla
            // por su cuenta (p. ej. empujándola a 3 al desactivarse el stream) y
            // ese eco llegaría aquí como si fuera un cambio del usuario,
            // corrompiendo A. Los eventos de esa ventana se ignoran; el reintento
            // diferido se encarga de volver a imponer A después.
            if (android.os.SystemClock.elapsedRealtime() - lastOwnAlarmWriteAt < ALARM_ECHO_GUARD_MS) {
                Log.d("Swapifi", "📊 Eco de escritura propia ignorado (alarma=$value)")
                return
            }
            // Con Spotify sonando, el volumen de alarma que fija el usuario pasa
            // a ser el que se restaura al volver de la música local (A). Durante
            // el silenciado los cambios van a C via ContentObserver, como antes.
            // localPlayer.isActive() cubre también la reproducción manual (fuera
            // de un anuncio): LocalPlayer escribe STREAM_ALARM por su cuenta
            // mientras suena por BT (maybeTeachBtAlarmBaseline, Fix B) y esa
            // escritura genera un VOLUME_CHANGED_ACTION real e indistinguible de
            // un cambio del usuario — sin este guard, A se corrompía al valor
            // que LocalPlayer usa para "enseñar" la curva BT.
            if (!isMuted && !localPlayer.isActive() && value != originalAlarmVolume) {
                originalAlarmVolume = value
                Log.d("Swapifi", "📊 A actualizado: $originalAlarmVolume")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        localPlayer = swapifi.app.player.LocalPlayer(this)
        swapifi.app.state.PlayerState.loadSelectedFolder(this)
        swapifi.app.state.PlayerState.localPlayerRef = localPlayer

        originalAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
        spotifyMusicVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        Log.d("Swapifi", "📊 Inicial — A: $originalAlarmVolume | B: $spotifyMusicVolume")

        registerVolumeObserver()
        createMediaSession()

        localPlayer.onSongEnded = {
            Log.d("Swapifi", "🎵 Canción local terminada | C: $userAlarmVolumeWhileLocal")
            if (waitingForLocalSongToEnd) {
                waitingForLocalSongToEnd = false

                // Libera el ExoPlayer y detiene su bucle de progreso; si se queda
                // vivo tras STATE_ENDED seguiría sincronizando localVolume con el
                // volumen de alarma ya restaurado.
                localPlayer.stop()
                restoreSpotifyVolumes()
                isMuted = false
                swapifi.app.state.PlayerState.isPlayingLocal.value = false
                handler.postDelayed({ playSpotify() }, 300)
                swapifi.app.state.PlayerState.isSpotifyPlaying.value = true

            }
            updateMediaNotification()
        }

        localPlayer.onReady = {
            updateMediaNotification()
        }

        swapifi.app.state.PlayerState.onPlayRequested = { file ->
            val uri = android.net.Uri.fromFile(file)
            // Reproducción manual (next/previous/resume): aquí no hay un mute()
            // previo que haya escrito el índice de alarma, así que se pasa la
            // fracción del índice actual. Con 1f fijo, LocalPlayer concluiría
            // que el índice no manda (indexControlsGain=false) y los botones de
            // volumen meterían atenuación doble vía player.volume.
            val maxAlarm = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val fraction = if (maxAlarm > 0) {
                audioManager.getStreamVolume(AudioManager.STREAM_ALARM).toFloat() / maxAlarm
            } else 1f
            localPlayer.play(uri, fraction)
            updateMediaNotification()
        }
        swapifi.app.state.PlayerState.onPlayRequestedWithVolume = { file, volume ->
            val uri = android.net.Uri.fromFile(file)
            localPlayer.play(uri, volume)
            updateMediaNotification()
        }
        swapifi.app.state.PlayerState.onPauseRequested = {
            localPlayer.pause()
            updateMediaNotification()
        }
        swapifi.app.state.PlayerState.onResumeRequested = {
            val resumed = localPlayer.resume()
            if (resumed) updateMediaNotification()
            resumed
        }

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(getString(swapifi.app.R.string.notification_active)))
        registerSpotifyReceiver()
        // NOT_EXPORTED: los broadcasts del sistema llegan igualmente y ninguna
        // app de terceros puede falsificarlos para manipular A.
        registerReceiver(
            volumeChangedReceiver,
            IntentFilter("android.media.VOLUME_CHANGED_ACTION"),
            RECEIVER_NOT_EXPORTED
        )

        // Anuncio ya en curso al abrir la app: Spotify solo avisa en los cambios
        // de estado, así que un arranque en mitad de un anuncio (o canción) nos
        // dejaría ciegos hasta el siguiente cambio. Sonda: con audio activo se
        // envía PAUSE a Spotify; si era él quien sonaba responde con un broadcast
        // y se le devuelve PLAY, cuyo broadcast trae el id actual (anuncio →
        // mute inmediato, canción → mute programado). Si sonaba otra app,
        // Spotify no responde y no se envía PLAY, para no arrancarlo por
        // accidente. Coste: un corte de <1 s al abrir.
        if (audioManager.isMusicActive) {
            handler.postDelayed({
                val probeStart = android.os.SystemClock.elapsedRealtime()
                // B leído con el audio aún sonando: es el volumen real del
                // anuncio en curso. Tras el PAUSE, Spotify reajusta a veces su
                // stream por su cuenta y el ContentObserver dejaría en B ese
                // valor transitorio; mute() traspasaría entonces a la alarma
                // un volumen que no es el que se estaba oyendo.
                val liveMusicVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                pauseSpotify()
                handler.postDelayed({
                    when {
                        // Respondió con broadcast: era una canción. El PLAY
                        // provoca otro broadcast con el id y posición actuales,
                        // que deja el mute programado para el final.
                        lastSpotifyBroadcastAt >= probeStart -> playSpotify()

                        // Sin broadcast pero el audio se detuvo: solo Spotify
                        // recibió el PAUSE, así que era él sonando con algo que
                        // no se anuncia — un anuncio (los anuncios no emiten
                        // playbackstatechanged). Silenciar primero y reanudar
                        // después, para que el anuncio vuelva ya en silencio y
                        // al terminar llegue el broadcast de la siguiente
                        // canción, que restaura el flujo normal.
                        !audioManager.isMusicActive -> {
                            Log.d("Swapifi", "🔴 Anuncio en curso al abrir — silenciando")
                            if (liveMusicVolume > 0) spotifyMusicVolume = liveMusicVolume
                            if (!isMuted) mute()
                            playSpotify()
                        }

                        else -> Log.d("Swapifi", "🔎 Sin respuesta de Spotify — era otro reproductor")
                    }
                }, 1200)
            }, 500)
        }
    }

    // Los botones de la notificación (pre-Android 13) llegan como intents con
    // acción; se enrutan por PlayerState para que la UI de la app quede en
    // sincronía, igual que los botones en pantalla.
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> swapifi.app.state.PlayerState.resume()
            ACTION_PAUSE -> swapifi.app.state.PlayerState.pause()
            ACTION_NEXT -> swapifi.app.state.PlayerState.next()
            ACTION_PREVIOUS -> swapifi.app.state.PlayerState.previous()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        // Si el servicio muere con la ventana abierta, el volumen de Spotify
        // quedaría atascado en 0.
        cancelPendingMute()
        if (isMuted) unmute()
        mediaSession.release()
        unregisterReceiver(spotifyReceiver)
        unregisterReceiver(volumeChangedReceiver)
        contentResolver.unregisterContentObserver(volumeObserver)
        // El singleton PlayerState sobrevive al servicio; sin esto retendría el
        // LocalPlayer (y su Context) tras morir el servicio — fuga de memoria.
        swapifi.app.state.PlayerState.localPlayerRef = null
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d("Swapifi", "🛑 App cerrada desde recientes — deteniendo todo")
        handler.removeCallbacksAndMessages(null)
        cancelPendingMute()
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

                // Durante la ventana de confirmación el 0 escrito por
                // startPendingMute() dispararía este onChange y corrompería B
                // a 0: la música local sonaría a volumen 0 y la restauración
                // devolvería 0.
                if (!isMuted && !pendingAdConfirmation) {
                    if (currentMusic != spotifyMusicVolume) {
                        spotifyMusicVolume = currentMusic
                        Log.d("Swapifi", "📊 B actualizado: $spotifyMusicVolume")
                    }
                } else if (!isMuting) {
                    if (userAlarmVolumeWhileLocal == null || currentAlarm != userAlarmVolumeWhileLocal) {
                        userAlarmVolumeWhileLocal = currentAlarm
                        Log.d("Swapifi", "📊 C capturado: $userAlarmVolumeWhileLocal")
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
        Log.d("Swapifi", "▶ Comando PLAY enviado a Spotify")
    }

    private fun pauseSpotify() {
        sendMediaButton(android.view.KeyEvent.KEYCODE_MEDIA_PAUSE)
        Log.d("Swapifi", "⏸ Comando PAUSE enviado a Spotify")
    }

    private fun loadSongsFromFolder(): List<File> {
        val folder = swapifi.app.state.PlayerState.selectedFolder.value
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

    // Fase 1: baja Spotify a 0 (barato y reversible al instante) pero NO
    // arranca la música local todavía. Si era una transición normal, el
    // broadcast de la siguiente canción cancela esto y el usuario solo nota un
    // micro-bajón de volumen en el borde de la canción (que suele acabar en
    // silencio de todos modos). Solo toca STREAM_MUSIC, nunca STREAM_ALARM,
    // así que no interactúa con el debounce de eco del volumen A.
    private fun startPendingMute() {
        if (isMuted || pendingAdConfirmation) return
        pendingAdConfirmation = true
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
        handler.postDelayed(confirmAdRunnable, AD_CONFIRM_WINDOW_MS)
        Log.d("Swapifi", "🟠 Ventana de confirmación abierta (${AD_CONFIRM_WINDOW_MS}ms) | B: $spotifyMusicVolume")
    }

    // Falsa alarma o abandono: cierra la ventana y devuelve a Spotify su
    // volumen. No-op si no había ventana abierta.
    private fun cancelPendingMute() {
        if (!pendingAdConfirmation) return
        handler.removeCallbacks(confirmAdRunnable)
        pendingAdConfirmation = false
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, spotifyMusicVolume, 0)
        Log.d("Swapifi", "🟠 Ventana cerrada — volumen de Spotify restaurado: $spotifyMusicVolume")
    }

    private fun mute() {
        isMuting = true
        isMuted = true
        userAlarmVolumeWhileLocal = null
        swapifi.app.state.PlayerState.isSpotifyPlaying.value = false

        val maxMusic = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val maxAlarm = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val relativeVolume = spotifyMusicVolume.toFloat() / maxMusic.toFloat()

        logBtVolumeDiagnostics("antes de mutear")
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
        handler.removeCallbacks(alarmReassertRunnable)
        alarmIndexSetOnMute = (relativeVolume * maxAlarm).roundToInt()
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, alarmIndexSetOnMute, 0)
        lastOwnAlarmWriteAt = android.os.SystemClock.elapsedRealtime()
        Log.d("Swapifi", "🔴 Muteando | B: $spotifyMusicVolume | Alarma ajustada: $alarmIndexSetOnMute")
        logBtVolumeDiagnostics("tras mutear")

        if (swapifi.app.state.PlayerState.playlist.isEmpty()) {
            val songs = loadSongsFromFolder()
            if (songs.isNotEmpty()) {
                val randomIndex = songs.indices.random()
                swapifi.app.state.PlayerState.setPlaylist(songs, randomIndex)
            }
        }

        if (swapifi.app.state.PlayerState.playlist.isNotEmpty()) {
            // Con una sola canción el rango queda vacío y .random() lanzaría
            // NoSuchElementException, matando el servicio (y con él todo el
            // silenciado de anuncios hasta reabrir la app).
            val candidates = swapifi.app.state.PlayerState.playlist.indices - swapifi.app.state.PlayerState.currentIndex
            val newIndex = if (candidates.isNotEmpty()) candidates.random() else swapifi.app.state.PlayerState.currentIndex
            swapifi.app.state.PlayerState.currentIndex = newIndex
            swapifi.app.state.PlayerState.playCurrentWithVolume(relativeVolume)
            Log.d("Swapifi", "🎵 Reproduciendo playlist usuario | B: $spotifyMusicVolume")
        } else {
            val songs = listOf(
                swapifi.app.R.raw.halfway_in,
                swapifi.app.R.raw.never_coming_down,
                swapifi.app.R.raw.two_things
            )
            val randomSong = songs.random()
            val uri = "android.resource://${packageName}/$randomSong".toUri()
            localPlayer.play(uri, relativeVolume)
            Log.d("Swapifi", "🎵 Reproduciendo fallback: $randomSong")
            // La rama de playlist ya actualiza vía onPlayRequestedWithVolume;
            // el fallback llama a localPlayer directamente y necesita esto.
            updateMediaNotification()
        }

        isMuting = false
    }

    private fun unmute() {
        if (!isMuted) return
        isMuted = false
        localPlayer.stop()
        swapifi.app.state.PlayerState.isPlayingLocal.value = false

        restoreSpotifyVolumes()
        swapifi.app.state.PlayerState.isSpotifyPlaying.value = true
        updateMediaNotification()

    }

    // Diagnóstico del bug "música local atenuada en BT": deja en el log los
    // índices visibles, los persistidos por dispositivo BT y las curvas en dB,
    // para ver si el 0 de STREAM_MUSIC arrastra el volumen absoluto A2DP.
    private fun logBtVolumeDiagnostics(momento: String) {
        try {
            val musicIdx = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val alarmIdx = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            val btMusic = try {
                Settings.System.getInt(contentResolver, "volume_music_bt_a2dp")
            } catch (e: Exception) {
                null
            }
            val btAlarm = try {
                Settings.System.getInt(contentResolver, "volume_alarm_bt_a2dp")
            } catch (e: Exception) {
                null
            }
            var curvas = ""
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                curvas = try {
                    val btDev = android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                    val musicDb = audioManager.getStreamVolumeDb(AudioManager.STREAM_MUSIC, musicIdx, btDev)
                    val alarmDb = audioManager.getStreamVolumeDb(
                        AudioManager.STREAM_ALARM, btAlarm ?: alarmIdx, btDev
                    )
                    " | curva BT: música=${"%.1f".format(musicDb)}dB alarma=${"%.1f".format(alarmDb)}dB"
                } catch (e: Exception) {
                    " | curva BT no disponible: ${e.message}"
                }
            }
            Log.d(
                "Swapifi",
                "🔬 [$momento] música=$musicIdx alarma=$alarmIdx | " +
                    "BT persistido: música=${btMusic ?: "—"} alarma=${btAlarm ?: "—"}$curvas"
            )
        } catch (e: Exception) {
            Log.w("Swapifi", "⚠ Diagnóstico de volúmenes falló: ${e.message}")
        }
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
        lastOwnAlarmWriteAt = android.os.SystemClock.elapsedRealtime()
        userAlarmVolumeWhileLocal = null

        handler.removeCallbacks(alarmReassertRunnable)
        handler.postDelayed(alarmReassertRunnable, ALARM_REASSERT_DELAY_MS)

        Log.d("Swapifi", "🟢 Restaurando | Música: $newMusicVolume | Alarma: $originalAlarmVolume")
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
            "Swapifi",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Swapifi")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
    }

    // En Android 13+ el sistema ignora los Action de la notificación y pinta los
    // botones a partir de las acciones declaradas en el PlaybackState de la
    // sesión, así que ambos caminos (callback de sesión e intents de acción)
    // tienen que existir y acabar en las mismas funciones de PlayerState.
    private fun createMediaSession() {
        mediaSession = MediaSession(this, "Swapifi")
        mediaSession.setCallback(object : MediaSession.Callback() {
            override fun onPlay() {
                swapifi.app.state.PlayerState.resume()
            }

            override fun onPause() {
                swapifi.app.state.PlayerState.pause()
            }

            override fun onSkipToNext() {
                swapifi.app.state.PlayerState.next()
            }

            override fun onSkipToPrevious() {
                swapifi.app.state.PlayerState.previous()
            }

            override fun onSeekTo(pos: Long) {
                localPlayer.seekTo(pos)
                updateMediaNotification()
            }
        })
    }

    // Punto único de refresco: con música local cargada muestra el controlador
    // multimedia (título, progreso, ⏮ ⏯ ⏭); sin ella vuelve a la notificación
    // básica y desactiva la sesión para que el sistema retire los controles.
    private fun updateMediaNotification() {
        val manager = getSystemService(NotificationManager::class.java)

        if (!localPlayer.hasMedia()) {
            mediaSession.isActive = false
            mediaSession.setPlaybackState(
                PlaybackState.Builder()
                    .setState(PlaybackState.STATE_STOPPED, 0L, 0f)
                    .build()
            )
            manager.notify(NOTIFICATION_ID, buildNotification(getString(swapifi.app.R.string.notification_active)))
            return
        }

        val playing = localPlayer.isActive()
        // La reproducción de fallback (raw) no pasa por PlayerState y deja el
        // nombre vacío; mostramos el nombre de la app en su lugar.
        val title = swapifi.app.state.PlayerState.currentSongName.value
            .ifEmpty { getString(swapifi.app.R.string.app_name) }

        mediaSession.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, getString(swapifi.app.R.string.notification_local_playing))
                .putLong(MediaMetadata.METADATA_KEY_DURATION, localPlayer.durationMs())
                .build()
        )
        mediaSession.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or
                        PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_SEEK_TO or
                        PlaybackState.ACTION_SKIP_TO_NEXT or
                        PlaybackState.ACTION_SKIP_TO_PREVIOUS
                )
                .setState(
                    if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    localPlayer.positionMs(),
                    if (playing) 1f else 0f
                )
                .build()
        )
        mediaSession.isActive = true

        manager.notify(NOTIFICATION_ID, buildMediaNotification(title, playing))
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, AudioService::class.java).setAction(action)
        return PendingIntent.getService(this, requestCode, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun mediaAction(iconRes: Int, titleRes: Int, action: String, requestCode: Int): Notification.Action {
        return Notification.Action.Builder(
            Icon.createWithResource(this, iconRes),
            getString(titleRes),
            servicePendingIntent(action, requestCode)
        ).build()
    }

    private fun buildMediaNotification(title: String, playing: Boolean): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, swapifi.app.MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val playPause = if (playing) {
            mediaAction(android.R.drawable.ic_media_pause, swapifi.app.R.string.player_pause, ACTION_PAUSE, 2)
        } else {
            mediaAction(android.R.drawable.ic_media_play, swapifi.app.R.string.player_play, ACTION_PLAY, 1)
        }

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(getString(swapifi.app.R.string.notification_local_playing))
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(contentIntent)
            .setOnlyAlertOnce(true)
            .addAction(mediaAction(android.R.drawable.ic_media_previous, swapifi.app.R.string.player_previous, ACTION_PREVIOUS, 3))
            .addAction(playPause)
            .addAction(mediaAction(android.R.drawable.ic_media_next, swapifi.app.R.string.player_next, ACTION_NEXT, 4))
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()
    }
}