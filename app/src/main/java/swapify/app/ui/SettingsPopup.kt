package swapify.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SettingsPopup(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var showFolderPicker by remember { mutableStateOf(false) }
    val currentFolder = swapify.app.state.PlayerState.selectedFolder.value

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.settings_title),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(stringResource(R.string.settings_music_folder), fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(currentFolder.ifEmpty { stringResource(R.string.settings_music_folder_default) })
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = { showFolderPicker = true }) {
                    Text(stringResource(R.string.settings_change_folder))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )

    if (showFolderPicker) {
        FolderPickerPopup(
            onDismiss = { showFolderPicker = false },
            onFolderSelected = { folder ->
                swapify.app.state.PlayerState.setSelectedFolder(context, folder)
                showFolderPicker = false
            }
        )
    }
}