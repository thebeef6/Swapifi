package swapify.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import swapify.app.R
import swapify.app.ui.theme.SwapifyRed
import swapify.app.ui.theme.SwapifyRedBright
import kotlin.math.roundToInt

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
    val controlsEnabled = !swapify.app.state.PlayerState.isSpotifyPlaying.value

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
            .padding(horizontal = 20.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Chip de estado con punto de color: verde Spotify / rojo Swapify
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isPlayingLocal) SwapifyRedBright else Color(0xFF1DB954))
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(18.dp))

        Text(
            text = songTitle,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (songArtist.isNotEmpty()) {
            Text(
                text = songArtist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(18.dp))

        SwapifySeekBar(
            position = position,
            duration = duration,
            enabled = controlsEnabled && duration > 0,
            onSeek = { player?.seekTo(it) },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(10.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onPrevious,
                enabled = controlsEnabled,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    Icons.Default.SkipPrevious,
                    contentDescription = stringResource(R.string.player_previous),
                    modifier = Modifier.size(32.dp)
                )
            }
            FilledIconButton(
                onClick = onPlayPause,
                enabled = controlsEnabled,
                modifier = Modifier.size(64.dp)
            ) {
                Icon(
                    if (isPlayingLocal) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = stringResource(
                        if (isPlayingLocal) R.string.player_pause else R.string.player_play
                    ),
                    modifier = Modifier.size(34.dp)
                )
            }
            IconButton(
                onClick = onNext,
                enabled = controlsEnabled,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    Icons.Default.SkipNext,
                    contentDescription = stringResource(R.string.player_next),
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

// Barra de progreso propia: pista redondeada, relleno con degradado rojo y
// pulgar deslizable. A diferencia del Slider anterior, permite hacer seek
// arrastrando o tocando la pista.
@Composable
private fun SwapifySeekBar(
    position: Long,
    duration: Long,
    enabled: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    // Mientras se arrastra, la barra y el tiempo siguen al dedo en vez de a la
    // reproducción; al soltar se lanza el seek real.
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val playedFraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val fraction = dragFraction ?: playedFraction
    val shownPosition = if (duration > 0) (fraction * duration).toLong() else 0L

    Column(modifier) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .pointerInput(enabled, duration) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { offset ->
                        onSeek(((offset.x / size.width).coerceIn(0f, 1f) * duration).toLong())
                    }
                }
                .pointerInput(enabled, duration) {
                    if (!enabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                        },
                        onHorizontalDrag = { change, _ ->
                            dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                            change.consume()
                        },
                        onDragEnd = {
                            dragFraction?.let { onSeek((it * duration).toLong()) }
                            dragFraction = null
                        },
                        onDragCancel = { dragFraction = null }
                    )
                }
        ) {
            val thumbSize = 16.dp
            val trackWidthPx = constraints.maxWidth.toFloat()
            val thumbPx = with(LocalDensity.current) { thumbSize.toPx() }

            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
            )
            if (fraction > 0f) {
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxWidth(fraction)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Brush.horizontalGradient(listOf(SwapifyRed, SwapifyRedBright)))
                )
            }
            if (enabled) {
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .offset { IntOffset(((trackWidthPx - thumbPx) * fraction).roundToInt(), 0) }
                        .size(thumbSize)
                        .clip(CircleShape)
                        .background(SwapifyRedBright)
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatTime(shownPosition),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatTime(duration),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
