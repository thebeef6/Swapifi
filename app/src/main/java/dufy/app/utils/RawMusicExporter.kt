package dufy.app.utils

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream

object RawMusicExporter {

    private val songs = mapOf(
        "musica_sin_copyright" to "Musica 1.mp3",
        "musica_sin_copyright_2" to "Musica 2.mp3",
        "musica_sin_copyright_3" to "Musica 3.mp3"
    )

    fun exportIfNeeded(context: Context) {
        val dufyFolder = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            "Dufy"
        )

        if (!dufyFolder.exists()) {
            dufyFolder.mkdirs()
        }

        val filesToScan = mutableListOf<String>()

        for ((rawName, fileName) in songs) {
            val destFile = File(dufyFolder, fileName)
            if (!destFile.exists()) {
                try {
                    val resId = context.resources.getIdentifier(rawName, "raw", context.packageName)
                    if (resId == 0) {
                        Log.e("Dufy", "❌ No se encontró el recurso raw: $rawName")
                        continue
                    }
                    context.resources.openRawResource(resId).use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    Log.d("Dufy", "✅ Exportada: $fileName")
                } catch (e: Exception) {
                    Log.e("Dufy", "❌ Error exportando $rawName: ${e.message}")
                }
            } else {
                Log.d("Dufy", "⏭ Ya existe: $fileName")
            }
            // Escanear siempre, exista o no
            filesToScan.add(destFile.absolutePath)
        }

        // Notificar a MediaStore de todos los archivos nuevos a la vez
        if (filesToScan.isNotEmpty()) {
            MediaScannerConnection.scanFile(
                context,
                filesToScan.toTypedArray(),
                filesToScan.map { "audio/mpeg" }.toTypedArray(),
                { path, uri -> Log.d("Dufy", "📱 MediaStore indexado: $path") }
            )
        }
    }
}