package swapify.app.state

import android.content.Context
import java.io.File

object PlayerState {
    var playlist: List<File> = emptyList()
    var currentIndex: Int = 0

    var isPlayingLocal = androidx.compose.runtime.mutableStateOf(false)
    var currentSongName = androidx.compose.runtime.mutableStateOf("")
    var permissionGranted = androidx.compose.runtime.mutableStateOf(false)
    var selectedFolder = androidx.compose.runtime.mutableStateOf("")

    var onPlayRequested: ((File) -> Unit)? = null
    var onPlayRequestedWithVolume: ((File, Float) -> Unit)? = null
    var onPauseRequested: (() -> Unit)? = null
    var localPosition = androidx.compose.runtime.mutableStateOf(0L)
    var localDuration = androidx.compose.runtime.mutableStateOf(0L)
    var localPlayerRef: swapify.app.player.LocalPlayer? = null
    var isSpotifyPlaying = androidx.compose.runtime.mutableStateOf(false)
    fun setPlaylist(files: List<File>, startIndex: Int = 0) {
        playlist = files
        currentIndex = startIndex
    }

    fun setSelectedFolder(context: Context, folderPath: String) {
        selectedFolder.value = folderPath
        val prefs = context.getSharedPreferences("Swapify_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("selected_folder", folderPath).apply()
    }

    fun loadSelectedFolder(context: Context) {
        val prefs = context.getSharedPreferences("Swapify_prefs", Context.MODE_PRIVATE)
        selectedFolder.value = prefs.getString("selected_folder", "") ?: ""
    }

    fun playCurrent() {
        if (playlist.isNotEmpty()) {
            val file = playlist[currentIndex]
            currentSongName.value = file.nameWithoutExtension
            onPlayRequested?.invoke(file)
            isPlayingLocal.value = true
        }
    }

    fun playCurrentWithVolume(volume: Float) {
        if (playlist.isNotEmpty()) {
            val file = playlist[currentIndex]
            currentSongName.value = file.nameWithoutExtension
            onPlayRequestedWithVolume?.invoke(file, volume)
            isPlayingLocal.value = true
        }
    }

    fun pause() {
        onPauseRequested?.invoke()
        isPlayingLocal.value = false
    }

    fun next() {
        if (playlist.isNotEmpty()) {
            currentIndex = (currentIndex + 1) % playlist.size
            playCurrent()
        }
    }

    fun previous() {
        if (playlist.isNotEmpty()) {
            currentIndex = if (currentIndex - 1 < 0) playlist.size - 1 else currentIndex - 1
            playCurrent()
        }
    }
}