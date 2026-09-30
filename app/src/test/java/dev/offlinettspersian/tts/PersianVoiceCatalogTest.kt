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
    }

    @Test
    fun modelIdsCannotEscapeModelDirectory() {
        listOf("../voice", "Voice", "voice/name").forEach { id ->
            val result = runCatching {
                PiperVoiceModel(id, "voice", "voice.onnx", "https://example.invalid", "license")
            }
            assertTrue("$id must be rejected", result.isFailure)
        }
        listOf("../voice.onnx", "..\\voice.onnx").forEach { filename ->
            val result = runCatching {
                PiperVoiceModel("valid", "voice", filename, "https://example.invalid", "license")
            }
            assertTrue("$filename must be rejected", result.isFailure)
        }
    }
}
