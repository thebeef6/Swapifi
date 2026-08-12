package swapifi.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import swapifi.app.R
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwapifiTopBar(
    onDonateClick: () -> Unit = {},
    onSetupHelpClick: () -> Unit = {},
    onHowItWorksClick: () -> Unit = {}
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var helpMenuExpanded by remember { mutableStateOf(false) }
    var showBugReport by remember { mutableStateOf(false) }
    var showContact by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }

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
            Box {
                IconButton(onClick = { helpMenuExpanded = true }) {
                    Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = stringResource(R.string.help_menu_how_it_works))
                }
                DropdownMenu(
                    expanded = helpMenuExpanded,
                    onDismissRequest = { helpMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.help_menu_how_it_works)) },
                        onClick = { helpMenuExpanded = false; onHowItWorksClick() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.help_menu_setup)) },
                        onClick = { helpMenuExpanded = false; onSetupHelpClick() }
                    )
                }
            }
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = null)
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
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

    if (showBugReport) BugReportPopup(onDismiss = { showBugReport = false })
    if (showContact) ContactPopup(onDismiss = { showContact = false })
    if (showAbout) AboutPopup(onDismiss = { showAbout = false })
}