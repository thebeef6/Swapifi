package dufy.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun HelpPopup(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Configura Dufy",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text("Para que Dufy funcione correctamente, sigue estos pasos:")
                Spacer(modifier = Modifier.height(12.dp))
                Text("1. Concede el permiso de música y audio cuando se solicite")
                Spacer(modifier = Modifier.height(8.dp))
                Text("2. Desactiva la optimización de batería para Dufy en los ajustes del sistema")
                Spacer(modifier = Modifier.height(8.dp))
                Text("3. Mantén Dufy abierta en segundo plano mientras usas Spotify")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Entendido")
            }
        }
    )
}