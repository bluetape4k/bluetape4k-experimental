package io.bluetape4k.benchmark.serializer

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue

class SnapshotCliOutputPathTest {

    @Test
    fun `serializer snapshot handles filename-only output path`() {
        val outputFile = File("serialized-size.json")

        try {
            writeSerializerMetricsSnapshot(outputFile.name)
            assertTrue(outputFile.exists())
            assertTrue(outputFile.length() > 0)
        } finally {
            outputFile.delete()
        }
    }

    @Test
    fun `compressor snapshot creates nested output directories`() {
        val workDir = Files.createTempDirectory("serializer-combo-snapshot")
        val output = workDir.resolve("nested/combo-size.json")

        try {
            writeSerializerCompressorMetricsSnapshot(output.toString())

            assertTrue(Files.exists(output))
            assertTrue(Files.size(output) > 0)
        } finally {
            Files.deleteIfExists(output)
            Files.deleteIfExists(output.parent)
            Files.deleteIfExists(workDir)
        }
    }
}
