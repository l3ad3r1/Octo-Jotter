package com.l3ad3r1.octojotter

import com.l3ad3r1.octojotter.ai.model.ModelCatalog
import com.l3ad3r1.octojotter.ai.model.ModelManager
import com.l3ad3r1.octojotter.ai.model.ModelStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ModelCatalogTest {

    @Test
    fun `shared dir name matches the Hermes cross-app contract`() {
        // Hermes ModelCatalog.DEFAULT_DIR_NAME is "AI Models"; renaming breaks reuse.
        assertEquals("AI Models", ModelStorage.SHARED_DIR_NAME)
    }

    @Test
    fun `chat model filenames mirror Hermes so downloads are reused`() {
        val names = ModelCatalog.CHAT_MODELS.map { it.file.fileName }.toSet()
        assertTrue(names.contains("Llama-3.2-1B-Instruct-Q4_K_M.gguf"))
        assertTrue(names.contains("Qwen2.5-1.5B-Instruct-Q4_K_M.gguf"))
        assertTrue(names.contains("Qwen2.5-3B-Instruct-Q4_K_M.gguf"))
        assertTrue(names.contains("Llama-3.2-3B-Instruct-Q4_K_M.gguf"))
        // All chat URLs are HuggingFace resolve links.
        assertTrue(ModelCatalog.CHAT_MODELS.all { it.file.url.startsWith("https://huggingface.co/") })
    }

    @Test
    fun `embedding bundle has verified sizes and 384 dims`() {
        val e = ModelCatalog.EMBEDDING
        assertEquals(384, e.dimension)
        assertEquals(22_972_370L, e.model.sizeBytes)
        assertEquals(231_508L, e.vocab.sizeBytes)
        assertEquals(22_972_370L + 231_508L, e.totalBytes)
    }

    @Test
    fun `size label formats MB and GB`() {
        assertEquals("22 MB", ModelCatalog.EMBEDDING.model.sizeLabel)
        assertEquals("1.8 GB", ModelCatalog.chatById("qwen2.5-3b-q4km").file.sizeLabel)
    }

    @Test
    fun `unknown chat id falls back to default`() {
        assertEquals(ModelCatalog.DEFAULT_CHAT.id, ModelCatalog.chatById("nope").id)
    }

    @Test
    fun `presence check honours expected size then non-empty`() {
        val dir = File(System.getProperty("java.io.tmpdir"), "octo-mm-${System.nanoTime()}").apply { mkdirs() }
        val f = File(dir, "m.bin")
        assertFalse(ModelManager.isFilePresent(f, 10L))
        f.writeBytes(ByteArray(10))
        assertTrue(ModelManager.isFilePresent(f, 10L))   // exact size match
        assertFalse(ModelManager.isFilePresent(f, 11L))  // wrong size => not present
        assertTrue(ModelManager.isFilePresent(f, null))  // unknown size => non-empty ok
        File(dir, "empty.bin").createNewFile()
        assertFalse(ModelManager.isFilePresent(File(dir, "empty.bin"), null)) // empty => absent
        dir.deleteRecursively()
    }
}
