package swapifi.app.ui

import android.content.Intent
import androidx.core.net.toUri
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
fun BugReportPopup(onDismiss: () -> Unit) {
    var bugText by remember { mutableStateOf("") }
    val context = LocalContext.current
    val emailSubject = stringResource(R.string.bug_email_subject)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.bug_title),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(stringResource(R.string.bug_description))
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = bugText,
                    onValueChange = { bugText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.bug_placeholder)) },
                    minLines = 4
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // Asunto y cuerpo van codificados en la propia URI mailto:
                    // varias apps de correo ignoran los extras del intent y solo
                    // leen los parámetros subject/body de la URI. Los extras se
                    // mantienen como respaldo para las que hacen lo contrario.
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = ("mailto:davidig.info@gmail.com" +
                                "?subject=" + android.net.Uri.encode(emailSubject) +
                                "&body=" + android.net.Uri.encode(bugText)).toUri()
                        putExtra(Intent.EXTRA_SUBJECT, emailSubject)
                        putExtra(Intent.EXTRA_TEXT, bugText)
                    }
                    context.startActivity(intent)
                    onDismiss()
                },
                enabled = bugText.isNotBlank()
            ) {
                Text(stringResource(R.string.send))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}