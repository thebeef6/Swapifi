package swapify.app.utils

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
        val SwapifyFolder = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            "Swapify"
        )

        if (!SwapifyFolder.exists()) {
            SwapifyFolder.mkdirs()
        }

        val filesToScan = mutableListOf<String>()

        for ((rawName, fileName) in songs) {
            val destFile = File(SwapifyFolder, fileName)
            if (!destFile.exists()) {
                try {
                    val resId = context.resources.getIdentifier(rawName, "raw", context.packageName)
                    if (resId == 0) {
                        Log.e("Swapify", "❌ No se encontró el recurso raw: $rawName")
                        continue
                    }
                    context.resources.openRawResource(resId).use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    Log.d("Swapify", "✅ Exportada: $fileName")
                } catch (e: Exception) {
                    Log.e("Swapify", "❌ Error exportando $rawName: ${e.message}")
                }
            } else {
                Log.d("Swapify", "⏭ Ya existe: $fileName")
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
                { path, uri -> Log.d("Swapify", "📱 MediaStore indexado: $path") }
            )
        }
    }
}