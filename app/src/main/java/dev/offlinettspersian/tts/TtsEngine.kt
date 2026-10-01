package dev.offlinettspersian.tts

import java.io.File

/** Replaceable boundary between the app flow and a concrete speech engine. */
interface TtsEngine {
    suspend fun generate(text: String, model: SpeechModel): File
}

interface SpeechModel {
    val id: String
    val displayName: String
}

data class PiperVoiceModel(
    override val id: String,
    override val displayName: String,
    val modelFileName: String,
    val archiveUrl: String,
    val archiveFileName: String,
    val archiveSha256: String,
    val licenseNote: String,
) : SpeechModel {
    init {
        require(id.matches(Regex("[a-z0-9_-]+"))) { "Voice id must be a safe directory name" }
        require(modelFileName.matches(Regex("[A-Za-z0-9_.-]+\\.onnx"))) {
            "Model filename must be a safe ONNX filename"
        }
        require(archiveFileName.matches(Regex("[A-Za-z0-9_.-]+\\.tar\\.bz2"))) {
            "Archive filename must be a safe tar.bz2 filename"
        }
        require(archiveSha256.matches(Regex("[a-f0-9]{64}"))) {
            "Archive SHA-256 must be a lowercase 64-character hexadecimal string"
        }
    }
}

/** Initial comparison set. Each model archive is downloaded separately from source. */
object PersianVoiceCatalog {
    val voices = listOf(
        PiperVoiceModel(
            id = "fa-ir-amir-medium",
            displayName = "Amir (medium)",
            modelFileName = "fa_IR-amir-medium.onnx",
            archiveUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-fa_IR-amir-medium.tar.bz2",
            archiveFileName = "vits-piper-fa_IR-amir-medium.tar.bz2",
            archiveSha256 = "46af65c1cdd86ec300db3998f2bf31a68d9e38da930c0655c1ff6f610e84f864",
            licenseNote = "Dataset: Datacula, CC0; review upstream terms before redistribution.",
        ),
        PiperVoiceModel(
            id = "fa-ir-ganji-medium",
            displayName = "Ganji (medium)",
            modelFileName = "fa_IR-ganji-medium.onnx",
            archiveUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-fa_IR-ganji-medium.tar.bz2",
            archiveFileName = "vits-piper-fa_IR-ganji-medium.tar.bz2",
            archiveSha256 = "6eae2acccd1b4460159fa5acdad4bb5d2df6d64d8da3e4029dbdff4db14e7a7a",
            licenseNote = "Dataset: Datacula, CC0; review upstream terms before redistribution.",
        ),
        PiperVoiceModel(
            id = "fa-ir-gyro-medium",
            displayName = "Gyro (medium)",
            modelFileName = "fa_IR-gyro-medium.onnx",
            archiveUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-fa_IR-gyro-medium.tar.bz2",
            archiveFileName = "vits-piper-fa_IR-gyro-medium.tar.bz2",
            archiveSha256 = "75dc485f0c0528b1b07800464e5c72f4229339919525b3f634aa89690988d30e",
            licenseNote = "Model card says dataset license is at the dataset source; review before redistribution.",
        ),
    )
}
