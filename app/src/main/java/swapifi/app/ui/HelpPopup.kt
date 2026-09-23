package swapifi.app.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import swapifi.app.R

@Composable
fun SetupHelpPopup(onDismiss: () -> Unit) {
    val context = LocalContext.current
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
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.help_step4))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.understood))
            }
        },
        dismissButton = {
            TextButton(onClick = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            }) {
                Text(stringResource(R.string.open_app_settings))
            }
        }
    )
}

@Composable
fun HowItWorksPopup(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.how_title),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(stringResource(R.string.how_point1))
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.how_point2))
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.how_point3))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.understood))
            }
        }
    )
}
