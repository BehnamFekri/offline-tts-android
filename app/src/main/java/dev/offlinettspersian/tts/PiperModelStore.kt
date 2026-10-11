package dev.offlinettspersian.tts

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

/** Copies an extracted sherpa-onnx Piper model into private storage without retaining external URI access. */
class PiperModelStore(context: Context) {
    private val appContext = context.applicationContext
    private val modelsDir = File(appContext.filesDir, "models")

    suspend fun recover() = withContext(Dispatchers.IO) {
        PersianVoiceCatalog.voices.forEach(::recoverInterruptedInstall)
    }

    fun installedDirectory(voice: PiperVoiceModel): File = File(modelsDir, voice.id)

    fun getCanonicalStoragePath(): String = modelsDir.absolutePath

    fun getModelStatus(voice: PiperVoiceModel): ModelStatus {
        val dir = installedDirectory(voice)
        val marker = File(dir, ".install-complete")
        val modelFile = File(dir, voice.modelFileName)
        val tokensFile = File(dir, "tokens.txt")
        val dataDir = File(dir, "espeak-ng-data")

        if (!dir.exists()) {
            return ModelStatus.NotDownloaded
        }

        val missing = mutableListOf<String>()
        if (!modelFile.isFile) missing.add(voice.modelFileName)
        if (!tokensFile.isFile) missing.add("tokens.txt")
        if (!dataDir.isDirectory) missing.add("espeak-ng-data/")

        if (missing.isNotEmpty()) {
            return ModelStatus.Incomplete(missing)
        }

        if (marker.isFile && marker.readText() == "${voice.id}\n") {
            return ModelStatus.Installed
        }

        return ModelStatus.Downloaded
    }

    fun isInstalled(voice: PiperVoiceModel): Boolean {
        return getModelStatus(voice) == ModelStatus.Installed
    }

    suspend fun install(
        voice: PiperVoiceModel,
        onProgress: (DownloadProgress) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        if (isInstalled(voice)) return@withContext installedDirectory(voice)

        check(modelsDir.mkdirs() || modelsDir.isDirectory) { "Could not create model storage" }
        val archive = File(modelsDir, "${voice.id}.download")
        val staging = File(modelsDir, "${voice.id}.installing")
        val destination = installedDirectory(voice)
        val backup = File(modelsDir, "${voice.id}.previous")
        staging.deleteRecursively()
        backup.deleteRecursively()

        try {
            if (!archive.exists() || archive.length() == 0L) {
                downloadWithRetry(voice, archive, onProgress)
                verifyArchive(voice, archive)
            } else {
                try {
                    verifyArchive(voice, archive)
                } catch (e: Exception) {
                    downloadWithRetry(voice, archive, onProgress)
                    verifyArchive(voice, archive)
                }
            }
            extractArchive(voice, archive, staging)
            validateModel(voice, staging)
            File(staging, ".install-complete").writeText("${voice.id}\n")
            if (destination.exists()) check(destination.renameTo(backup)) {
                "Could not stage the current model"
            }
            check(staging.renameTo(destination)) { "Could not finalize model installation" }
            backup.deleteRecursively()
            archive.delete()
            destination
        } catch (error: Exception) {
            staging.deleteRecursively()
            if (!destination.exists() && backup.exists()) backup.renameTo(destination)
            throw IllegalStateException("${voice.displayName} installation failed: ${error.message}", error)
        } finally {
            staging.deleteRecursively()
            if (destination.exists()) backup.deleteRecursively()
        }
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
            validateModel(voice, staging)
            File(staging, ".install-complete").writeText("${voice.id}\n")
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

    private fun validateModel(voice: PiperVoiceModel, directory: File) {
        require(File(directory, voice.modelFileName).isFile) { "Missing ${voice.modelFileName}" }
        require(File(directory, "tokens.txt").isFile) { "Missing tokens.txt" }
        require(File(directory, "espeak-ng-data").isDirectory) { "Missing espeak-ng-data" }
    }

    private fun downloadWithRetry(
        voice: PiperVoiceModel,
        destination: File,
        onProgress: (DownloadProgress) -> Unit,
    ) {
        var lastError: Exception? = null
        repeat(MAX_DOWNLOAD_ATTEMPTS) { attempt ->
            destination.delete()
            try {
                onProgress(DownloadProgress(0, 0))
                downloadArchive(voice, destination, onProgress)
                return
            } catch (error: CancellationException) {
                destination.delete()
                throw error
            } catch (error: Exception) {
                destination.delete()
                lastError = error
                if (attempt < MAX_DOWNLOAD_ATTEMPTS - 1) {
                    Thread.sleep(RETRY_DELAY_MS * (attempt + 1))
                }
            }
        }
        throw lastError ?: IllegalStateException("Model download failed")
    }

    private fun downloadArchive(
        voice: PiperVoiceModel,
        destination: File,
        onProgress: (DownloadProgress) -> Unit,
    ) {
        val url = URL(voice.archiveUrl)
        require(url.protocol == "https" && url.host == "github.com") {
            "Model download URL must use the official HTTPS host"
        }
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = false
            requestMethod = "GET"
        }
        try {
            var code = connection.responseCode
            if (code in 300..399) {
                val redirect = connection.getHeaderField("Location")
                    ?: error("Model download returned an invalid redirect")
                val redirectUrl = URL(url, redirect)
                require(
                    redirectUrl.protocol == "https" &&
                        (redirectUrl.host == "release-assets.githubusercontent.com" ||
                            redirectUrl.host.endsWith(".githubusercontent.com")),
                ) {
                    "Model download redirected to an untrusted host"
                }
                connection.disconnect()
                val redirected = (redirectUrl.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    instanceFollowRedirects = false
                    requestMethod = "GET"
                }
                try {
                    code = redirected.responseCode
                    check(code in 200..299) { "Model download failed (HTTP $code)" }
                    streamDownload(redirected, destination, onProgress)
                } finally {
                    redirected.disconnect()
                }
                return
            }
            check(code in 200..299) { "Model download failed (HTTP $code)" }
            streamDownload(connection, destination, onProgress)
        } finally {
            connection.disconnect()
        }
    }

    private fun streamDownload(
        connection: HttpURLConnection,
        destination: File,
        onProgress: (DownloadProgress) -> Unit,
    ) {
        val total = connection.contentLengthLong
        require(total < 0 || total <= MAX_ARCHIVE_BYTES) {
            "Model archive exceeds the allowed download size"
        }
        connection.inputStream.use { input ->
            destination.outputStream().buffered().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var downloaded = 0L
                var reported = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    downloaded += read
                    require(downloaded <= MAX_ARCHIVE_BYTES) {
                        "Model archive exceeds the allowed download size"
                    }
                    if (downloaded - reported >= PROGRESS_INTERVAL_BYTES ||
                        (total > 0 && downloaded >= total)
                    ) {
                        reported = downloaded
                        onProgress(DownloadProgress(downloaded, total))
                    }
                }
            }
        }
        onProgress(DownloadProgress(destination.length(), total))
        check(destination.length() > 0L) { "Downloaded model archive is empty" }
    }

    private fun extractArchive(voice: PiperVoiceModel, archive: File, destination: File) {
        check(destination.mkdirs() || destination.isDirectory) { "Could not create model staging folder" }
        val expectedRoot = voice.archiveFileName.removeSuffix(".tar.bz2")
        var totalExtractedBytes = 0L
        TarArchiveInputStream(BufferedInputStream(BZip2CompressorInputStream(FileInputStream(archive)))).use { tar ->
            var entry = tar.nextTarEntry
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (entry != null) {
                val normalizedName = entry.name.replace('\\', '/').trimEnd('/')
                val parts = normalizedName.split('/')
                require(!entry.isSymbolicLink && !entry.isLink && parts.none { it == ".." || it.isEmpty() }) {
                    "Model archive contains an unsafe path"
                }
                require(!normalizedName.startsWith('/') && !Regex("^[A-Za-z]:").containsMatchIn(normalizedName)) {
                    "Model archive contains an absolute path"
                }
                if (parts.size == 1 && parts.first() == expectedRoot && entry.isDirectory) {
                    entry = tar.nextTarEntry
                    continue
                }
                require(parts.first() == expectedRoot && parts.size >= 2) {
                    "Unexpected path in model archive"
                }
                val relative = parts.drop(1).joinToString("/")
                if (relative.isNotEmpty()) {
                    val target = File(destination, relative).canonicalFile
                    require(target.toPath().startsWith(destination.canonicalFile.toPath())) {
                        "Model archive path escapes its staging folder"
                    }
                    if (entry.isDirectory) {
                        require(target.mkdirs() || target.isDirectory) { "Could not create model subfolder" }
                    } else {
                        require(entry.isFile) { "Unsupported entry in model archive" }
                        val parentDir = target.parentFile
                        if (parentDir != null && !parentDir.exists()) {
                            require(parentDir.mkdirs()) { "Could not create model subfolder: ${parentDir.absolutePath}" }
                        }
                        require(parentDir == null || parentDir.isDirectory) { "Parent is not a directory: ${parentDir?.absolutePath}" }
                        BufferedOutputStream(FileOutputStream(target)).use { output ->
                            while (true) {
                                val read = tar.read(buffer)
                                if (read < 0) break
                                totalExtractedBytes += read
                                require(totalExtractedBytes <= MAX_EXTRACTED_BYTES) {
                                    "Model archive exceeds the allowed extracted size"
                                }
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                }
                entry = tar.nextTarEntry
            }
        }
    }

    private fun verifyArchive(voice: PiperVoiceModel, archive: File) {
        val digest = MessageDigest.getInstance("SHA-256")
        archive.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        require(actual == voice.archiveSha256) {
            "${voice.displayName} archive failed SHA-256 integrity verification"
        }
    }

    private fun recoverInterruptedInstall(voice: PiperVoiceModel) {
        val destination = installedDirectory(voice)
        val backup = File(modelsDir, "${voice.id}.previous")
        val staging = File(modelsDir, "${voice.id}.installing")
        val importStaging = File(modelsDir, "${voice.id}.importing")
        val archive = File(modelsDir, "${voice.id}.download")
        val marker = File(destination, ".install-complete")
        val destinationIsComplete = marker.isFile && marker.readText() == "${voice.id}\n"
        if (destinationIsComplete) {
            backup.deleteRecursively()
        } else if (backup.exists()) {
            destination.deleteRecursively()
            check(backup.renameTo(destination)) { "Could not recover the previous ${voice.displayName} model" }
        }
        staging.deleteRecursively()
        importStaging.deleteRecursively()
        if (!destinationIsComplete) {
            archive.delete()
        }
    }

    private companion object {
        const val MAX_EXTRACTED_BYTES = 1_000_000_000L
        const val MAX_ARCHIVE_BYTES = 128 * 1_000_000L
        const val MAX_DOWNLOAD_ATTEMPTS = 3
        const val RETRY_DELAY_MS = 750L
        const val PROGRESS_INTERVAL_BYTES = 512 * 1024L
    }
}

data class DownloadProgress(val downloadedBytes: Long, val totalBytes: Long) {
    val fraction: Float? get() = totalBytes.takeIf { it > 0 }?.let {
        (downloadedBytes.toFloat() / it).coerceIn(0f, 1f)
    }

    val downloadedMegabytes: Long get() = downloadedBytes / 1_000_000
    val totalMegabytes: Long? get() = totalBytes.takeIf { it > 0 }?.div(1_000_000)
}
