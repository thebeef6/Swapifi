package dufy.app.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import android.util.Log

class LocalPlayer(private val context: Context) {

    private var player: ExoPlayer? = null

    fun play(uri: Uri) {
        stop()
        player = ExoPlayer.Builder(context).build().also {
            it.setMediaItem(MediaItem.fromUri(uri))
            it.prepare()
            it.play()
            Log.d("Dufy", "🎵 Reproduciendo audio local: $uri")
        }
    }

    fun stop() {
        player?.stop()
        player?.release()
        player = null
        Log.d("Dufy", "⏹ Audio local detenido")
    }

    fun isPlaying(): Boolean {
        return player?.isPlaying ?: false
    }
}