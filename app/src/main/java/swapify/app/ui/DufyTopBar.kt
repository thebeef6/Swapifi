package swapify.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import swapify.app.R
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

    TopAppBar(
        title = {
            Text(
                text = stringResource(R.string.app_name),
                fontWeight = FontWeight.Bold
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
}