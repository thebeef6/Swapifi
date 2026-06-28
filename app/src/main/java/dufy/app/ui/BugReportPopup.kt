package dufy.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.net.Uri

@Composable
fun BugReportPopup(onDismiss: () -> Unit) {
    var bugText by remember { mutableStateOf("") }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Reportar un bug",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text("Describe el problema que has encontrado:")
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = bugText,
                    onValueChange = { bugText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Ej: la música no se silencia al pasar de canción...") },
                    minLines = 4
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:tuemail@ejemplo.com")
                        putExtra(Intent.EXTRA_SUBJECT, "Reporte de bug - Dufy")
                        putExtra(Intent.EXTRA_TEXT, bugText)
                    }
                    context.startActivity(intent)
                    onDismiss()
                },
                enabled = bugText.isNotBlank()
            ) {
                Text("Enviar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}