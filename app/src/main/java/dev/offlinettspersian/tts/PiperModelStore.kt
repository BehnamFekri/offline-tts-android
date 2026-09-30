package dev.offlinettspersian.tts

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File

/** Copies an extracted sherpa-onnx Piper model into private storage without retaining external URI access. */
class PiperModelStore(context: Context) {
    private val appContext = context.applicationContext
    private val modelsDir = File(appContext.filesDir, "models")

    fun installedDirectory(voice: PiperVoiceModel): File = File(modelsDir, voice.id)

    fun isInstalled(voice: PiperVoiceModel): Boolean {
        val dir = installedDirectory(voice)
        return File(dir, voice.modelFileName).isFile &&
            File(dir, "tokens.txt").isFile &&
            File(dir, "espeak-ng-data").isDirectory
    }

    /** The selected directory must be the extracted root of the sherpa-onnx Piper archive. */
    fun import(voice: PiperVoiceModel, sourceTree: Uri): File {
        val root = DocumentFile.fromTreeUri(appContext, sourceTree)
            ?: error("Could not open the selected model folder")
        require(root.isDirectory) { "Select the extracted model folder" }

        check(modelsDir.mkdirs() || modelsDir.isDirectory) { "Could not create model storage" }
        val staging = File(modelsDir, "${voice.id}.importing")
        val destination = installedDirectory(voice)
        val backup = File(modelsDir, "${voice.id}.previous")
        staging.deleteRecursively()
        backup.deleteRecursively()
        require(staging.mkdirs()) { "Could not create private model storage" }

        try {
            copyTree(root, staging)
            require(File(staging, voice.modelFileName).isFile) { "Missing ${voice.modelFileName}" }
            require(File(staging, "tokens.txt").isFile) { "Missing tokens.txt" }
            require(File(staging, "espeak-ng-data").isDirectory) { "Missing espeak-ng-data" }
            if (destination.exists()) {
                check(destination.renameTo(backup)) { "Could not stage the current model" }
            }
            check(staging.renameTo(destination)) { "Could not finalize model import" }
            backup.deleteRecursively()
            return destination
        } catch (error: Exception) {
            staging.deleteRecursively()
            if (!destination.exists() && backup.exists()) backup.renameTo(destination)
            throw error
        }
    }

    private fun copyTree(source: DocumentFile, destination: File) {
        source.listFiles().forEach { child ->
            val name = child.name ?: error("Model contains an unnamed file")
            require(name != "." && name != ".." && !name.contains('/') && !name.contains('\\')) {
                "Model contains an unsafe path"
            }
            val output = File(destination, name)
            if (child.isDirectory) {
                require(output.mkdirs() || output.isDirectory) { "Could not create model subfolder" }
                copyTree(child, output)
            } else {
                val input = appContext.contentResolver.openInputStream(child.uri)
                    ?: error("Could not read $name")
                input.use { stream -> output.outputStream().use(stream::copyTo) }
            }
        }
    }
}
