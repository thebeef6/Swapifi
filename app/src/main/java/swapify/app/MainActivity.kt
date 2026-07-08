package swapify.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import swapify.app.ui.theme.SwapifyRed
import swapify.app.ui.theme.SwapifyRedBright
import swapify.app.ui.theme.SwapifyRedDeep
import swapify.app.services.AudioService
import swapify.app.ui.SwapifyTopBar
import swapify.app.ui.FileExplorerSection
import swapify.app.ui.PlayerSection
import swapify.app.ui.theme.SwapifyTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val serviceIntent = Intent(this, AudioService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val permission = if (android.os.Build.VERSION.SDK_INT >= 33) {
            android.Manifest.permission.READ_MEDIA_AUDIO
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (androidx.core.content.ContextCompat.checkSelfPermission(this, permission)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            androidx.core.app.ActivityCompat.requestPermissions(this, arrayOf(permission), 100)
        } else {
            swapify.app.utils.RawMusicExporter.exportIfNeeded(this)
        }

        setContent {
            SwapifyTheme {
                SwapifyScreen()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() &&
            grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            swapify.app.state.PlayerState.permissionGranted.value = true
            swapify.app.utils.RawMusicExporter.exportIfNeeded(this)
        }
    }
}

@Composable
fun SwapifyScreen() {
    var showHelpPopup by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val openKofi = {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://ko-fi.com/davidig6"))
        )
    }

    LaunchedEffect(Unit) {
        swapify.app.state.PlayerState.loadSelectedFolder(context)
    }

    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("Swapify_prefs", android.content.Context.MODE_PRIVATE)
        val firstLaunch = prefs.getBoolean("first_launch", true)
        if (firstLaunch) {
            showHelpPopup = true
            val editor = prefs.edit()
            editor.putBoolean("first_launch", false)
            editor.apply()
        }
    }

    Scaffold(
        topBar = {
            SwapifyTopBar(
                onDonateClick = openKofi,
                onHelpClick = { showHelpPopup = true }
            )
        },
        bottomBar = {
            // Contenedor transparente + Box interior con degradado: Button no
            // admite Brush como color de fondo, pero sí recorta su contenido
            // a la forma, así que el degradado hereda las esquinas redondeadas.
            Button(
                onClick = openKofi,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(),
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            // Simétrico: oscuro en los extremos, carmesí en el centro
                            Brush.horizontalGradient(
                                listOf(SwapifyRedDeep, SwapifyRed, SwapifyRedBright, SwapifyRed, SwapifyRedDeep)
                            )
                        )
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.donate_button),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            PlayerSection(
                statusText = if (swapify.app.state.PlayerState.isPlayingLocal.value)
                    stringResource(R.string.status_ad_detected)
                else
                    stringResource(R.string.status_playing_spotify),
                songTitle = swapify.app.state.PlayerState.currentSongName.value,
                songArtist = "",
                isPlayingLocal = swapify.app.state.PlayerState.isPlayingLocal.value,
                onPlayPause = {
                    if (swapify.app.state.PlayerState.isPlayingLocal.value) {
                        swapify.app.state.PlayerState.pause()
                    } else {
                        swapify.app.state.PlayerState.resume()
                    }
                },
                onNext = { swapify.app.state.PlayerState.next() },
                onPrevious = { swapify.app.state.PlayerState.previous() }
            )
            FileExplorerSection()
        }
    }

    if (showHelpPopup) {
        swapify.app.ui.HelpPopup(
            onDismiss = { showHelpPopup = false }
        )
    }
}