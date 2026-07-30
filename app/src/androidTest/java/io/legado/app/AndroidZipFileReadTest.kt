package io.legado.app

import android.os.ParcelFileDescriptor
import me.ag2s.epublib.util.zip.AndroidZipFile
import me.ag2s.epublib.util.zip.AndroidZipEntry
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Regression test for [AndroidZipFile.PartialInputStream.read].
 *
 * Background: [AndroidZipFile.PartialInputStream.read] must advance its
 * internal position by the number of bytes actually returned by the
 * underlying read, NOT by the number of bytes requested. If the underlying
 * read returns a short read (e.g. on signal interruption, network
 * filesystem, or other edge cases) and the position is incorrectly advanced
 * by the requested length, the next read starts at the wrong offset and
 * the bytes between the two reads are silently lost. Downstream,
 * [java.util.zip.InflaterInputStream] would then observe corrupted
 * compressed data and either throw [java.util.zip.ZipException] (app
 * crash) or produce garbage content (data loss).
 *
 * This test pins down the basic correctness of [AndroidZipFile] by round-
 * tripping a STORED (uncompressed) entry whose data is large enough to
 * span many reads at the default [java.io.BufferedInputStream] buffer
 * size (8 KiB). Any future regression in the position-advancement logic
 * will cause the round-tripped bytes to mismatch the original payload.
 */
class AndroidZipFileReadTest {

    private lateinit var zipFile: File

    @Before
    fun setUp() {
        zipFile = File.createTempFile("android-zipfile-test", ".zip")
        zipFile.deleteOnExit()
    }

    @After
    fun tearDown() {
        if (::zipFile.isInitialized && zipFile.exists()) {
            zipFile.delete()
        }
    }

    @Test
    fun readEntry_returnsExactBytes_forStoredEntry() {
        val payload = buildLargePayload(size = 64 * 1024)
        writeStoredZip(zipFile, ENTRY_NAME, payload)

        val pfd = ParcelFileDescriptor.open(zipFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val zip = AndroidZipFile(pfd, zipFile.name)
        try {
            val entry: AndroidZipEntry = zip.getEntry(ENTRY_NAME)
            assertNotNull("entry must be present", entry)

            zip.getInputStream(entry).use { input ->
                val readBack = input.readBytes()
                assertArrayEquals(
                    "round-tripped bytes must match original payload " +
                        "(regression guard for PartialInputStream position advancement)",
                    payload,
                    readBack
                )
                assertEquals(
                    "entry must report exact payload length",
                    payload.size.toLong(),
                    entry.size
                )
            }
        } finally {
            zip.close()
        }
    }

    @Test
    fun readEntry_returnsExactBytes_acrossMultipleBufferedReads() {
        // 256 KiB guarantees many read() calls even if BufferedInputStream
        // uses its default 8 KiB buffer, exercising the position-advance
        // path that previously used `filepos += len` instead of
        // `filepos += count`.
        val payload = buildLargePayload(size = 256 * 1024)
        writeStoredZip(zipFile, ENTRY_NAME, payload)

        val pfd = ParcelFileDescriptor.open(zipFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val zip = AndroidZipFile(pfd, zipFile.name)
        try {
            val entry = zip.getEntry(ENTRY_NAME)!!
            zip.getInputStream(entry).use { input ->
                val readBack = input.readBytes()
                assertArrayEquals(payload, readBack)
            }
        } finally {
            zip.close()
        }
    }

    private fun buildLargePayload(size: Int): ByteArray {
        val data = ByteArray(size)
        var seed = 1
        for (i in data.indices) {
            // Deterministic pseudo-random pattern; any byte dropped by a
            // short-read regression shows up as a mismatch at the first
            // affected index.
            seed = (seed * 1103515245 + 12345) and 0x7fffffff
            data[i] = (seed ushr 16).toByte()
        }
        return data
    }

    private fun writeStoredZip(target: File, entryName: String, payload: ByteArray) {
        FileOutputStream(target).use { fos ->
            ZipOutputStream(fos).use { zos ->
                val entry = ZipEntry(entryName)
                entry.method = ZipEntry.STORED
                entry.size = payload.size.toLong()
                entry.compressedSize = payload.size.toLong()
                entry.crc = crc32(payload)
                zos.putNextEntry(entry)
                zos.write(payload)
                zos.closeEntry()
            }
        }
    }

    private fun crc32(data: ByteArray): Long {
        val crc = java.util.zip.CRC32()
        crc.update(data)
        return crc.value
    }

    companion object {
        private const val ENTRY_NAME = "payload.bin"
    }
}
