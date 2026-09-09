package me.rerere.rikkahub.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class StorageScanUtilsTest {
    @Test
    fun `toLocalFileOrNull supports file url absolute and relative paths`() {
        val filesDir = Files.createTempDirectory("filesDir").toFile()
        val uploadFile = File(filesDir, "upload/test.png")

        val absolutePath = StorageScanUtils.normalizePath(uploadFile)
        val fileUrl = "file:///" + absolutePath.replace('\\', '/')
        val contentUrl = "content://example.provider/upload/upload/test.png"

        val fromAbsolute = StorageScanUtils.toLocalFileOrNull(absolutePath, filesDir)
        val fromFileUrl = StorageScanUtils.toLocalFileOrNull(fileUrl, filesDir)
        val fromContentUrl = StorageScanUtils.toLocalFileOrNull(contentUrl, filesDir)
        val fromRelative = StorageScanUtils.toLocalFileOrNull("upload/test.png", filesDir)

        assertEquals(absolutePath, StorageScanUtils.normalizePath(fromAbsolute!!))
        assertEquals(absolutePath, StorageScanUtils.normalizePath(fromFileUrl!!))
        assertEquals(absolutePath, StorageScanUtils.normalizePath(fromContentUrl!!))
        assertEquals(absolutePath, StorageScanUtils.normalizePath(fromRelative!!))

        assertNull(StorageScanUtils.toLocalFileOrNull("data:image/png;base64,abc", filesDir))
        assertNull(StorageScanUtils.toLocalFileOrNull("https://example.com/a.png", filesDir))
    }

    @Test
    fun `toLocalFileOrNull rejects paths outside filesDir`() {
        val filesDir = Files.createTempDirectory("filesDir").toFile()
        val outside = Files.createTempDirectory("outside").toFile()
        val outsideFile = File(outside, "upload/test.png")

        val absolutePath = StorageScanUtils.normalizePath(outsideFile)
        val fileUrl = "file:///" + absolutePath.replace('\\', '/')

        assertNull(StorageScanUtils.toLocalFileOrNull(absolutePath, filesDir))
        assertNull(StorageScanUtils.toLocalFileOrNull(fileUrl, filesDir))
    }

    @Test
    fun `toExistingLocalFileOrNull rejects missing files`() {
        val filesDir = Files.createTempDirectory("filesDir").toFile()
        val missingFile = File(filesDir, "upload/missing.png")

        val absolutePath = StorageScanUtils.normalizePath(missingFile)
        val fileUrl = "file:///" + absolutePath.replace('\\', '/')

        assertNotNull(StorageScanUtils.toLocalFileOrNull(absolutePath, filesDir))
        assertNotNull(StorageScanUtils.toLocalFileOrNull(fileUrl, filesDir))
        assertNull(StorageScanUtils.toExistingLocalFileOrNull(absolutePath, filesDir))
        assertNull(StorageScanUtils.toExistingLocalFileOrNull(fileUrl, filesDir))

        requireNotNull(missingFile.parentFile).mkdirs()
        missingFile.writeBytes(byteArrayOf(1, 2, 3))

        assertEquals(absolutePath, StorageScanUtils.normalizePath(StorageScanUtils.toExistingLocalFileOrNull(absolutePath, filesDir)!!))
        assertEquals(absolutePath, StorageScanUtils.normalizePath(StorageScanUtils.toExistingLocalFileOrNull(fileUrl, filesDir)!!))
        assertNull(StorageScanUtils.toExistingLocalFileOrNull("upload/", filesDir))
    }

    @Test
    fun `extractReferencedFilePathsFromText finds both file urls and absolute paths`() {
        val filesDir = Files.createTempDirectory("filesDir").toFile()
        val uploadFile = File(filesDir, "upload/test.png")
        val absolutePath = StorageScanUtils.normalizePath(uploadFile)
        val fileUrl = "file:///" + absolutePath.replace('\\', '/')
        val contentUrl = "content://example.provider/upload/upload/test.png"

        val text = buildString {
            append("a=\"")
            append(absolutePath)
            append("\" ")
            append("b=\"")
            append(fileUrl)
            append("\" ")
            append("c=\"")
            append(contentUrl)
            append("\"")
        }

        val found = StorageScanUtils.extractReferencedFilePathsFromText(text, filesDir)
        assertTrue(found.contains(absolutePath))
        assertEquals(1, found.size)
    }
}
