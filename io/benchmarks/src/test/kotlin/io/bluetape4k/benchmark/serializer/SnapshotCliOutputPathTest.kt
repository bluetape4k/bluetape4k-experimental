package io.bluetape4k.benchmark.serializer

import org.amshove.kluent.shouldBeGreaterThan
import org.amshove.kluent.shouldBeTrue
import java.io.File
import java.nio.file.Files
import kotlin.test.Test

class SnapshotCliOutputPathTest {

    @Test
    fun `serializer snapshot handles filename-only output path`() {
        val outputFile = File("serialized-size.json")

        try {
            writeSerializerMetricsSnapshot(outputFile.name)
            outputFile.exists().shouldBeTrue()
            outputFile.length() shouldBeGreaterThan 0
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

            Files.exists(output).shouldBeTrue()
            Files.size(output) shouldBeGreaterThan 0
        } finally {
            Files.deleteIfExists(output)
            Files.deleteIfExists(output.parent)
            Files.deleteIfExists(workDir)
        }
    }
}
