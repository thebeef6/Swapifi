package dufy.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun PlayerSection(
    statusText: String = "Reproduciendo en Spotify",
    songTitle: String = "Nombre canción",
    songArtist: String = "Artista",
    isPlayingLocal: Boolean = false,
    onPlayPause: () -> Unit = {},
    onNext: () -> Unit = {},
    onPrevious: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = songTitle,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = songArtist,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        val player = dufy.app.state.PlayerState.localPlayerRef
        if (player != null) {
            val position = player.currentPosition.value
            val duration = player.duration.value
            if (duration > 0) {
                Slider(
                    value = position.toFloat(),
                    onValueChange = {},
                    valueRange = 0f..duration.toFloat(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPrevious) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "Anterior")
            }
            FilledIconButton(
                onClick = onPlayPause,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    if (isPlayingLocal) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlayingLocal) "Pausar" else "Play"
                )
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Default.SkipNext, contentDescription = "Siguiente")
            }
        }
    }
}