package swapifi.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.draw.clip
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.net.toUri
import swapifi.app.ui.theme.SwapifiRed
import swapifi.app.ui.theme.SwapifiRedBright
import swapifi.app.ui.theme.SwapifiRedDeep
import swapifi.app.services.AudioService
import swapifi.app.ui.SwapifiTopBar
import swapifi.app.ui.FileExplorerSection
import swapifi.app.ui.PlayerSection
import swapifi.app.ui.theme.SwapifiTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding

class MainActivity : ComponentActivity() {

    private val audioPermission = if (android.os.Build.VERSION.SDK_INT >= 33) {
        android.Manifest.permission.READ_MEDIA_AUDIO
    } else {
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    }

    private val permissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results[audioPermission] == true) {
            swapifi.app.state.PlayerState.permissionGranted.value = true
            swapifi.app.utils.RawMusicExporter.exportIfNeeded(this) {
                swapifi.app.state.PlayerState.notifyMediaScanned()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        startForegroundService(Intent(this, AudioService::class.java))
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        fun granted(permission: String) =
            androidx.core.content.ContextCompat.checkSelfPermission(this, permission) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED

        if (granted(audioPermission)) {
            swapifi.app.utils.RawMusicExporter.exportIfNeeded(this) {
                swapifi.app.state.PlayerState.notifyMediaScanned()
            }
        }

        // Además del permiso de audio, desde Android 13 las notificaciones (la
        // del servicio y la multimedia) requieren su propio permiso de ejecución.
        val missing = mutableListOf<String>()
        if (!granted(audioPermission)) missing += audioPermission
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            !granted(android.Manifest.permission.POST_NOTIFICATIONS)
        ) {
            missing += android.Manifest.permission.POST_NOTIFICATIONS
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }

        setContent {
            SwapifiTheme {
                SwapifiScreen()
            }
        }
    }
}

@Composable
fun SwapifiScreen() {
    var showSetupHelpPopup by remember { mutableStateOf(false) }
    var showHowItWorksPopup by remember { mutableStateOf(false) }
    var showHandsOffHint by remember { mutableStateOf(true) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("Swapifi_prefs", android.content.Context.MODE_PRIVATE)
    }
    val openKofi = {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, "https://ko-fi.com/davidig6".toUri())
        )
    }

    LaunchedEffect(Unit) {
        swapifi.app.state.PlayerState.loadSelectedFolder(context)
    }

    LaunchedEffect(Unit) {
        val firstLaunch = prefs.getBoolean("first_launch", true)
        if (firstLaunch) {
            showHowItWorksPopup = true
            prefs.edit { putBoolean("first_launch", false) }
        }
        showHandsOffHint = !prefs.getBoolean("hands_off_hint_dismissed", false)
    }

    Scaffold(
        topBar = {
            SwapifiTopBar(
                onDonateClick = openKofi,
                onSetupHelpClick = { showSetupHelpPopup = true },
                onHowItWorksClick = { showHowItWorksPopup = true }
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
                                listOf(SwapifiRedDeep, SwapifiRed, SwapifiRedBright, SwapifiRed, SwapifiRedDeep)
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
                statusText = if (swapifi.app.state.PlayerState.isPlayingLocal.value)
                    stringResource(R.string.status_ad_detected)
                else
                    stringResource(R.string.status_playing_spotify),
                songTitle = swapifi.app.state.PlayerState.currentSongName.value,
                songArtist = "",
                isPlayingLocal = swapifi.app.state.PlayerState.isPlayingLocal.value,
                onPlayPause = {
                    if (swapifi.app.state.PlayerState.isPlayingLocal.value) {
                        swapifi.app.state.PlayerState.pause()
                    } else {
                        swapifi.app.state.PlayerState.resume()
                    }
                },
                onNext = { swapifi.app.state.PlayerState.next() },
                onPrevious = { swapifi.app.state.PlayerState.previous() }
            )
            if (showHandsOffHint) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Text(
                        text = stringResource(R.string.hands_off_hint),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.Medium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 28.dp, top = 12.dp, bottom = 12.dp)
                    )
                    IconButton(
                        onClick = {
                            showHandsOffHint = false
                            prefs.edit { putBoolean("hands_off_hint_dismissed", true) }
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(20.dp)
                            .padding(top = 4.dp, end = 4.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.dismiss),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
            FileExplorerSection()
        }
    }

    if (showHowItWorksPopup) {
        swapifi.app.ui.HowItWorksPopup(
            onDismiss = { showHowItWorksPopup = false }
        )
    }
    if (showSetupHelpPopup) {
        swapifi.app.ui.SetupHelpPopup(
            onDismiss = { showSetupHelpPopup = false }
        )
    }
}