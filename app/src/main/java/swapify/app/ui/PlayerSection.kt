package swapify.app.ui

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

fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

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
    val player = swapify.app.state.PlayerState.localPlayerRef
    val position = player?.currentPosition?.value ?: 0L
    val duration = player?.duration?.value ?: 0L
    val timeLeft = if (duration > 0) duration - position else 0L
    val controlsEnabled = !swapify.app.state.PlayerState.isSpotifyPlaying.value

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

        // Barra de progreso
        if (duration > 0) {
            Slider(
                value = position.toFloat(),
                onValueChange = {},
                valueRange = 0f..duration.toFloat(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            )
            // Tiempo actual y tiempo restante
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTime(position),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = formatTime(duration),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPrevious, enabled = controlsEnabled) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "Anterior")
            }
            FilledIconButton(
                onClick = onPlayPause,
                enabled = controlsEnabled,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    if (isPlayingLocal) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlayingLocal) "Pausar" else "Play"
                )
            }
            IconButton(onClick = onNext, enabled = controlsEnabled) {
                Icon(Icons.Default.SkipNext, contentDescription = "Siguiente")
            }
        }
    }
}