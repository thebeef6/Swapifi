package dufy.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dufy.app.services.AudioService
import dufy.app.ui.DufyTopBar
import dufy.app.ui.FileExplorerSection
import dufy.app.ui.PlayerSection
import dufy.app.ui.theme.DufyTheme
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
        // Pedir permiso de lectura de audio
        val permission = if (android.os.Build.VERSION.SDK_INT >= 33) {
            android.Manifest.permission.READ_MEDIA_AUDIO
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (androidx.core.content.ContextCompat.checkSelfPermission(this, permission)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            androidx.core.app.ActivityCompat.requestPermissions(this, arrayOf(permission), 100)
        }
        setContent {
            DufyTheme {
                DufyScreen()
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
            dufy.app.state.PlayerState.permissionGranted.value = true
        }
    }
}
@Composable
fun DufyScreen() {
    var showDonatePopup by remember { mutableStateOf(false) }
    var showHelpPopup by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(Unit) {
        dufy.app.state.PlayerState.loadSelectedFolder(context)
    }
    // Mostrar ayuda automáticamente la primera vez
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("dufy_prefs", android.content.Context.MODE_PRIVATE)
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
            DufyTopBar(
                onDonateClick = { showDonatePopup = true },
                onHelpClick = { showHelpPopup = true }
            )
        },
        bottomBar = {
            Button(
                onClick = { showDonatePopup = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(16.dp)
            ) {
                Text("♡ Donar")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            PlayerSection(
                statusText = "Reproduciendo en local",
                songTitle = dufy.app.state.PlayerState.currentSongName.value,
                songArtist = "",
                isPlayingLocal = dufy.app.state.PlayerState.isPlayingLocal.value,
                onPlayPause = {
                    if (dufy.app.state.PlayerState.isPlayingLocal.value) {
                        dufy.app.state.PlayerState.pause()
                    } else {
                        dufy.app.state.PlayerState.playCurrent()
                    }
                },
                onNext = { dufy.app.state.PlayerState.next() },
                onPrevious = { dufy.app.state.PlayerState.previous() }
            )
            FileExplorerSection()
        }
    }

    if (showDonatePopup) {
        dufy.app.ui.DonatePopup(
            onDismiss = { showDonatePopup = false },
            onAmountSelected = { amount: Int ->
                showDonatePopup = false
            }
        )
    }

    if (showHelpPopup) {
        dufy.app.ui.HelpPopup(
            onDismiss = { showHelpPopup = false }
        )
    }
}