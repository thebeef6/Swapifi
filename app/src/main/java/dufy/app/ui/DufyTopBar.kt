package dufy.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.font.FontWeight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DufyTopBar(
    onDonateClick: () -> Unit = {},
    onHelpClick: () -> Unit = {}
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showBugReport by remember { mutableStateOf(false) }
    var showContact by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Text(
                text = "Dufy",
                fontWeight = FontWeight.Bold
            )
        },
        actions = {
            IconButton(onClick = onDonateClick) {
                Text("☕")
            }
            IconButton(onClick = onHelpClick) {
                Icon(Icons.Default.HelpOutline, contentDescription = "Ayuda")
            }
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Más opciones")
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Ajustes") },
                    onClick = { menuExpanded = false; showSettings = true }
                )
                DropdownMenuItem(
                    text = { Text("Reportar un bug") },
                    onClick = { menuExpanded = false; showBugReport = true }
                )
                DropdownMenuItem(
                    text = { Text("Contacto") },
                    onClick = { menuExpanded = false; showContact = true }
                )
                DropdownMenuItem(
                    text = { Text("Acerca de") },
                    onClick = { menuExpanded = false; showAbout = true }
                )
            }
        }
    )

    if (showSettings) SettingsPopup(onDismiss = { showSettings = false })
    if (showBugReport) BugReportPopup(onDismiss = { showBugReport = false })
    if (showContact) ContactPopup(onDismiss = { showContact = false })
    if (showAbout) AboutPopup(onDismiss = { showAbout = false })
}