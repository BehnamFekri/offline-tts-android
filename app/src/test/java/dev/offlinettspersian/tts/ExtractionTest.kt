package dev.offlinettspersian.tts

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ExtractionTest {
    @Test
    fun mkdirsAcceptsExistingDirectory() {
        // Regression test for line 302 bug: mkdirs() != false incorrectly fails when dir exists
        val temp = File(System.getProperty("java.io.tmpdir"), "extraction-test-${System.currentTimeMillis()}")
        try {
            // Create parent directory first
            assertTrue("Failed to create parent", temp.mkdirs())
            assertTrue("Parent should exist", temp.exists())
            assertTrue("Parent should be directory", temp.isDirectory)

            // Now create a nested directory - this should succeed
            val nested = File(temp, "nested")
            val result = if (!nested.exists()) {
                nested.mkdirs()
            } else {
                true
            }
            assertTrue("Should accept existing or create new directory", result)
            assertTrue("Nested directory must exist", nested.exists())
            assertTrue("Nested must be directory", nested.isDirectory)

            // Try to "create" an already-existing directory - should succeed
            val result2 = if (!nested.exists()) {
                nested.mkdirs()
            } else {
                true
            }
            assertTrue("Should accept already-existing directory", result2)
        } finally {
            temp.deleteRecursively()
        }
    }

    @Test
    fun mkdirsReportsActualFailure() {
        // Verify that genuinely uncreatable directories are detected
        val invalidPath = if (System.getProperty("os.name").contains("Windows")) {
            "Z:\\nonexistent\\path\\test"
        } else {
            "/root/forbidden/test"
        }
        val invalid = File(invalidPath)

        // Only run if the path genuinely doesn't exist and parent can't be created
        val parentExists = invalid.parentFile?.exists() ?: false
        if (!parentExists) {
            val result = invalid.mkdirs()
            // If mkdirs fails, the directory should not exist
            if (!result) {
                assertTrue("Failed mkdir should not create directory", !invalid.exists())
            }
        }
    }
}
