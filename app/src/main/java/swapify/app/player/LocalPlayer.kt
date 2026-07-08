package swapify.app.player

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import android.util.Log

class LocalPlayer(private val context: Context) {

    private var player: ExoPlayer? = null
    private var deviceCallbackRegistered = false

    var onSongEnded: (() -> Unit)? = null
    // La duración real no se conoce hasta STATE_READY; la notificación multimedia
    // la necesita para mostrar la barra de progreso, así que avisamos entonces.
    var onReady: (() -> Unit)? = null
    val currentPosition = mutableStateOf(0L)
    val duration = mutableStateOf(0L)

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // Con Bluetooth (AVRCP absolute volume) o algunos auriculares por cable,
    // cambiar el índice de STREAM_ALARM no llega a mover la ganancia real del
    // dispositivo conectado: ese mapeo de volumen absoluto solo está atado a
    // STREAM_MUSIC en Android. Por eso la ganancia audible real la controla
    // el propio ExoPlayer (player.volume), que atenúa la muestra de audio
    // antes de enviarla a cualquier salida. Este estado guarda esa ganancia
    // y se sincroniza con los botones físicos en progressRunnable.
    val localVolume = mutableStateOf(getAlarmVolumeFraction())

    // Último índice de STREAM_ALARM escrito por la app (o adoptado de los botones
    // físicos). Sirve para distinguir en progressRunnable un cambio externo real
    // (botones) de los ajustes que hace la propia app (mute() en AudioService):
    // con BT de volumen absoluto el sistema puede recortar o ignorar
    // setStreamVolume, y si comparáramos fracciones el bucle de sincronización
    // pisaría player.volume con valores que no ha puesto el usuario.
    private var lastAlarmIndex = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)

    private fun getAlarmVolumeFraction(): Float {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        if (max <= 0) return 0f
        return audioManager.getStreamVolume(AudioManager.STREAM_ALARM).toFloat() / max
    }

    // USAGE_ALARM fuerza que Android duplique la salida al altavoz además del
    // dispositivo conectado (Bluetooth/auriculares). Fijamos el dispositivo
    // preferido explícitamente para evitar esa duplicación.
    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = updatePreferredOutputDevice()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = updatePreferredOutputDevice()
    }

    private fun updatePreferredOutputDevice() {
        // TYPE_BLUETOOTH_SCO es el perfil de llamadas (HFP), no de música: nunca
        // es una ruta válida para el ExoPlayer y se descarta explícitamente.
        // El orden aquí es de prioridad: A2DP gana si está presente.
        val priorityTypes = listOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES
        )
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val preferred = priorityTypes.firstNotNullOfOrNull { type -> devices.firstOrNull { it.type == type } }
        player?.setPreferredAudioDevice(preferred)
        Log.d("Swapify", "🔈 Dispositivo de salida preferido: ${preferred?.type ?: "por defecto (altavoz)"}")
    }

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            player?.let {
                currentPosition.value = it.currentPosition
                duration.value = it.duration.coerceAtLeast(0)
                // Si los botones físicos mueven STREAM_ALARM, reflejamos ese cambio
                // también en la ganancia real del reproductor. Comparamos índices
                // (no fracciones) contra lo último que escribió la app para no
                // revertir el slider cuando el sistema ignora nuestros ajustes.
                val alarmIndex = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
                if (alarmIndex != lastAlarmIndex) {
                    lastAlarmIndex = alarmIndex
                    val streamFraction = getAlarmVolumeFraction()
                    localVolume.value = streamFraction
                    it.volume = streamFraction
                }
            }
            handler.postDelayed(this, 500)
        }
    }

    fun play(uri: Uri, volume: Float) {
        stop()
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_ALARM)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        player = ExoPlayer.Builder(context).build().also {
            it.setAudioAttributes(audioAttributes, false)
            it.volume = volume
            localVolume.value = volume.coerceIn(0f, 1f)
            // AudioService acaba de ajustar STREAM_ALARM en mute(); partimos de ese
            // índice para que el primer tick del bucle no pise el volumen inicial.
            lastAlarmIndex = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            it.setMediaItem(MediaItem.fromUri(uri))
            it.prepare()
            it.play()
            it.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) {
                        onReady?.invoke()
                    }
                    if (state == Player.STATE_ENDED) {
                        onSongEnded?.invoke()
                    }
                }
            })
            Log.d("Swapify", "🎵 Reproduciendo audio local: $uri")
            Log.d("Swapify", "🔊 Player volume: $volume | STREAM_ALARM actual: ${audioManager.getStreamVolume(AudioManager.STREAM_ALARM)}")
        }
        updatePreferredOutputDevice()
        if (!deviceCallbackRegistered) {
            audioManager.registerAudioDeviceCallback(audioDeviceCallback, handler)
            deviceCallbackRegistered = true
        }
        handler.post(progressRunnable)
    }

    fun pause() {
        player?.pause()
    }

    // Devuelve false si no hay nada pausado que reanudar (player liberado o
    // canción ya terminada); en ese caso el llamador debe arrancar de cero.
    fun resume(): Boolean {
        val p = player ?: return false
        if (p.playbackState == Player.STATE_IDLE || p.playbackState == Player.STATE_ENDED) return false
        p.play()
        return true
    }

    fun stop() {
        handler.removeCallbacks(progressRunnable)
        if (deviceCallbackRegistered) {
            audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
            deviceCallbackRegistered = false
        }
        player?.stop()
        player?.release()
        player = null
        currentPosition.value = 0L
        duration.value = 0L
        Log.d("Swapify", "⏹ Audio local detenido")
    }

    fun isPlaying(): Boolean {
        return player?.isPlaying ?: false
    }

    // Hay una canción cargada (sonando o en pausa); distingue el estado
    // "reproductor local en uso" del reposo, para la notificación multimedia.
    fun hasMedia(): Boolean = player != null

    fun positionMs(): Long = player?.currentPosition ?: 0L

    fun durationMs(): Long = player?.duration?.coerceAtLeast(0L) ?: 0L

    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs)
    }

    // isPlaying() devuelve false mientras ExoPlayer sigue en BUFFERING justo tras
    // empezar; si AudioService lo consultara en esa ventana al llegar un broadcast
    // de canción, desmutearía antes de tiempo y el anuncio sonaría con volumen.
    fun isActive(): Boolean {
        val p = player ?: return false
        return p.playWhenReady &&
            p.playbackState != Player.STATE_ENDED &&
            p.playbackState != Player.STATE_IDLE
    }
}