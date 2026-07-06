package swapify.app.player

import android.content.Context
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

    var onSongEnded: (() -> Unit)? = null
    val currentPosition = mutableStateOf(0L)
    val duration = mutableStateOf(0L)

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            player?.let {
                currentPosition.value = it.currentPosition
                duration.value = it.duration.coerceAtLeast(0)
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
            it.setMediaItem(MediaItem.fromUri(uri))
            it.prepare()
            it.play()
            it.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED) {
                        onSongEnded?.invoke()
                    }
                }
            })
            Log.d("Swapify", "🎵 Reproduciendo audio local: $uri")
            Log.d("Swapify", "🔊 Player volume: $volume | STREAM_ALARM actual: ${(context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager).getStreamVolume(android.media.AudioManager.STREAM_ALARM)}")
        }
        handler.post(progressRunnable)
    }

    fun stop() {
        handler.removeCallbacks(progressRunnable)
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
}