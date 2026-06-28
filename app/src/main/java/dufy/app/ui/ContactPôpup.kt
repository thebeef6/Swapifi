package dufy.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ContactPopup(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Contacto",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text("¿Tienes alguna pregunta o sugerencia?")
                Spacer(modifier = androidx.compose.ui.Modifier.height(12.dp))
                Text(
                    text = "tuemail@ejemplo.com",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}