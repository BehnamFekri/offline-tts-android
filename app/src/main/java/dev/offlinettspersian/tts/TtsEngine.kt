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
    val id: String,
    val displayName: String,
    val modelFileName: String,
    val sourceUrl: String,
    val licenseNote: String,
) : SpeechModel {
    init {
        require(id.matches(Regex("[a-z0-9_-]+"))) { "Voice id must be a safe directory name" }
        require(modelFileName.matches(Regex("[A-Za-z0-9_.-]+\\.onnx"))) {
            "Model filename must be a safe ONNX filename"
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
            sourceUrl = "https://huggingface.co/rhasspy/piper-voices/tree/main/fa/fa_IR/amir/medium",
            licenseNote = "Dataset: Datacula, CC0; review upstream terms before redistribution.",
        ),
        PiperVoiceModel(
            id = "fa-ir-ganji-medium",
            displayName = "Ganji (medium)",
            modelFileName = "fa_IR-ganji-medium.onnx",
            sourceUrl = "https://huggingface.co/rhasspy/piper-voices/tree/main/fa/fa_IR/ganji/medium",
            licenseNote = "Dataset: Datacula, CC0; review upstream terms before redistribution.",
        ),
        PiperVoiceModel(
            id = "fa-ir-gyro-medium",
            displayName = "Gyro (medium)",
            modelFileName = "fa_IR-gyro-medium.onnx",
            sourceUrl = "https://huggingface.co/rhasspy/piper-voices/tree/main/fa/fa_IR/gyro/medium",
            licenseNote = "Model card says dataset license is at the dataset source; review before redistribution.",
        ),
    )
}
