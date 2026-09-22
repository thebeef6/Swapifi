package swapifi.app.ui

import android.content.Context
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File
import swapifi.app.R
data class AudioFile(val file: File, val title: String)

fun getAudioFiles(context: Context, folder: String): List<AudioFile> {
    val songs = mutableListOf<AudioFile>()
    val projection = arrayOf(
        MediaStore.Audio.Media.DATA,
        MediaStore.Audio.Media.TITLE
    )
    val cursor = context.contentResolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        projection,
        "${MediaStore.Audio.Media.IS_MUSIC} != 0",
        null,
        MediaStore.Audio.Media.TITLE + " ASC"
    )
    cursor?.use {
        val pathCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
        val titleCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        while (it.moveToNext()) {
            val path = it.getString(pathCol)
            val title = it.getString(titleCol)
            if (folder.isEmpty() || path.substringBeforeLast("/") == folder) {
                songs.add(AudioFile(File(path), title))
            }
        }
    }
    return songs
}

@Composable
fun FileExplorerSection() {
    val context = LocalContext.current
    var songs by remember { mutableStateOf<List<AudioFile>>(emptyList()) }
    var showFolderPicker by remember { mutableStateOf(false) }
    val permissionGranted = swapifi.app.state.PlayerState.permissionGranted.value
    val selectedFolder = swapifi.app.state.PlayerState.selectedFolder.value
    val mediaScanTick = swapifi.app.state.PlayerState.mediaScanTick.intValue

    // mediaScanTick como key: la primera vez que se concede el permiso, el
    // escaneo de MediaStore (RawMusicExporter) termina en segundo plano y
    // puede llegar bastante después de permissionGranted; sin esta key la
    // lista se cargaría vacía hasta cerrar y reabrir la app.
    LaunchedEffect(permissionGranted, selectedFolder, mediaScanTick) {
        songs = getAudioFiles(context, selectedFolder)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showFolderPicker = true },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (selectedFolder.isEmpty())
                        stringResource(R.string.your_music)
                    else
                        selectedFolder.substringAfterLast("/"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = stringResource(R.string.settings_change_folder)
                )
            }
        }

        val selectionEnabled = !swapifi.app.state.PlayerState.isSpotifyPlaying.value

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(songs) { song ->
                val isCurrent = swapifi.app.state.PlayerState.isPlayingLocal.value &&
                    swapifi.app.state.PlayerState.playlist
                        .getOrNull(swapifi.app.state.PlayerState.currentIndex) == song.file
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (isCurrent)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)
                        )
                        .clickable(enabled = selectionEnabled) {
                            val playlist = songs.map { it.file }
                            val startIndex = songs.indexOf(song)
                            swapifi.app.state.PlayerState.setPlaylist(playlist, startIndex)
                            swapifi.app.state.PlayerState.playCurrentWithVolume(
                                swapifi.app.state.PlayerState.localPlayerRef?.localVolume?.value ?: 1f
                            )
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .alpha(if (selectionEnabled) 1f else 0.4f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(
                                    alpha = if (isCurrent) 0.25f else 0.12f
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = song.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isCurrent)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }

    if (showFolderPicker) {
        FolderPickerPopup(
            onDismiss = { showFolderPicker = false },
            onFolderSelected = { folder ->
                swapifi.app.state.PlayerState.setSelectedFolder(context, folder)
                showFolderPicker = false
            }
        )
    }
}