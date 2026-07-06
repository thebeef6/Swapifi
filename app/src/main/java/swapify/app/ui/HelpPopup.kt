package swapify.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun HelpPopup(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.help_title),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(stringResource(R.string.help_intro))
                Spacer(modifier = Modifier.height(12.dp))
                Text(stringResource(R.string.help_step1))
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.help_step2))
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.help_step3))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.understood))
            }
        }
    )
}