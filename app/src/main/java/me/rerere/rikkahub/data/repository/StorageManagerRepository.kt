package me.rerere.rikkahub.data.repository

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.db.dao.ConversationDAO
import me.rerere.rikkahub.data.db.dao.GenMediaDAO
import me.rerere.rikkahub.data.db.dao.MessageNodeDAO
import me.rerere.rikkahub.data.files.FileFolders
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.utils.JsonInstant
import java.io.File
import java.time.YearMonth
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid

@Serializable
enum class StorageCategoryKey(val key: String) {
    IMAGES("images"),
    FILES("files"),
    CHAT_RECORDS("chat_records"),
    CACHE("cache"),
    HISTORY_FILES("history_files"),
    ;

    companion object {
        fun fromKeyOrNull(key: String): StorageCategoryKey? = entries.firstOrNull { it.key == key }
    }
}

@Serializable
data class StorageCategoryUsage(
    val category: StorageCategoryKey,
    val bytes: Long,
    val fileCount: Int,
)

@Serializable
data class CacheTopLevelUsage(
    val name: String,
    val bytes: Long,
    val fileCount: Int,
    val isDirectory: Boolean,
)

@Serializable
data class StorageOverview(
    val totalBytes: Long,
    val categories: List<StorageCategoryUsage>,
    val generatedAt: Long,
)

data class OrphanEntry(
    val absolutePath: String,
    val bytes: Long,
)

data class OrphanScanResult(
    val totalBytes: Long,
    val totalCount: Int,
    val preview: List<OrphanEntry>,
)

data class DeleteResult(
    val deletedCount: Int,
    val failedCount: Int,
    val deletedBytes: Long,
)

data class AssistantAttachmentStats(
    val imageCount: Int,
    val imageBytes: Long,
    val fileCount: Int,
    val fileBytes: Long,
)

data class AssistantImageEntry(
    val absolutePath: String,
    val bytes: Long,
    val lastModified: Long,
    val url: String,
)

data class AssistantFileEntry(
    val absolutePath: String,
    val bytes: Long,
    val lastModified: Long,
    val fileName: String,
    val mime: String,
)

data class ChatRecordsMonthEntry(
    val yearMonth: String,
    val conversationCount: Int,
)

internal object ChatRecordMonthRange {
    fun millisRange(
        yearMonth: String,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Pair<Long, Long>? {
        val ym = runCatching { YearMonth.parse(yearMonth) }.getOrNull() ?: return null
        val startMs = ym.atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endMs = ym.plusMonths(1).atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        return startMs to endMs
    }
}

class StorageManagerRepository(
    private val context: Context,
    private val settingsStore: SettingsStore,
    private val conversationDAO: ConversationDAO,
    private val conversationRepository: ConversationRepository,
    private val conversationDeletionCoordinator: ConversationDeletionCoordinator,
    private val messageNodeDAO: MessageNodeDAO,
    private val genMediaDAO: GenMediaDAO,
) {
    private companion object {
        private const val ATTACHMENT_LIST_CACHE_MAX_AGE_MS = 10 * 60_000L
        private const val OVERVIEW_CACHE_MAX_AGE_MS = 30 * 60_000L
    }

    private val chatAttachmentDirs: List<File> by lazy {
        listOf(File(context.filesDir, FileFolders.UPLOAD))
    }

    private fun isChatAttachmentFile(file: File): Boolean =
        chatAttachmentDirs.any { StorageScanUtils.isInChildOf(file, it) }

    private val overviewCache = TimedSuspendCache<StorageOverview>(
        maxAgeMs = OVERVIEW_CACHE_MAX_AGE_MS,
    )
    private val allImageEntriesCache = TimedSuspendCache<List<AssistantImageEntry>>(
        maxAgeMs = ATTACHMENT_LIST_CACHE_MAX_AGE_MS,
    )
    private val allFileEntriesCache = TimedSuspendCache<List<AssistantFileEntry>>(
        maxAgeMs = ATTACHMENT_LIST_CACHE_MAX_AGE_MS,
    )
    private val cacheTopLevelUsageCache = TimedSuspendCache<List<CacheTopLevelUsage>>(
        maxAgeMs = OVERVIEW_CACHE_MAX_AGE_MS,
    )
    private val assistantImageEntriesCaches = ConcurrentHashMap<Uuid, TimedSuspendCache<List<AssistantImageEntry>>>()
    private val assistantFileEntriesCaches = ConcurrentHashMap<Uuid, TimedSuspendCache<List<AssistantFileEntry>>>()

    private val overviewCacheFile = File(File(context.filesDir, "storage"), "overview_cache.json")

    private fun readDiskOverviewCache(): StorageOverview? = runCatching {
        val file = overviewCacheFile
        if (!file.exists() || !file.isFile) return null
        JsonInstant.decodeFromString<StorageOverview>(file.readText())
    }.getOrNull()

    private fun writeDiskOverviewCache(overview: StorageOverview) {
        runCatching {
            val file = overviewCacheFile
            file.parentFile?.mkdirs()
            val tmp = File.createTempFile(file.name, ".tmp", file.parentFile)
            tmp.writeText(JsonInstant.encodeToString(StorageOverview.serializer(), overview))
            if (file.exists()) file.delete()
            tmp.renameTo(file)
        }
    }

    fun peekOverviewCache(): StorageOverview? = overviewCache.peek()?.value

    fun peekDiskOverviewCache(): StorageOverview? {
        overviewCache.peek()?.value?.let { return it }
        return readDiskOverviewCache()
    }

    fun peekAllImageEntriesCache(): List<AssistantImageEntry>? = allImageEntriesCache.peek()?.value
    fun peekAllFileEntriesCache(): List<AssistantFileEntry>? = allFileEntriesCache.peek()?.value

    fun peekAssistantImageEntriesCache(assistantId: Uuid): List<AssistantImageEntry>? {
        return assistantImageEntriesCaches[assistantId]?.peek()?.value
    }

    fun peekAssistantFileEntriesCache(assistantId: Uuid): List<AssistantFileEntry>? {
        return assistantFileEntriesCaches[assistantId]?.peek()?.value
    }

    fun invalidateOverviewCache() {
        overviewCache.invalidate()
    }

    private fun invalidateCacheUsageCaches() {
        cacheTopLevelUsageCache.invalidate()
    }

    private fun invalidateImageEntriesCaches() {
        allImageEntriesCache.invalidate()
        assistantImageEntriesCaches.values.forEach { it.invalidate() }
    }

    private fun invalidateFileEntriesCaches() {
        allFileEntriesCache.invalidate()
        assistantFileEntriesCaches.values.forEach { it.invalidate() }
    }

    suspend fun loadOverview(forceRefresh: Boolean = false): StorageOverview {
        val overview = overviewCache.get(forceRefresh = forceRefresh) { computeOverview() }
        writeDiskOverviewCache(overview)
        return overview
    }

    private suspend fun computeOverview(): StorageOverview = withContext(Dispatchers.IO) {
        val settings = settingsStore.settingsFlow.value
        val referencedFilePaths = buildReferencedFilePathSet(settings = settings)

        val uploadDir = File(context.filesDir, FileFolders.UPLOAD)
        val imagesDir = File(context.filesDir, "images")
        val avatarsDir = File(context.filesDir, "avatars")
        val customIconsDir = File(context.filesDir, "custom_icons")
        val skillsDir = File(context.filesDir, FileFolders.SKILLS)

        val conversationCount = async { runCatching { conversationDAO.countAll() }.getOrNull() ?: 0 }
        val dbUsage = async { countDatabaseUsage() }
        val cacheUsage = async { countDirUsage(context.cacheDir) }
        val uploadUsage = async {
            countManagedFilesInDir(uploadDir, referencedFilePaths, treatAllAsImages = false)
        }
        val imagesUsage = async {
            countManagedFilesInDir(imagesDir, referencedFilePaths, treatAllAsImages = true)
        }
        val avatarsUsage = async {
            countManagedFilesInDir(avatarsDir, referencedFilePaths, treatAllAsImages = true)
        }
        val customIconsUsage = async {
            countManagedFilesInDir(customIconsDir, referencedFilePaths, treatAllAsImages = true)
        }
        val skillsUsage = async { countSkillsUsage(skillsDir) }

        val conversationCountVal = conversationCount.await()
        val dbUsageVal = dbUsage.await()
        val cacheUsageVal = cacheUsage.await()
        val uploadUsageVal = uploadUsage.await()
        val imagesUsageVal = imagesUsage.await()
        val avatarsUsageVal = avatarsUsage.await()
        val customIconsUsageVal = customIconsUsage.await()
        val skillsUsageVal = skillsUsage.await()

        val imagesBytes = uploadUsageVal.images.bytes +
            imagesUsageVal.images.bytes +
            avatarsUsageVal.images.bytes +
            customIconsUsageVal.images.bytes
        val imagesCount = uploadUsageVal.images.count +
            imagesUsageVal.images.count +
            avatarsUsageVal.images.count +
            customIconsUsageVal.images.count

        val filesBytes = uploadUsageVal.files.bytes + imagesUsageVal.files.bytes
        val filesCount = uploadUsageVal.files.count + imagesUsageVal.files.count

        val historyBytes = uploadUsageVal.history.bytes +
            imagesUsageVal.history.bytes +
            avatarsUsageVal.history.bytes +
            customIconsUsageVal.history.bytes +
            skillsUsageVal.history.bytes
        val historyCount = uploadUsageVal.history.count +
            imagesUsageVal.history.count +
            avatarsUsageVal.history.count +
            customIconsUsageVal.history.count +
            skillsUsageVal.history.count

        val categories = listOf(
            StorageCategoryUsage(StorageCategoryKey.IMAGES, imagesBytes, imagesCount),
            StorageCategoryUsage(StorageCategoryKey.FILES, filesBytes, filesCount),
            StorageCategoryUsage(StorageCategoryKey.CHAT_RECORDS, dbUsageVal.bytes, conversationCountVal),
            StorageCategoryUsage(StorageCategoryKey.CACHE, cacheUsageVal.bytes, cacheUsageVal.count),
            StorageCategoryUsage(StorageCategoryKey.HISTORY_FILES, historyBytes, historyCount),
        )

        StorageOverview(
            totalBytes = categories.sumOf { it.bytes },
            categories = categories,
            generatedAt = System.currentTimeMillis(),
        )
    }

    suspend fun getCacheUsage(forceRefresh: Boolean = false): StorageCategoryUsage {
        val topLevel = getCacheTopLevelUsage(forceRefresh = forceRefresh)
        return StorageCategoryUsage(
            category = StorageCategoryKey.CACHE,
            bytes = topLevel.sumOf { it.bytes },
            fileCount = topLevel.sumOf { it.fileCount },
        )
    }

    suspend fun getCacheTopLevelUsage(forceRefresh: Boolean = false): List<CacheTopLevelUsage> {
        return cacheTopLevelUsageCache.get(forceRefresh = forceRefresh) { computeCacheTopLevelUsage() }
    }

    private suspend fun computeCacheTopLevelUsage(): List<CacheTopLevelUsage> = withContext(Dispatchers.IO) {
        context.cacheDir
            .listFiles()
            .orEmpty()
            .asSequence()
            .filter { it.exists() }
            .map { entry ->
                val usage = countDirUsage(entry)
                CacheTopLevelUsage(
                    name = entry.name.ifBlank { entry.absolutePath },
                    bytes = usage.bytes,
                    fileCount = usage.count,
                    isDirectory = entry.isDirectory,
                )
            }
            .sortedWith(
                compareByDescending<CacheTopLevelUsage> { it.bytes }
                    .thenBy { it.name.lowercase() }
            )
            .toList()
    }

    suspend fun getChatRecordsUsage(): StorageCategoryUsage = withContext(Dispatchers.IO) {
        val usage = countDatabaseUsage()
        val conversationCount = runCatching { conversationDAO.countAll() }.getOrNull() ?: 0
        StorageCategoryUsage(
            category = StorageCategoryKey.CHAT_RECORDS,
            bytes = usage.bytes,
            fileCount = conversationCount,
        )
    }

    suspend fun getChatRecordsMonthEntries(assistantId: Uuid?): List<ChatRecordsMonthEntry> = withContext(Dispatchers.IO) {
        val rows = if (assistantId == null) {
            conversationDAO.getConversationMonthCounts()
        } else {
            conversationDAO.getConversationMonthCountsOfAssistant(assistantId.toString())
        }
        rows.map { ChatRecordsMonthEntry(yearMonth = it.yearMonth, conversationCount = it.count) }
    }

    suspend fun getChatRecordConversationsByYearMonth(
        assistantId: Uuid?,
        yearMonth: String,
    ): List<LightConversationEntity> = withContext(Dispatchers.IO) {
        val range = ChatRecordMonthRange.millisRange(yearMonth) ?: return@withContext emptyList()
        val (startMs, endMs) = range
        if (assistantId == null) {
            conversationDAO.getLightConversationsByUpdateAtRange(startMs = startMs, endMs = endMs)
        } else {
            conversationDAO.getLightConversationsOfAssistantByUpdateAtRange(
                assistantId = assistantId.toString(),
                startMs = startMs,
                endMs = endMs,
            )
        }
    }

    suspend fun clearChatRecordsByYearMonths(
        assistantId: Uuid?,
        yearMonths: Set<String>,
    ): DeleteResult = withContext(Dispatchers.IO) {
        val ids = LinkedHashSet<String>(yearMonths.size * 8)
        yearMonths.forEach { yearMonth ->
            val range = ChatRecordMonthRange.millisRange(yearMonth) ?: return@forEach
            val (startMs, endMs) = range
            val monthIds = if (assistantId == null) {
                conversationDAO.getConversationIdsByUpdateAtRange(startMs = startMs, endMs = endMs)
            } else {
                conversationDAO.getConversationIdsOfAssistantByUpdateAtRange(
                    assistantId = assistantId.toString(),
                    startMs = startMs,
                    endMs = endMs,
                )
            }
            ids.addAll(monthIds)
        }
        deleteConversationsByIds(ids)
    }

    suspend fun clearChatRecordsByConversationIds(
        conversationIds: Set<String>,
    ): DeleteResult = withContext(Dispatchers.IO) {
        deleteConversationsByIds(conversationIds)
    }

    private suspend fun deleteConversationsByIds(ids: Set<String>): DeleteResult {
        if (ids.isEmpty()) return DeleteResult(0, 0, 0L)
        var deletedCount = 0
        var failedCount = 0
        ids.forEach { conversationId ->
            val ok = runCatching {
                conversationDeletionCoordinator.deleteConversationById(
                    Uuid.parse(conversationId),
                    deleteFiles = false,
                )
            }.isSuccess
            if (ok) deletedCount += 1 else failedCount += 1
        }
        invalidateOverviewCache()
        invalidateImageEntriesCaches()
        invalidateFileEntriesCaches()
        return DeleteResult(deletedCount = deletedCount, failedCount = failedCount, deletedBytes = 0L)
    }

    suspend fun getAssistantAttachmentStats(assistantId: Uuid): AssistantAttachmentStats = withContext(Dispatchers.IO) {
        val images = collectAssistantImageFiles(assistantId)
        val files = collectAssistantFileEntries(assistantId).map { File(it.absolutePath) }
        AssistantAttachmentStats(
            imageCount = images.size,
            imageBytes = images.sumOf { it.lengthSafe() },
            fileCount = files.size,
            fileBytes = files.sumOf { it.lengthSafe() },
        )
    }

    suspend fun clearAssistantChatAttachments(
        assistantId: Uuid,
        clearImages: Boolean,
        clearFiles: Boolean,
    ): DeleteResult = withContext(Dispatchers.IO) {
        val imageUrls = if (clearImages) collectAssistantImageUrls(assistantId) else emptySet()
        val fileUrls = if (clearFiles) collectAssistantFileUrls(assistantId) else emptySet()
        val targetFiles = (imageUrls + fileUrls)
            .asSequence()
            .mapNotNull { StorageScanUtils.toExistingLocalFileOrNull(it, context.filesDir) }
            .distinctBy { StorageScanUtils.normalizePath(it) }
            .filter { file -> isChatAttachmentFile(file) }
            .toList()
        val result = deleteFiles(targetFiles)
        invalidateOverviewCache()
        if (clearImages) invalidateImageEntriesCaches()
        if (clearFiles) invalidateFileEntriesCaches()
        result
    }

    suspend fun getAssistantConversationCount(assistantId: Uuid): Int = withContext(Dispatchers.IO) {
        runCatching { conversationDAO.getConversationCountOfAssistant(assistantId.toString()) }
            .getOrNull()
            ?: 0
    }

    private fun getAssistantImageEntriesCache(assistantId: Uuid): TimedSuspendCache<List<AssistantImageEntry>> {
        return assistantImageEntriesCaches.computeIfAbsent(assistantId) {
            TimedSuspendCache(maxAgeMs = ATTACHMENT_LIST_CACHE_MAX_AGE_MS)
        }
    }

    suspend fun getAssistantImageEntries(
        assistantId: Uuid,
        forceRefresh: Boolean = false,
    ): List<AssistantImageEntry> {
        return getAssistantImageEntriesCache(assistantId)
            .get(forceRefresh = forceRefresh) { mapImageEntries(collectAssistantImageFiles(assistantId)) }
    }

    suspend fun getAllImageEntries(forceRefresh: Boolean = false): List<AssistantImageEntry> {
        return allImageEntriesCache.get(forceRefresh = forceRefresh) {
            mapImageEntries(collectImageFiles(paths = collectReferencedPathsFromMessages(assistantId = null)))
        }
    }

    private fun getAssistantFileEntriesCache(assistantId: Uuid): TimedSuspendCache<List<AssistantFileEntry>> {
        return assistantFileEntriesCaches.computeIfAbsent(assistantId) {
            TimedSuspendCache(maxAgeMs = ATTACHMENT_LIST_CACHE_MAX_AGE_MS)
        }
    }

    suspend fun getAssistantFileEntries(
        assistantId: Uuid,
        forceRefresh: Boolean = false,
    ): List<AssistantFileEntry> {
        return getAssistantFileEntriesCache(assistantId)
            .get(forceRefresh = forceRefresh) { collectAssistantFileEntries(assistantId) }
    }

    suspend fun getAllFileEntries(forceRefresh: Boolean = false): List<AssistantFileEntry> {
        return allFileEntriesCache.get(forceRefresh = forceRefresh) {
            collectFileEntries(collectReferencedPathsFromMessages(assistantId = null))
        }
    }

    suspend fun deleteAssistantImageEntries(absolutePaths: List<String>): DeleteResult = withContext(Dispatchers.IO) {
        val files = absolutePaths
            .asSequence()
            .map { File(it) }
            .filter { file -> isChatAttachmentFile(file) }
            .distinctBy { StorageScanUtils.normalizePath(it) }
            .toList()
        val result = deleteFiles(files)
        invalidateOverviewCache()
        invalidateImageEntriesCaches()
        result
    }

    suspend fun deleteAssistantFileEntries(absolutePaths: List<String>): DeleteResult = withContext(Dispatchers.IO) {
        val files = absolutePaths
            .asSequence()
            .map { File(it) }
            .filter { file -> isChatAttachmentFile(file) }
            .distinctBy { StorageScanUtils.normalizePath(it) }
            .toList()
        val result = deleteFiles(files)
        invalidateOverviewCache()
        invalidateFileEntriesCaches()
        result
    }

    suspend fun scanOrphans(previewLimit: Int = 40): OrphanScanResult = withContext(Dispatchers.IO) {
        val settings = settingsStore.settingsFlow.value
        val referencedFilePaths = buildReferencedFilePathSet(settings = settings)
        val preview = mutableListOf<OrphanEntry>()
        var totalBytes = 0L
        var totalCount = 0

        fun addOrphan(file: File) {
            val bytes = file.lengthSafe()
            totalBytes += bytes
            totalCount += 1
            if (preview.size < previewLimit) {
                preview += OrphanEntry(absolutePath = file.absolutePath, bytes = bytes)
            }
        }

        orphanRoots().forEach { root ->
            if (!root.exists()) return@forEach
            root.walkTopDown()
                .filter { it.isFile }
                .forEach { file ->
                    val normalized = StorageScanUtils.normalizePath(file)
                    if (normalized !in referencedFilePaths) {
                        addOrphan(file)
                    }
                }
        }

        val skillsDir = File(context.filesDir, FileFolders.SKILLS)
        if (skillsDir.exists()) {
            skillsDir.listFiles()
                ?.filter { it.isFile }
                ?.forEach { file -> addOrphan(file) }
        }

        OrphanScanResult(
            totalBytes = totalBytes,
            totalCount = totalCount,
            preview = preview,
        )
    }

    suspend fun clearAllOrphans(): DeleteResult = withContext(Dispatchers.IO) {
        val settings = settingsStore.settingsFlow.value
        val referencedFilePaths = buildReferencedFilePathSet(settings = settings)
        var deletedCount = 0
        var failedCount = 0
        var deletedBytes = 0L

        fun deleteFile(file: File) {
            val bytes = file.lengthSafe()
            val ok = runCatching { file.delete() }.getOrNull() == true
            if (ok) {
                deletedCount += 1
                deletedBytes += bytes
            } else {
                failedCount += 1
            }
        }

        orphanRoots().forEach { root ->
            if (!root.exists()) return@forEach
            root.walkTopDown()
                .filter { it.isFile }
                .forEach { file ->
                    val normalized = StorageScanUtils.normalizePath(file)
                    if (normalized !in referencedFilePaths) {
                        deleteFile(file)
                    }
                }
        }

        val skillsDir = File(context.filesDir, FileFolders.SKILLS)
        if (skillsDir.exists()) {
            skillsDir.listFiles()
                ?.filter { it.isFile }
                ?.forEach { file -> deleteFile(file) }
        }

        val result = DeleteResult(
            deletedCount = deletedCount,
            failedCount = failedCount,
            deletedBytes = deletedBytes,
        )
        invalidateOverviewCache()
        result
    }

    suspend fun clearCache(): DeleteResult = withContext(Dispatchers.IO) {
        val root = context.cacheDir
        val targets = root.listFiles().orEmpty().filter { it.exists() }
        val result = deleteFilesOrDirs(targets)
        invalidateOverviewCache()
        invalidateCacheUsageCaches()
        result
    }

    private fun orphanRoots(): List<File> = listOf(
        File(context.filesDir, FileFolders.UPLOAD),
        File(context.filesDir, "images"),
        File(context.filesDir, "avatars"),
        File(context.filesDir, "custom_icons"),
    )

    private data class Usage(val count: Int, val bytes: Long)

    private data class SplitUsage(
        val images: Usage,
        val files: Usage,
        val history: Usage,
    )

    private data class SkillSplitUsage(
        val files: Usage,
        val history: Usage,
    )

    private data class FileCandidate(
        val url: String,
        val fileName: String,
        val mime: String,
    )

    private fun countDatabaseUsage(): Usage {
        val dbFile = context.getDatabasePath("rikka_hub")
        val walFile = File(dbFile.parentFile, "rikka_hub-wal")
        val shmFile = File(dbFile.parentFile, "rikka_hub-shm")
        val files = listOf(dbFile, walFile, shmFile).filter { it.exists() && it.isFile }
        return Usage(
            count = files.size,
            bytes = files.sumOf { it.lengthSafe() },
        )
    }

    private fun countDirUsage(root: File): Usage {
        if (!root.exists()) return Usage(count = 0, bytes = 0)
        var count = 0
        var bytes = 0L
        root.walkTopDown()
            .filter { it.isFile }
            .forEach { file ->
                count += 1
                bytes += file.lengthSafe()
            }
        return Usage(count = count, bytes = bytes)
    }

    private fun countManagedFilesInDir(
        rootDir: File,
        referencedFilePaths: Set<String>,
        treatAllAsImages: Boolean,
    ): SplitUsage {
        if (!rootDir.exists()) {
            return SplitUsage(images = Usage(0, 0), files = Usage(0, 0), history = Usage(0, 0))
        }

        var imagesCount = 0
        var imagesBytes = 0L
        var filesCount = 0
        var filesBytes = 0L
        var historyCount = 0
        var historyBytes = 0L

        rootDir.walkTopDown()
            .filter { it.isFile }
            .forEach { file ->
                val bytes = file.lengthSafe()
                val referenced = StorageScanUtils.normalizePath(file) in referencedFilePaths
                if (!referenced) {
                    historyCount += 1
                    historyBytes += bytes
                    return@forEach
                }

                val isImage = treatAllAsImages || StorageScanUtils.isImageExtension(file.extension)
                if (isImage) {
                    imagesCount += 1
                    imagesBytes += bytes
                } else {
                    filesCount += 1
                    filesBytes += bytes
                }
            }

        return SplitUsage(
            images = Usage(imagesCount, imagesBytes),
            files = Usage(filesCount, filesBytes),
            history = Usage(historyCount, historyBytes),
        )
    }

    private fun countSkillsUsage(skillsDir: File): SkillSplitUsage {
        if (!skillsDir.exists()) return SkillSplitUsage(files = Usage(0, 0), history = Usage(0, 0))

        var filesCount = 0
        var filesBytes = 0L
        var historyCount = 0
        var historyBytes = 0L

        skillsDir.listFiles().orEmpty().forEach { entry ->
            if (!entry.exists()) return@forEach
            val usage = countDirUsage(entry)
            if (entry.isDirectory) {
                filesCount += usage.count
                filesBytes += usage.bytes
            } else {
                historyCount += usage.count
                historyBytes += usage.bytes
            }
        }

        return SkillSplitUsage(
            files = Usage(filesCount, filesBytes),
            history = Usage(historyCount, historyBytes),
        )
    }

    private suspend fun collectReferencedPathsFromMessages(assistantId: Uuid?): Set<String> {
        val rows = try {
            if (assistantId == null) {
                messageNodeDAO.getMessagesForScan()
            } else {
                messageNodeDAO.getMessagesOfAssistantForScan(assistantId.toString())
            }
        } catch (_: Exception) {
            emptyList()
        }
        val referenced = HashSet<String>(8_192)
        rows.forEach { json ->
            referenced += StorageScanUtils.extractReferencedFilePathsFromText(json, context.filesDir)
        }
        return referenced
    }

    private suspend fun buildReferencedFilePathSet(settings: Settings): Set<String> {
        val referenced = HashSet<String>(8_192)

        fun addUrl(url: String?) {
            if (url.isNullOrBlank()) return
            val file = StorageScanUtils.toLocalFileOrNull(url, context.filesDir) ?: return
            referenced += StorageScanUtils.normalizePath(file)
        }

        fun addAvatar(avatar: Avatar?) {
            when (avatar) {
                is Avatar.Image -> addUrl(avatar.url)
                else -> Unit
            }
        }

        addAvatar(settings.displaySetting.userAvatar)
        settings.assistants.forEach { assistant ->
            addAvatar(assistant.avatar)
            addUrl(assistant.background)
        }

        val mediaList = try {
            genMediaDAO.getAllMedia()
        } catch (_: Exception) {
            emptyList()
        }
        mediaList.forEach { media ->
            val path = media.path.trim()
            if (path.isBlank()) return@forEach
            val file = File(context.filesDir, path)
            referenced += StorageScanUtils.normalizePath(file)
        }

        referenced += collectReferencedPathsFromMessages(assistantId = null)
        return referenced
    }

    private suspend fun collectAssistantImageUrls(assistantId: Uuid): Set<String> {
        val conversations = conversationRepository.getConversationsOfAssistant(assistantId).first()
        val urls = LinkedHashSet<String>()
        conversations.forEach { conversation ->
            val full = if (conversation.messageNodes.isEmpty()) {
                conversationRepository.getConversationById(conversation.id) ?: conversation
            } else {
                conversation
            }
            full.messageNodes.forEach { node ->
                node.messages.forEach { message ->
                    message.parts.forEach { part ->
                        when (part) {
                            is UIMessagePart.Image -> urls += part.url
                            is UIMessagePart.Document -> if (part.mime.startsWith("image/")) urls += part.url
                            else -> Unit
                        }
                    }
                }
            }
        }
        return urls
    }

    private suspend fun collectAssistantFileUrls(assistantId: Uuid): Set<String> {
        return collectAssistantFileCandidates(assistantId).map { it.url }.toSet()
    }

    private suspend fun collectAssistantImageFiles(assistantId: Uuid): List<File> {
        return collectImageFiles(collectReferencedPathsFromMessages(assistantId))
    }

    private fun collectImageFiles(paths: Set<String>): List<File> {
        return paths
            .asSequence()
            .map { File(it) }
            .filter { it.exists() && it.isFile }
            .distinctBy { StorageScanUtils.normalizePath(it) }
            .filter { file ->
                isChatAttachmentFile(file) && StorageScanUtils.isImageExtension(file.extension)
            }
            .toList()
    }

    private fun mapImageEntries(files: List<File>): List<AssistantImageEntry> {
        return files
            .map { file ->
                val path = StorageScanUtils.normalizePath(file)
                AssistantImageEntry(
                    absolutePath = path,
                    bytes = file.lengthSafe(),
                    lastModified = runCatching { file.lastModified() }.getOrNull() ?: 0L,
                    url = "file://$path",
                )
            }
            .sortedByDescending { it.lastModified }
    }

    private suspend fun collectAssistantFileCandidates(assistantId: Uuid): List<FileCandidate> {
        val conversations = conversationRepository.getConversationsOfAssistant(assistantId).first()
        val candidates = ArrayList<FileCandidate>(64)
        conversations.forEach { conversation ->
            val full = if (conversation.messageNodes.isEmpty()) {
                conversationRepository.getConversationById(conversation.id) ?: conversation
            } else {
                conversation
            }
            full.messageNodes.forEach { node ->
                node.messages.forEach { message ->
                    message.parts.forEach { part ->
                        when (part) {
                            is UIMessagePart.Document -> {
                                if (!part.mime.startsWith("image/")) {
                                    candidates += FileCandidate(part.url, part.fileName, part.mime)
                                }
                            }
                            is UIMessagePart.Video -> candidates += FileCandidate(part.url, "", "video/*")
                            is UIMessagePart.Audio -> candidates += FileCandidate(part.url, "", "audio/*")
                            else -> Unit
                        }
                    }
                }
            }
        }
        return candidates
    }

    private suspend fun collectAssistantFileEntries(assistantId: Uuid): List<AssistantFileEntry> {
        return mergeFileCandidates(collectAssistantFileCandidates(assistantId))
    }

    private fun collectFileEntries(paths: Set<String>): List<AssistantFileEntry> {
        val byPath = LinkedHashMap<String, AssistantFileEntry>()
        paths.forEach { pathValue ->
            val file = File(pathValue)
            if (!file.exists() || !file.isFile) return@forEach
            if (!isChatAttachmentFile(file)) return@forEach
            if (StorageScanUtils.isImageExtension(file.extension)) return@forEach
            val normalizedPath = StorageScanUtils.normalizePath(file)
            byPath[normalizedPath] = AssistantFileEntry(
                absolutePath = normalizedPath,
                bytes = file.lengthSafe(),
                lastModified = runCatching { file.lastModified() }.getOrNull() ?: 0L,
                fileName = File(normalizedPath).name,
                mime = "",
            )
        }
        return byPath.values.sortedByDescending { it.lastModified }
    }

    private fun mergeFileCandidates(candidates: List<FileCandidate>): List<AssistantFileEntry> {
        val byPath = LinkedHashMap<String, AssistantFileEntry>()
        candidates.forEach { candidate ->
            val file = StorageScanUtils.toExistingLocalFileOrNull(candidate.url, context.filesDir) ?: return@forEach
            if (!isChatAttachmentFile(file)) return@forEach
            val normalizedPath = StorageScanUtils.normalizePath(file)
            val bytes = file.lengthSafe()
            val lastModified = runCatching { file.lastModified() }.getOrNull() ?: 0L
            val fallbackName = File(normalizedPath).name
            val desiredName = candidate.fileName.trim().ifBlank { fallbackName }
            val desiredMime = candidate.mime.trim()
            val existing = byPath[normalizedPath]
            val merged = if (existing == null) {
                AssistantFileEntry(
                    absolutePath = normalizedPath,
                    bytes = bytes,
                    lastModified = lastModified,
                    fileName = desiredName,
                    mime = desiredMime,
                )
            } else {
                val existingFallbackName = File(existing.absolutePath).name
                val mergedName = when {
                    existing.fileName.isBlank() -> desiredName
                    desiredName.isBlank() -> existing.fileName
                    existing.fileName == existingFallbackName && desiredName != existingFallbackName -> desiredName
                    else -> existing.fileName
                }
                val mergedMime = if (existing.mime.isBlank()) desiredMime else existing.mime
                existing.copy(
                    bytes = bytes,
                    lastModified = maxOf(existing.lastModified, lastModified),
                    fileName = mergedName,
                    mime = mergedMime,
                )
            }
            byPath[normalizedPath] = merged
        }
        return byPath.values.sortedByDescending { it.lastModified }
    }

    private fun File.lengthSafe(): Long = runCatching { length() }.getOrNull() ?: 0L

    private fun deleteFiles(files: List<File>): DeleteResult {
        var deletedCount = 0
        var failedCount = 0
        var deletedBytes = 0L
        files.forEach { file ->
            val bytes = file.lengthSafe()
            val ok = runCatching { file.delete() }.getOrNull() == true
            if (ok) {
                deletedCount += 1
                deletedBytes += bytes
            } else {
                failedCount += 1
            }
        }
        return DeleteResult(
            deletedCount = deletedCount,
            failedCount = failedCount,
            deletedBytes = deletedBytes,
        )
    }

    private fun deleteFilesOrDirs(entries: List<File>): DeleteResult {
        var deletedCount = 0
        var failedCount = 0
        var deletedBytes = 0L
        entries.forEach { entry ->
            val usage = countDirUsage(entry)
            val ok = runCatching {
                if (entry.isDirectory) entry.deleteRecursively() else entry.delete()
            }.getOrNull() == true
            if (ok) {
                deletedCount += usage.count
                deletedBytes += usage.bytes
            } else {
                failedCount += 1
            }
        }
        return DeleteResult(
            deletedCount = deletedCount,
            failedCount = failedCount,
            deletedBytes = deletedBytes,
        )
    }
}
