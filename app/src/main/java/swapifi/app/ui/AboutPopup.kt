package swapifi.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.stringResource
import swapifi.app.R
@Composable
fun AboutPopup(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.about_title),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(stringResource(R.string.about_version))
                Spacer(modifier = androidx.compose.ui.Modifier.height(12.dp))
                Text(stringResource(R.string.about_description))
                Spacer(modifier = androidx.compose.ui.Modifier.height(12.dp))
                Text(stringResource(R.string.about_made_with))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}