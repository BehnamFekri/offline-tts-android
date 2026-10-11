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
        require(store.isInstalled(voice)) { "${voice.displayName}: Model not installed" }

        val modelDir = store.installedDirectory(voice)
        val modelFile = File(modelDir, voice.modelFileName)
        val tokensFile = File(modelDir, "tokens.txt")
        val dataDir = File(modelDir, "espeak-ng-data")

        require(modelFile.isFile) { "${voice.displayName}: Missing ${voice.modelFileName}" }
        require(tokensFile.isFile) { "${voice.displayName}: Missing tokens.txt" }
        require(dataDir.isDirectory) { "${voice.displayName}: Missing espeak-ng-data directory" }

        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = modelFile.absolutePath,
                    tokens = tokensFile.absolutePath,
                    dataDir = dataDir.absolutePath,
                ),
                numThreads = 2,
                debug = false,
            ),
        )
        val tts = try {
            OfflineTts(config = config)
        } catch (error: Exception) {
            throw IllegalStateException("${voice.displayName}: Failed to initialize TTS engine - ${error.message}", error)
        }
        try {
            val audio = try {
                tts.generateWithConfig(
                    text = text,
                    config = GenerationConfig(sid = 0, speed = 1.0f, silenceScale = 0.2f),
                )
            } catch (error: Exception) {
                throw IllegalStateException("${voice.displayName}: Speech generation failed - ${error.message}", error)
            }
            require(audio.samples.isNotEmpty()) { "${voice.displayName}: Engine returned empty audio" }
            val target = File(outputDir, "speech-${System.currentTimeMillis()}.wav")
            val temporary = File(outputDir, "${target.name}.partial")
            try {
                check(audio.save(temporary.absolutePath)) { "${voice.displayName}: Could not write WAV audio" }
                check(temporary.renameTo(target)) { "${voice.displayName}: Could not finalize WAV audio" }
            } finally {
                temporary.delete()
            }
            target
        } finally {
            tts.release()
        }
    }
}
