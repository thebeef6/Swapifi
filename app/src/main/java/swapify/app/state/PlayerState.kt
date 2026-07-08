package swapify.app.state

import android.content.Context
import androidx.core.content.edit
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
    var onResumeRequested: (() -> Boolean)? = null
    var localPosition = androidx.compose.runtime.mutableLongStateOf(0L)
    var localDuration = androidx.compose.runtime.mutableLongStateOf(0L)
    var localPlayerRef: swapify.app.player.LocalPlayer? = null
    var isSpotifyPlaying = androidx.compose.runtime.mutableStateOf(false)
    fun setPlaylist(files: List<File>, startIndex: Int = 0) {
        playlist = files
        currentIndex = startIndex
    }

    fun setSelectedFolder(context: Context, folderPath: String) {
        selectedFolder.value = folderPath
        val prefs = context.getSharedPreferences("Swapify_prefs", Context.MODE_PRIVATE)
        prefs.edit { putString("selected_folder", folderPath) }
    }

    fun loadSelectedFolder(context: Context) {
        val prefs = context.getSharedPreferences("Swapify_prefs", Context.MODE_PRIVATE)
        // Primera ejecución (sin carpeta guardada): se abre directamente en la
        // carpeta Music/Swapify con las canciones que exporta la app, para que
        // el usuario vea contenido desde el primer momento. Puede cambiarla
        // después; su elección queda guardada y aquí se respeta.
        val defaultFolder = java.io.File(
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC),
            "Swapify"
        ).absolutePath
        selectedFolder.value = prefs.getString("selected_folder", defaultFolder) ?: defaultFolder
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

    // Reanuda la canción pausada; si no había nada pausado, arranca la actual.
    fun resume() {
        if (onResumeRequested?.invoke() == true) {
            isPlayingLocal.value = true
        } else {
            playCurrent()
        }
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