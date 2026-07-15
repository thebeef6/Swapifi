package swapifi.app.player

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import android.util.Log
import kotlin.math.pow
import kotlin.math.roundToInt

class LocalPlayer(private val context: Context) {

    private var player: ExoPlayer? = null
    private var deviceCallbackRegistered = false

    var onSongEnded: (() -> Unit)? = null
    // La duración real no se conoce hasta STATE_READY; la notificación multimedia
    // la necesita para mostrar la barra de progreso, así que avisamos entonces.
    var onReady: (() -> Unit)? = null
    val currentPosition = mutableLongStateOf(0L)
    val duration = mutableLongStateOf(0L)

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // Fracción de volumen local que ve la UI; refleja el índice de STREAM_ALARM
    // y se sincroniza con los botones físicos en progressRunnable.
    val localVolume = mutableFloatStateOf(getAlarmVolumeFraction())

    // Cómo se controla la ganancia audible de la música local:
    //
    // Los índices de stream en Android son POR DISPOSITIVO, y para el stream de
    // alarma get/setStreamVolume operan siempre sobre el altavoz mientras este
    // figure en el routing de sonificación — comprobado en MIUI escribiendo el
    // índice con la reproducción activa en A2DP: solo se movía el del altavoz.
    // El índice de alarma del dispositivo Bluetooth (el que de verdad atenúa la
    // música local en los auriculares) es inalcanzable para la app, así que ahí
    // no se lucha contra él: se LEE (persistido en Settings.System como
    // "volume_alarm_bt_a2dp"), se convierte a dB con getStreamVolumeDb() y se
    // COMPENSA digitalmente para igualar la sonoridad que tendría Spotify a la
    // fracción pedida: player.volume si hay que atenuar, LoudnessEnhancer (boost
    // en mB sobre la sesión de audio) si hay que amplificar.
    //
    // En el altavoz (sin dispositivo preferido) el índice sí lo controla la app
    // (AudioService lo escribe en mute()) y el sistema ya atenúa con él, así que
    // player.volume queda en 1.0 y no se duplica la atenuación.
    private var desiredFraction = 1f
    private var currentOutputType: Int? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    // Último índice de STREAM_ALARM visto (escrito por la app o adoptado de los
    // botones físicos). Sirve para detectar en progressRunnable un cambio hecho
    // con los botones y reaplicar la ganancia acorde.
    private var lastAlarmIndex = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)

    // Último valor visto de "volume_alarm_bt_a2dp": si el usuario mueve el
    // slider de alarma con los auriculares puestos, el boost debe recalibrarse
    // en vivo (la atenuación del sistema cambia con ese índice).
    private var lastBtAlarmSetting: Int? = null

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

    // setPreferredAudioDevice está marcada @UnstableApi en Media3; se acepta
    // explícitamente porque es la única vía para fijar la salida del player.
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
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
        currentOutputType = preferred?.type
        applyGain(desiredFraction)
        Log.d("Swapifi", "🔈 Dispositivo de salida preferido: ${preferred?.type ?: "por defecto (altavoz)"}")
    }

    // dB que el sistema aplica a la música local: índice de alarma del dispositivo
    // BT (solo legible desde Settings.System; AudioManager devuelve el del
    // altavoz) pasado por la curva de volumen de alarma de ese dispositivo.
    // "volume_alarm_bt_a2dp" solo se persiste cuando el usuario ha tocado el
    // volumen de alarma con el BT conectado; si falta, se estima con el índice
    // de alarma visible — compensar con una estimación gana a no compensar.
    private fun btAlarmAttenuationDb(): Float? {
        if (android.os.Build.VERSION.SDK_INT < 28) {
            Log.w("Swapifi", "⚠ API < 28: sin getStreamVolumeDb, no se puede medir la atenuación BT")
            return null
        }
        val persistedIdx = try {
            Settings.System.getInt(context.contentResolver, "volume_alarm_bt_a2dp")
        } catch (e: Settings.SettingNotFoundException) {
            null
        }
        // El stream de alarma no baja de getStreamMinVolume (2 en MIUI); con el
        // slider al mínimo el índice persistido queda fuera de rango y
        // getStreamVolumeDb lanza "Invalid stream volume index".
        val idx = (persistedIdx ?: audioManager.getStreamVolume(AudioManager.STREAM_ALARM))
            .coerceIn(
                audioManager.getStreamMinVolume(AudioManager.STREAM_ALARM),
                audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            )
        val db = try {
            audioManager.getStreamVolumeDb(AudioManager.STREAM_ALARM, idx, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
        } catch (e: Exception) {
            Log.w("Swapifi", "⚠ getStreamVolumeDb(ALARM, $idx, BT_A2DP) falló: ${e.message}")
            null
        }
        Log.d(
            "Swapifi",
            "🔎 Alarma BT | volume_alarm_bt_a2dp=${persistedIdx ?: "ausente (estimado)"} | índice usado=$idx | atenuación=${db}dB"
        )
        return db
    }

    // dB al que sonaría Spotify (stream de música) en el BT a esta fracción:
    // es la sonoridad que la música local debe igualar.
    private fun desiredMusicDb(fraction: Float): Float? {
        if (android.os.Build.VERSION.SDK_INT < 28) return null
        val maxMusic = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (maxMusic <= 0) return null
        val idx = (fraction * maxMusic).roundToInt().coerceIn(1, maxMusic)
        return try {
            audioManager.getStreamVolumeDb(AudioManager.STREAM_MUSIC, idx, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
        } catch (e: Exception) {
            Log.w("Swapifi", "⚠ getStreamVolumeDb(MUSIC, $idx, BT_A2DP) falló: ${e.message}")
            null
        }
    }

    // En algunos MIUI con efectos globales (Dolby, etc.) la creación del efecto
    // falla de forma transitoria; sin él no hay boost posible en BT, así que se
    // reintenta una vez con margen antes de rendirse.
    private fun createLoudnessEnhancer(audioSessionId: Int, allowRetry: Boolean) {
        loudnessEnhancer?.release()
        loudnessEnhancer = try {
            LoudnessEnhancer(audioSessionId)
        } catch (e: Exception) {
            Log.w(
                "Swapifi",
                "⚠ No se pudo crear LoudnessEnhancer: ${e.message}" +
                    if (allowRetry) " — reintentando en 500 ms" else " — sin boost en esta sesión"
            )
            if (allowRetry) {
                handler.postDelayed({
                    if (player != null) createLoudnessEnhancer(audioSessionId, allowRetry = false)
                }, 500)
            }
            null
        }
        applyGain(desiredFraction)
    }

    private fun setEnhancerGainMb(mB: Int) {
        val le = loudnessEnhancer ?: return
        try {
            if (mB > 0) {
                le.setTargetGain(mB)
                le.enabled = true
            } else {
                le.setTargetGain(0)
                le.enabled = false
            }
        } catch (e: Exception) {
            Log.d("Swapifi", "⚠ LoudnessEnhancer falló: ${e.message}")
        }
    }

    private fun applyGain(fraction: Float) {
        val p = player ?: return
        desiredFraction = fraction
        if (fraction <= 0f) {
            setEnhancerGainMb(0)
            p.volume = 0f
            return
        }
        if (currentOutputType == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) {
            val attDb = btAlarmAttenuationDb()
            val desiredDb = desiredMusicDb(fraction)
            if (attDb != null && desiredDb != null) {
                val deltaDb = desiredDb - attDb
                var boostMb = 0
                if (deltaDb <= 0f) {
                    setEnhancerGainMb(0)
                    p.volume = 10f.pow(deltaDb / 20f).coerceIn(0f, 1f)
                } else {
                    // Amplificar por encima de la atenuación fija del sistema:
                    // solo el LoudnessEnhancer puede ganar >1.0. Tope de +36 dB;
                    // su limitador comprime la dinámica con boosts así, pero
                    // audible-y-comprimido gana a inaudible (aquí el sistema
                    // mete −39 dB que no podemos quitar de otra forma).
                    p.volume = 1f
                    val wantedMb = (deltaDb * 100).roundToInt()
                    boostMb = wantedMb.coerceAtMost(3600)
                    if (wantedMb > boostMb) {
                        Log.w("Swapifi", "⚠ Boost capado a +36dB: faltan ${(wantedMb - boostMb) / 100f}dB para igualar a Spotify")
                    }
                    setEnhancerGainMb(boostMb)
                }
                Log.d(
                    "Swapifi",
                    "🎚 Ganancia BT | objetivo=${"%.1f".format(desiredDb)}dB | sistema=${"%.1f".format(attDb)}dB | " +
                        "delta=${"%.1f".format(deltaDb)}dB | player.volume=${p.volume} | boost=${boostMb}mB"
                )
                return
            }
            // Sin curvas (API < 28 o getStreamVolumeDb roto). El índice de alarma
            // que escribe AudioService solo mueve el del altavoz, así que en BT
            // la rama "volume=1, sin boost" dejaba la atenuación del sistema
            // entera Y sin control de volumen. Boost fijo moderado + fracción
            // digital: no iguala a Spotify pero evita el caso peor.
            Log.w("Swapifi", "⚠ Sin datos de curva BT (att=$attDb, obj=$desiredDb) — boost fijo de emergencia +12dB")
            p.volume = fraction
            setEnhancerGainMb(1200)
            return
        }
        // Altavoz (o sin datos de compensación): el índice de STREAM_ALARM que
        // escribió AudioService ya atenúa; no duplicar con player.volume.
        setEnhancerGainMb(0)
        p.volume = 1f
    }

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            player?.let {
                currentPosition.value = it.currentPosition
                duration.value = it.duration.coerceAtLeast(0)
                // Si los botones físicos mueven STREAM_ALARM (en BT ese índice es
                // el del altavoz — audiblemente inerte, pero sirve de superficie
                // de control), adoptamos la nueva fracción y reaplicamos ganancia.
                val alarmIndex = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
                if (alarmIndex != lastAlarmIndex) {
                    lastAlarmIndex = alarmIndex
                    val streamFraction = getAlarmVolumeFraction()
                    localVolume.value = streamFraction
                    applyGain(streamFraction)
                }
                // El índice de alarma del dispositivo BT puede cambiar sin tocar
                // el del altavoz (slider de alarma con auriculares puestos);
                // la atenuación del sistema cambia con él → recalibrar boost.
                if (currentOutputType == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) {
                    val btIdx = try {
                        Settings.System.getInt(context.contentResolver, "volume_alarm_bt_a2dp")
                    } catch (e: Exception) {
                        null
                    }
                    if (btIdx != null && btIdx != lastBtAlarmSetting) {
                        lastBtAlarmSetting = btIdx
                        applyGain(desiredFraction)
                    }
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
            val fraction = volume.coerceIn(0f, 1f)
            desiredFraction = fraction
            localVolume.value = fraction
            // Partimos del índice actual para que el primer tick del bucle no
            // pise el volumen inicial.
            lastAlarmIndex = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            it.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) {
                        onReady?.invoke()
                    }
                    if (state == Player.STATE_ENDED) {
                        onSongEnded?.invoke()
                    }
                }

                // El LoudnessEnhancer se ata a la sesión de audio, que ExoPlayer
                // asigna al inicializar el sink; hasta entonces no existe y el
                // boost no puede aplicarse — por eso se (re)crea aquí.
                override fun onAudioSessionIdChanged(audioSessionId: Int) {
                    createLoudnessEnhancer(audioSessionId, allowRetry = true)
                }
            })
            it.setMediaItem(MediaItem.fromUri(uri))
            it.prepare()
            it.play()
            Log.d("Swapifi", "🎵 Reproduciendo audio local: $uri")
            Log.d("Swapifi", "🔊 Fracción pedida: $volume | STREAM_ALARM actual: ${audioManager.getStreamVolume(AudioManager.STREAM_ALARM)}")
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
        loudnessEnhancer?.release()
        loudnessEnhancer = null
        player?.stop()
        player?.release()
        player = null
        currentPosition.value = 0L
        duration.value = 0L
        Log.d("Swapifi", "⏹ Audio local detenido")
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
