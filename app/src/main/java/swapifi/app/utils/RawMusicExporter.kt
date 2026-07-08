package swapifi.app.utils

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream

object RawMusicExporter {

    private val songs = mapOf(
        swapifi.app.R.raw.halfway_in to "Halfway In - Anno Domini Beats.mp3",
        swapifi.app.R.raw.never_coming_down to "Never Coming Down - The Soundlings.mp3",
        swapifi.app.R.raw.two_things to "Two Things - Anno Domini Beats.mp3"
    )

    fun exportIfNeeded(context: Context) {
        val SwapifiFolder = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            "Swapifi"
        )

        if (!SwapifiFolder.exists()) {
            SwapifiFolder.mkdirs()
        }

        val filesToScan = mutableListOf<String>()

        for ((resId, fileName) in songs) {
            val destFile = File(SwapifiFolder, fileName)
            if (!destFile.exists()) {
                try {
                    context.resources.openRawResource(resId).use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    Log.d("Swapifi", "✅ Exportada: $fileName")
                } catch (e: Exception) {
                    Log.e("Swapifi", "❌ Error exportando $fileName: ${e.message}")
                }
            } else {
                Log.d("Swapifi", "⏭ Ya existe: $fileName")
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
                { path, uri -> Log.d("Swapifi", "📱 MediaStore indexado: $path") }
            )
        }
    }
}