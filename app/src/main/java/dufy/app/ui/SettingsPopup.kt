package dufy.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SettingsPopup(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("dufy_prefs", android.content.Context.MODE_PRIVATE)
    }
    var selectedLanguage by remember {
        mutableStateOf(prefs.getString("language", "es") ?: "es")
    }
    var showFolderPicker by remember { mutableStateOf(false) }
    val currentFolder = dufy.app.state.PlayerState.selectedFolder.value

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Ajustes",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text("Carpeta de música", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(currentFolder.ifEmpty { "Todas las carpetas" })
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = { showFolderPicker = true }) {
                    Text("Cambiar carpeta")
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text("Idioma", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row {
                    FilterChip(
                        selected = selectedLanguage == "es",
                        onClick = {
                            selectedLanguage = "es"
                            prefs.edit().putString("language", "es").apply()
                        },
                        label = { Text("Español") }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = selectedLanguage == "en",
                        onClick = {
                            selectedLanguage = "en"
                            prefs.edit().putString("language", "en").apply()
                        },
                        label = { Text("English") }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )

    if (showFolderPicker) {
        FolderPickerPopup(
            onDismiss = { showFolderPicker = false },
            onFolderSelected = { folder ->
                dufy.app.state.PlayerState.setSelectedFolder(context, folder)
                showFolderPicker = false
            }
        )
    }
}