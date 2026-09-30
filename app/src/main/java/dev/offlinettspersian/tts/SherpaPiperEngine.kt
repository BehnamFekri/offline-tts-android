package dev.offlinettspersian.tts

import android.content.Context
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SherpaPiperEngine(context: Context) : TtsEngine {
    private val store = PiperModelStore(context)
    private val outputDir = File(context.applicationContext.filesDir, "audio").apply { mkdirs() }

    override suspend fun generate(text: String, model: SpeechModel): File = withContext(Dispatchers.IO) {
        val voice = model as? PiperVoiceModel ?: error("This engine requires a Piper voice model")
        require(text.isNotBlank()) { "Enter Persian text to synthesize" }
        require(store.isInstalled(voice)) { "Import the ${voice.displayName} model first" }

        val modelDir = store.installedDirectory(voice)
        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = File(modelDir, voice.modelFileName).absolutePath,
                    tokens = File(modelDir, "tokens.txt").absolutePath,
                    dataDir = File(modelDir, "espeak-ng-data").absolutePath,
                ),
                numThreads = 2,
                debug = false,
            ),
        )
        val tts = OfflineTts(config = config)
        try {
            val audio = tts.generateWithConfig(
                text = text,
                config = GenerationConfig(sid = 0, speed = 1.0f, silenceScale = 0.2f),
            )
            require(audio.samples.isNotEmpty()) { "The engine returned empty audio" }
            val target = File(outputDir, "speech-${System.currentTimeMillis()}.wav")
            val temporary = File(outputDir, "${target.name}.partial")
            try {
                check(audio.save(temporary.absolutePath)) { "Could not write WAV audio" }
                check(temporary.renameTo(target)) { "Could not finalize WAV audio" }
            } finally {
                temporary.delete()
            }
            target
        } finally {
            tts.release()
        }
    }
}
