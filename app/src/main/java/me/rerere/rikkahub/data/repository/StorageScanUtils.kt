package me.rerere.rikkahub.data.repository

import java.io.File
import java.net.URI

internal object StorageScanUtils {
    internal val fileUrlRegex: Regex = Regex("""file:[^\s"]+""")
    internal val contentUrlRegex: Regex = Regex("""content:[^\s"]+""")

    private val imageExtensions = setOf(
        "png", "jpg", "jpeg", "webp", "gif", "bmp", "heic", "heif", "avif",
    )

    private val relativePathPrefixes = listOf(
        "upload/",
        "images/",
        "avatars/",
        "custom_icons/",
        "skills/",
    )

    internal fun isImageExtension(extension: String): Boolean {
        val ext = extension.trim().lowercase()
        return ext.isNotBlank() && ext in imageExtensions
    }

    internal fun normalizePath(file: File): String {
        return runCatching { file.canonicalFile.absolutePath }.getOrElse { file.absolutePath }
    }

    internal fun isInChildOf(child: File, parent: File): Boolean {
        val parentPath = normalizePath(parent).let { p -> if (p.endsWith(File.separator)) p else p + File.separator }
        val childPath = normalizePath(child)
        return childPath.startsWith(parentPath)
    }

    internal fun toLocalFileOrNull(value: String, filesDir: File): File? {
        val raw = value.trim()
        if (raw.isBlank()) return null

        val file = when {
            raw.startsWith("file:") -> runCatching { File(URI(raw)) }
                .getOrNull()
                ?: runCatching { File(raw.removePrefix("file:")) }.getOrNull()

            raw.startsWith("content:") -> {
                val uri = runCatching { URI(raw) }.getOrNull() ?: return null
                val segments = uri.path
                    ?.split('/')
                    ?.asSequence()
                    ?.filter { it.isNotBlank() }
                    ?.toList()
                    .orEmpty()
                val root = segments.firstOrNull()
                if (root != "upload") return null
                val relativePath = segments.drop(1).joinToString(File.separator)
                File(filesDir, relativePath)
            }

            File(raw).isAbsolute -> File(raw)
            relativePathPrefixes.any { raw.startsWith(it) } -> File(filesDir, raw)
            else -> null
        } ?: return null

        return file.takeIf { isInChildOf(it, filesDir) }
    }

    internal fun toExistingLocalFileOrNull(value: String, filesDir: File): File? {
        val file = toLocalFileOrNull(value, filesDir) ?: return null
        return file.takeIf { it.exists() && it.isFile }
    }

    internal fun extractReferencedFilePathsFromText(text: String, filesDir: File): Set<String> {
        if (text.isBlank()) return emptySet()

        val result = HashSet<String>(256)

        val filesDirPath = normalizePath(filesDir).let { p -> if (p.endsWith(File.separator)) p else p + File.separator }
        val localPathRegex = Regex(Regex.escape(filesDirPath) + """[^\s"]+""")

        fileUrlRegex.findAll(text).forEach { match ->
            val file = toLocalFileOrNull(match.value, filesDir) ?: return@forEach
            result += normalizePath(file)
        }

        contentUrlRegex.findAll(text).forEach { match ->
            val file = toLocalFileOrNull(match.value, filesDir) ?: return@forEach
            result += normalizePath(file)
        }

        localPathRegex.findAll(text).forEach { match ->
            val file = File(match.value)
            if (isInChildOf(file, filesDir)) {
                result += normalizePath(file)
            }
        }

        return result
    }
}
