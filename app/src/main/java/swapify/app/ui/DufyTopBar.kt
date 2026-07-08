package swapify.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import swapify.app.R
import swapify.app.ui.theme.SwapifyRed
import swapify.app.ui.theme.SwapifyRedDeep
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwapifyTopBar(
    onDonateClick: () -> Unit = {},
    onHelpClick: () -> Unit = {}
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showBugReport by remember { mutableStateOf(false) }
    var showContact by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }

    // Easter egg: 6 toques seguidos sobre el icono muestran "Dfy"
    var iconTaps by remember { mutableStateOf(0) }
    var lastTapAt by remember { mutableStateOf(0L) }
    var showDfy by remember { mutableStateOf(false) }

    TopAppBar(
        navigationIcon = {
            // El foreground del icono adaptativo trae márgenes de zona segura;
            // se escala para que el logo se vea al tamaño esperado sin recortes.
            Image(
                painter = painterResource(R.mipmap.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .size(40.dp)
                    .graphicsLayer(scaleX = 1.4f, scaleY = 1.4f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        val now = System.currentTimeMillis()
                        // "Seguidas" = sin pausas largas: más de 1,5 s reinicia la cuenta
                        iconTaps = if (now - lastTapAt < 1500) iconTaps + 1 else 1
                        lastTapAt = now
                        if (iconTaps >= 6) {
                            iconTaps = 0
                            showDfy = true
                        }
                    }
            )
        },
        title = {
            Text(
                text = stringResource(R.string.app_name),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        },
        actions = {
            IconButton(onClick = onDonateClick) {
                Text("☕")
            }
            IconButton(onClick = onHelpClick) {
                Icon(Icons.Default.HelpOutline, contentDescription = stringResource(R.string.menu_about))
            }
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = null)
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_settings)) },
                    onClick = { menuExpanded = false; showSettings = true }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_report_bug)) },
                    onClick = { menuExpanded = false; showBugReport = true }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_contact)) },
                    onClick = { menuExpanded = false; showContact = true }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_about)) },
                    onClick = { menuExpanded = false; showAbout = true }
                )
            }
        }
    )

    if (showSettings) SettingsPopup(onDismiss = { showSettings = false })
    if (showBugReport) BugReportPopup(onDismiss = { showBugReport = false })
    if (showContact) ContactPopup(onDismiss = { showContact = false })
    if (showAbout) AboutPopup(onDismiss = { showAbout = false })
    if (showDfy) DfyEasterEgg(onDismiss = { showDfy = false })
}

@Composable
private fun DfyEasterEgg(onDismiss: () -> Unit) {
    // Se desvanece solo pasados unos segundos, como un guiño fugaz
    LaunchedEffect(Unit) {
        delay(2500)
        onDismiss()
    }
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(32.dp))
                .background(Brush.verticalGradient(listOf(SwapifyRed, SwapifyRedDeep)))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
                .padding(horizontal = 56.dp, vertical = 40.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Dfy",
                fontSize = 64.sp,
                fontWeight = FontWeight.Black,
                color = Color.White
            )
        }
    }
}