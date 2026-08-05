package `in`.dragonbra.javasteam.depotdownloader

import `in`.dragonbra.javasteam.types.ChunkData
import `in`.dragonbra.javasteam.util.Adler32
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.random.Random

/**
 * Verifies that [Util.validateSteam3FileChecksums] correctly identifies mismatched chunks,
 * and that parallelizing the per-chunk checksum work does not change the result.
 */
class UtilValidateSteam3FileChecksumsTest {

    companion object {
        const val CHUNK_SIZE = 64
    }

    private fun writeChunkedFile(tempDir: File, chunkCount: Int, chunkSize: Int = CHUNK_SIZE): Pair<File, List<ChunkData>> {
        val file = tempDir.resolve("data.bin")
        val random = Random(seed = 42)
        val chunks = mutableListOf<ChunkData>()

        file.outputStream().use { out ->
            for (i in 0 until chunkCount) {
                val bytes = ByteArray(chunkSize) { random.nextInt().toByte() }
                out.write(bytes)
                chunks.add(
                    ChunkData(
                        offset = (i * chunkSize).toLong(),
                        uncompressedLength = chunkSize,
                        checksum = Adler32.calculate(bytes),
                    )
                )
            }
        }

        return file to chunks
    }

    @Test
    fun `all chunks match returns empty list`(@TempDir tempDir: File) = runBlocking {
        val (file, chunks) = writeChunkedFile(tempDir, chunkCount = 20)

        FileSystem.SYSTEM.openReadOnly(file.toOkioPath()).use { handle ->
            val needed = Util.validateSteam3FileChecksums(handle, chunks)
            assertTrue(needed.isEmpty(), "Expected no chunks to need re-download, got ${needed.size}")
        }
    }

    @Test
    fun `mismatched chunks are returned`(@TempDir tempDir: File) = runBlocking {
        val (file, chunks) = writeChunkedFile(tempDir, chunkCount = 20)

        // Corrupt the checksum of a known subset so those chunks appear stale.
        val corruptedIndices = setOf(0, 5, 19)
        val chunksWithCorruption = chunks.mapIndexed { index, chunk ->
            if (index in corruptedIndices) chunk.copy(checksum = chunk.checksum xor 0x1) else chunk
        }

        FileSystem.SYSTEM.openReadOnly(file.toOkioPath()).use { handle ->
            val needed = Util.validateSteam3FileChecksums(handle, chunksWithCorruption)
            val neededOffsets = needed.map { it.offset }.toSet()
            val expectedOffsets = corruptedIndices.map { chunksWithCorruption[it].offset }.toSet()

            assertEquals(expectedOffsets, neededOffsets)
        }
    }

    @Test
    fun `result order matches input order`(@TempDir tempDir: File) = runBlocking {
        val (file, chunks) = writeChunkedFile(tempDir, chunkCount = 50)
        val chunksWithCorruption = chunks.map { it.copy(checksum = it.checksum xor 0x1) }

        FileSystem.SYSTEM.openReadOnly(file.toOkioPath()).use { handle ->
            val needed = Util.validateSteam3FileChecksums(handle, chunksWithCorruption)
            assertEquals(chunksWithCorruption.map { it.offset }, needed.map { it.offset })
        }
    }

    @Test
    fun `concurrency parameter does not change correctness`(@TempDir tempDir: File) = runBlocking {
        val (file, chunks) = writeChunkedFile(tempDir, chunkCount = 40)
        val corruptedIndices = setOf(1, 2, 3, 30)
        val chunksWithCorruption = chunks.mapIndexed { index, chunk ->
            if (index in corruptedIndices) chunk.copy(checksum = chunk.checksum xor 0x1) else chunk
        }

        FileSystem.SYSTEM.openReadOnly(file.toOkioPath()).use { handle ->
            val serial = Util.validateSteam3FileChecksums(handle, chunksWithCorruption, concurrency = 1)
            val parallel = Util.validateSteam3FileChecksums(handle, chunksWithCorruption, concurrency = 8)

            assertEquals(serial.map { it.offset }.toSet(), parallel.map { it.offset }.toSet())
        }
    }

    @Test
    fun `large chunk count completes and is correct`(@TempDir tempDir: File) = runBlocking {
        val (file, chunks) = writeChunkedFile(tempDir, chunkCount = 1000, chunkSize = 16)
        val corruptedIndices = (0 until 1000 step 37).toSet()
        val chunksWithCorruption = chunks.mapIndexed { index, chunk ->
            if (index in corruptedIndices) chunk.copy(checksum = chunk.checksum xor 0x1) else chunk
        }

        FileSystem.SYSTEM.openReadOnly(file.toOkioPath()).use { handle ->
            val needed = Util.validateSteam3FileChecksums(handle, chunksWithCorruption)
            val neededOffsets = needed.map { it.offset }.toSet()
            val expectedOffsets = corruptedIndices.map { chunksWithCorruption[it].offset }.toSet()

            assertEquals(expectedOffsets, neededOffsets)
        }
    }
}
