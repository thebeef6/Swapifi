package dufy.app.ui

import android.content.Context
import android.provider.MediaStore
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.io.File

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
    val permissionGranted = dufy.app.state.PlayerState.permissionGranted.value
    val selectedFolder = dufy.app.state.PlayerState.selectedFolder.value

    LaunchedEffect(permissionGranted, selectedFolder) {
        songs = getAudioFiles(context, selectedFolder)
        android.util.Log.d("Dufy", "Canciones encontradas: ${songs.size}")
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (selectedFolder.isEmpty()) "Tu música" else selectedFolder.substringAfterLast("/"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        LazyColumn {
            items(songs) { song ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val playlist = songs.map { it.file }
                            val startIndex = songs.indexOf(song)
                            dufy.app.state.PlayerState.setPlaylist(playlist, startIndex)
                            dufy.app.state.PlayerState.playCurrent()
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.MusicNote, contentDescription = null)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = song.title)
                }
            }
        }
    }
}