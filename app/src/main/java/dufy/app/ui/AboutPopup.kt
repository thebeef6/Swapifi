package dufy.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun AboutPopup(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Acerca de Dufy",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text("Dufy v1.0")
                Spacer(modifier = androidx.compose.ui.Modifier.height(12.dp))
                Text("Dufy silencia los anuncios de Spotify y reproduce tu música local mientras tanto.")
                Spacer(modifier = androidx.compose.ui.Modifier.height(12.dp))
                Text("Hecho con ❤️ de forma independiente.")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}