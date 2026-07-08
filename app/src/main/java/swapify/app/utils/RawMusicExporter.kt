package swapify.app.utils

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream

object RawMusicExporter {

    private val songs = mapOf(
        swapify.app.R.raw.halfway_in to "Halfway In - Anno Domini Beats.mp3",
        swapify.app.R.raw.never_coming_down to "Never Coming Down - The Soundlings.mp3",
        swapify.app.R.raw.two_things to "Two Things - Anno Domini Beats.mp3"
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

        for ((resId, fileName) in songs) {
            val destFile = File(SwapifyFolder, fileName)
            if (!destFile.exists()) {
                try {
                    context.resources.openRawResource(resId).use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    Log.d("Swapify", "✅ Exportada: $fileName")
                } catch (e: Exception) {
                    Log.e("Swapify", "❌ Error exportando $fileName: ${e.message}")
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