package dev.offlinettspersian.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersianVoiceCatalogTest {
    @Test
    fun catalogContainsMultipleExplicitPersianVoices() {
        assertTrue(PersianVoiceCatalog.voices.size >= 2)
        assertEquals(PersianVoiceCatalog.voices.size, PersianVoiceCatalog.voices.map { it.id }.toSet().size)
        assertTrue(PersianVoiceCatalog.voices.all { it.id.startsWith("fa-ir-") })
        assertTrue(PersianVoiceCatalog.voices.all { it.modelFileName.endsWith(".onnx") })
        assertTrue(PersianVoiceCatalog.voices.all {
            it.archiveUrl.startsWith("https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/")
        })
        assertTrue(PersianVoiceCatalog.voices.all { it.archiveSha256.matches(Regex("[a-f0-9]{64}")) })
        assertEquals(3, PersianVoiceCatalog.voices.size)
    }

    @Test
    fun modelIdsCannotEscapeModelDirectory() {
        listOf("../voice", "Voice", "voice/name").forEach { id ->
            val result = runCatching {
                PiperVoiceModel(id, "voice", "voice.onnx", "https://example.invalid/model.tar.bz2", "model.tar.bz2", "${"0".repeat(64)}", "license")
            }
            assertTrue("$id must be rejected", result.isFailure)
        }
        listOf("../voice.onnx", "..\\voice.onnx").forEach { filename ->
            val result = runCatching {
                PiperVoiceModel("valid", "voice", filename, "https://example.invalid/model.tar.bz2", "model.tar.bz2", "${"0".repeat(64)}", "license")
            }
            assertTrue("$filename must be rejected", result.isFailure)
        }
    }

    @Test
    fun downloadProgressHandlesUnknownAndKnownContentLengths() {
        assertEquals(null, DownloadProgress(20, 0).fraction)
        assertEquals(0.5f, DownloadProgress(50, 100).fraction)
        assertEquals(1f, DownloadProgress(200, 100).fraction)
    }
}
