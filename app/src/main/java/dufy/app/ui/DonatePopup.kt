package dufy.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun DonatePopup(
    onDismiss: () -> Unit,
    onAmountSelected: (Int) -> Unit
) {
    val amounts = listOf(1, 3, 5, 10)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Apoya a Dufy",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text("Si te gusta la app, puedes invitarme a un café ☕")
                Spacer(modifier = Modifier.height(16.dp))
                amounts.forEach { amount ->
                    OutlinedButton(
                        onClick = { onAmountSelected(amount) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Text("$amount €")
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}