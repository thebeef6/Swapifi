package dufy.app.ui

import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.io.File

@Composable
fun FolderPickerPopup(
    onDismiss: () -> Unit,
    onFolderSelected: (String) -> Unit
) {
    val rootDir = remember { Environment.getExternalStorageDirectory() }
    var currentDir by remember { mutableStateOf(rootDir) }

    val subFolders = remember(currentDir) {
        currentDir.listFiles()
            ?.filter { it.isDirectory && !it.isHidden }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (currentDir != rootDir) {
                    IconButton(onClick = {
                        currentDir = currentDir.parentFile ?: rootDir
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                }
                Text(
                    text = currentDir.name,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 350.dp)) {
                items(subFolders) { folder ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { currentDir = folder }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = folder.name)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onFolderSelected(currentDir.path) }) {
                Text("Elegir esta carpeta")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}