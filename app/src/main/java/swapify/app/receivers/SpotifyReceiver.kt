package swapify.app.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class SpotifyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "com.spotify.music.playbackstatechanged" -> {
                val playing = intent.getBooleanExtra("playing", false)
                val trackType = intent.getStringExtra("track_type")

                Log.d("Swapify", "Estado: playing=$playing, type=$trackType")

                if (trackType == "ad" && playing) {
                    Log.d("Swapify", "🔴 ANUNCIO DETECTADO")
                    // Aquí irá la lógica de silenciar Spotify y reproducir tu música
                } else if (trackType == "track" && playing) {
                    Log.d("Swapify", "🟢 MÚSICA NORMAL")
                    // Aquí irá la lógica de restaurar Spotify
                }
            }
        }
    }
}