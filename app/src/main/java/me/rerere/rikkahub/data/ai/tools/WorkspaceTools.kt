package me.rerere.rikkahub.data.ai.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.DiffMetadata
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.toMetadata
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.utils.generateUnifiedDiff
import me.rerere.workspace.WorkspaceCommandResult
import me.rerere.workspace.WorkspaceFileEntry
import me.rerere.workspace.WorkspaceManager
import org.koin.java.KoinJavaComponent.getKoin
import java.io.ByteArrayOutputStream
import java.io.File

private const val SHELL_TIMEOUT_MAX_SECONDS = 600L
private const val MAX_READ_FILE_BYTES = 8L * 1024 * 1024

// 只读检索工具的查询超时(ls/grep 要遍历目录树, 比 shell 默认值宽松)
private const val WORKSPACE_QUERY_TIMEOUT_MS = 60_000L

// workspace_read_file 的行窗口: 默认 250 行, 上限 400 行
private const val READ_FILE_DEFAULT_LINES = 250
private const val READ_FILE_MAX_LINES = 400

// workspace_ls: 默认只看这一层, 最多 5 层, 最多 500 条
private const val LS_DEFAULT_DEPTH = 1
private const val LS_MAX_DEPTH = 5
private const val LS_MAX_ENTRIES = 500

// workspace_grep: 默认字面量 + 忽略大小写, 上下各 2 行, 最多 20 条命中
private const val GREP_DEFAULT_MAX_HITS = 20
private const val GREP_MAX_HITS = 50
private const val GREP_DEFAULT_CONTEXT_LINES = 2
private const val GREP_MAX_CONTEXT_LINES = 5

val WorkspaceToolDefaultApprovals: Map<String, Boolean> = mapOf(
    "workspace_read_file" to false,
    "workspace_ls" to false,
    "workspace_grep" to false,
    "workspace_write_file" to false,
    "workspace_edit_file" to false,
    "workspace_shell" to true,
)

fun resolveWorkspaceToolApproval(name: String, overrides: Map<String, Boolean>): Boolean =
    overrides[name] ?: WorkspaceToolDefaultApprovals[name] ?: false

suspend fun createWorkspaceTools(
    workspaceId: String?,
    workspaceRepository: WorkspaceRepository,
    cwd: String? = null,
): List<Tool> {
    if (workspaceId.isNullOrBlank()) return emptyList()
    val approvalOverrides = workspaceRepository.getById(workspaceId)?.toolApprovalOverrides().orEmpty()
    fun needsApproval(name: String) = resolveWorkspaceToolApproval(name, approvalOverrides)

    val shellCwd = cwd?.removePrefix("/workspace/")?.removePrefix("/workspace")

    return listOf(
        createReadFileTool(workspaceId, ::needsApproval, workspaceRepository),
        createListFilesTool(workspaceId, ::needsApproval, workspaceRepository),
        createGrepTool(workspaceId, ::needsApproval, workspaceRepository),
        createWriteFileTool(workspaceId, ::needsApproval, workspaceRepository),
        createEditFileTool(workspaceId, ::needsApproval, workspaceRepository),
        createShellTool(workspaceId, ::needsApproval, workspaceRepository, shellCwd),
    )
}

private val IMAGE_EXTENSIONS = setOf(
    "png", "jpg", "jpeg", "gif", "webp", "bmp", "svg", "heic", "heif", "avif", "ico",
)

private fun String.isImagePath(): Boolean =
    substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS

private fun createReadFileTool(
    workspaceId: String,
    needsApproval: (String) -> Boolean,
    workspaceRepository: WorkspaceRepository,
) = Tool(
    name = "workspace_read_file",
    description = """
        Read a file using the assistant's bound workspace Rootfs. Paths must be absolute inside Rootfs.
        Use /workspace for the workspace files area.
        Text files are returned as a line window: offset is the 1-based first line, limit is how many lines.
        Defaults to the first $READ_FILE_DEFAULT_LINES lines; limit is capped at $READ_FILE_MAX_LINES lines.
        The result carries offset, limit, totalLines and truncated. When truncated is true there is more content:
        call again with offset = nextOffset.
        Supports image files (png, jpg, jpeg, gif, webp, bmp, svg, heic, heif, avif, ico); offset/limit do not apply to images.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                putPathProperty(required = true)
                put("offset", buildJsonObject {
                    put("type", "integer")
                    put("description", "1-based first line to return. Defaults to 1. Ignored for images.")
                })
                put("limit", buildJsonObject {
                    put(
                        "type", "integer"
                    )
                    put(
                        "description",
                        "How many lines to return. Defaults to $READ_FILE_DEFAULT_LINES, max $READ_FILE_MAX_LINES. Ignored for images."
                    )
                })
            },
            required = listOf("path"),
        )
    },
    needsApproval = { needsApproval("workspace_read_file") },
    execute = {
        val params = it.jsonObject
        val path = params.absolutePath("path")
        if (path.isImagePath()) {
            workspaceRepository.readImageInRootfs(workspaceId, path)
        } else {
            val requestedOffset = (params.int("offset") ?: 1).coerceAtLeast(1)
            val limit = (params.int("limit") ?: READ_FILE_DEFAULT_LINES)
                .coerceIn(1, READ_FILE_MAX_LINES)
            val lines = workspaceRepository.readTextInRootfs(workspaceId, path).toWorkspaceLines()
            val totalLines = lines.size
            val fromIndex = (requestedOffset - 1).coerceAtMost(totalLines)
            val toIndex = (fromIndex + limit).coerceAtMost(totalLines)
            // offset 超过文件末尾时不要夹到末尾: 保留请求值、返回空窗口、并把这件事说清楚,
            // 否则回显的 offset 会变成 totalLines + 1, 调用方以为读到了别的地方。
            val beyondEnd = requestedOffset > totalLines
            val truncated = !beyondEnd && toIndex < totalLines
            listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("path", path)
                        put("text", if (beyondEnd) "" else lines.subList(fromIndex, toIndex).joinToString("\n"))
                        put("offset", requestedOffset)
                        put("limit", limit)
                        put("totalLines", totalLines)
                        put("truncated", truncated)
                        if (truncated) {
                            put("nextOffset", toIndex + 1)
                            put(
                                "hint",
                                "Truncated. Call workspace_read_file again with offset=${toIndex + 1} to continue."
                            )
                        } else if (beyondEnd) {
                            put(
                                "hint",
                                "offset=$requestedOffset is past the end of the file (totalLines=$totalLines); text is empty."
                            )
                        }
                    }.toString()
                )
            )
        }
    },
)

private fun createListFilesTool(
    workspaceId: String,
    needsApproval: (String) -> Boolean,
    workspaceRepository: WorkspaceRepository,
) = Tool(
    name = "workspace_ls",
    description = """
        List entries inside a directory of the assistant's bound workspace Rootfs.
        path must be absolute and stay under /workspace, /skills or /tmp. Defaults to /workspace.
        Every entry carries its absolute Rootfs path, so it can be passed straight to workspace_read_file or workspace_grep.
        depth defaults to $LS_DEFAULT_DEPTH (this directory only) and is capped at $LS_MAX_DEPTH.
        glob matches entry NAMES with * and ? only. It is not a shell glob: no {a,b} expansion, no ! negation.
        At most $LS_MAX_ENTRIES entries come back; when truncated is true, omitted says how many were dropped.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                putPathProperty(required = false)
                put("glob", buildJsonObject {
                    put("type", "string")
                    put("description", "Optional name filter using * and ? only, for example *.kt. Not a shell glob.")
                })
                put("depth", buildJsonObject {
                    put("type", "integer")
                    put("description", "Directory levels to list. Defaults to $LS_DEFAULT_DEPTH, max $LS_MAX_DEPTH.")
                })
            },
        )
    },
    needsApproval = { needsApproval("workspace_ls") },
    execute = {
        val params = it.jsonObject
        val rootPath = (params.string("path") ?: DEFAULT_QUERY_PATH).requireQueryableRootPath("path")
        val depth = (params.int("depth") ?: LS_DEFAULT_DEPTH).coerceIn(1, LS_MAX_DEPTH)
        val glob = params.string("glob")?.requireNoNul("glob")?.trim()?.takeIf { it.isNotBlank() }

        val scanned = workspaceRepository.scanRootfsEntries(workspaceId, rootPath, depth, glob)
        val sorted = scanned.entries.sortedWith(compareBy({ it.type != "dir" }, { it.path.lowercase() }))
        val shown = sorted.take(LS_MAX_ENTRIES)
        val droppedByLimit = (sorted.size - shown.size).coerceAtLeast(0)
        val truncated = scanned.truncated || droppedByLimit > 0

        listOf(
            UIMessagePart.Text(
                buildJsonObject {
                    put("path", rootPath)
                    put("depth", depth)
                    if (glob != null) put("glob", glob)
                    put("entries", buildJsonArray {
                        shown.forEach { entry ->
                            add(
                                buildJsonObject {
                                    put("path", entry.path)
                                    put("type", entry.type)
                                    entry.sizeBytes?.let { size -> put("sizeBytes", size) }
                                }
                            )
                        }
                    })
                    put("count", shown.size)
                    put("truncated", truncated)
                    if (truncated) {
                        if (scanned.truncated) put("omittedIsLowerBound", true) else put("omitted", droppedByLimit)
                        put("hint", "Truncated. Narrow path, lower depth, or search by content with workspace_grep.")
                    }
                }.toString()
            )
        )
    },
)

private fun createGrepTool(
    workspaceId: String,
    needsApproval: (String) -> Boolean,
    workspaceRepository: WorkspaceRepository,
) = Tool(
    name = "workspace_grep",
    description = """
        Search file contents inside the assistant's bound workspace Rootfs.
        path must be absolute and stay under /workspace, /skills or /tmp. Defaults to /workspace.
        pattern is a LITERAL string unless regex=true. ignoreCase defaults to true.
        Each hit carries the absolute Rootfs path, the 1-based line number, the matched line, and up to
        contextLines lines of surrounding context, so the neighbourhood comes back in the same call.
        maxHits defaults to $GREP_DEFAULT_MAX_HITS (max $GREP_MAX_HITS); contextLines defaults to
        $GREP_DEFAULT_CONTEXT_LINES (max $GREP_MAX_CONTEXT_LINES). Binary and oversized files are skipped.
        Junk directories (.git, node_modules, build, .gradle, .idea, dist, __pycache__) are skipped
        unless path points inside one. If the file scan hits the cap, truncated is true.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("pattern", buildJsonObject {
                    put("type", "string")
                    put("description", "Text to search for. Literal by default; set regex=true for an extended regular expression.")
                })
                putPathProperty(required = false)
                put("glob", buildJsonObject {
                    put("type", "string")
                    put("description", "Optional name filter limiting which files are searched, for example *.kt. Not a shell glob.")
                })
                put("regex", buildJsonObject {
                    put("type", "boolean")
                    put("description", "Treat pattern as an extended regular expression. Defaults to false (literal).")
                })
                put("ignoreCase", buildJsonObject {
                    put("type", "boolean")
                    put("description", "Case-insensitive search. Defaults to true.")
                })
                put("maxHits", buildJsonObject {
                    put("type", "integer")
                    put("description", "Maximum number of matches to return. Defaults to $GREP_DEFAULT_MAX_HITS, max $GREP_MAX_HITS.")
                })
                put("contextLines", buildJsonObject {
                    put("type", "integer")
                    put(
                        "description",
                        "Lines of context before and after each match. Defaults to $GREP_DEFAULT_CONTEXT_LINES, max $GREP_MAX_CONTEXT_LINES, 0 disables context."
                    )
                })
            },
            required = listOf("pattern"),
        )
    },
    needsApproval = { needsApproval("workspace_grep") },
    execute = {
        val params = it.jsonObject
        val pattern = params.string("pattern")?.requireNoNul("pattern")?.takeIf { it.isNotBlank() }
            ?: error("pattern is required")
        val rootPath = (params.string("path") ?: DEFAULT_QUERY_PATH).requireQueryableRootPath("path")
        val glob = params.string("glob")?.requireNoNul("glob")?.trim()?.takeIf { it.isNotBlank() }
        val regex = params.boolean("regex") ?: false
        val ignoreCase = params.boolean("ignoreCase") ?: true
        val maxHits = (params.int("maxHits") ?: GREP_DEFAULT_MAX_HITS).coerceIn(1, GREP_MAX_HITS)
        val contextLines = (params.int("contextLines") ?: GREP_DEFAULT_CONTEXT_LINES)
            .coerceIn(0, GREP_MAX_CONTEXT_LINES)

        val found = workspaceRepository.grepRootfs(
            workspaceId = workspaceId,
            rootPath = rootPath,
            pattern = pattern,
            regex = regex,
            ignoreCase = ignoreCase,
            glob = glob,
            maxHits = maxHits,
            contextLines = contextLines,
        )

        listOf(
            UIMessagePart.Text(
                buildJsonObject {
                    put("pattern", pattern)
                    put("path", rootPath)
                    if (glob != null) put("glob", glob)
                    put("ignoreCase", ignoreCase)
                    put("matches", buildJsonArray {
                        found.hits.forEach { hit ->
                            add(
                                buildJsonObject {
                                    put("path", hit.path)
                                    put("line", hit.line)
                                    put("text", hit.text)
                                    if (hit.before.isNotEmpty()) {
                                        put("before", buildJsonArray { hit.before.forEach { line -> add(JsonPrimitive(line)) } })
                                    }
                                    if (hit.after.isNotEmpty()) {
                                        put("after", buildJsonArray { hit.after.forEach { line -> add(JsonPrimitive(line)) } })
                                    }
                                }
                            )
                        }
                    })
                    put("count", found.hits.size)
                    put("truncated", found.truncated)
                    if (found.truncated) {
                        if (found.scanCapped) put("scanCapped", true)
                        when {
                            found.omittedIsLowerBound -> put("omittedIsLowerBound", true)
                            found.omitted > 0 -> put("omitted", found.omitted)
                        }
                        put("hint", found.truncationHint())
                    }
                }.toString()
            )
        )
    },
)

private fun createWriteFileTool(
    workspaceId: String,
    needsApproval: (String) -> Boolean,
    workspaceRepository: WorkspaceRepository,
) = Tool(
    name = "workspace_write_file",
    description = """
        Write a UTF-8 text file using the assistant's bound workspace Rootfs. Paths must be absolute inside Rootfs.
        Use /workspace for the workspace files area.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                putPathProperty(required = true)
                put("text", buildJsonObject {
                    put("type", "string")
                    put("description", "UTF-8 text content to write")
                })
                put("overwrite", buildJsonObject {
                    put("type", "boolean")
                    put("description", "Whether to overwrite an existing file. Defaults to true.")
                })
            },
            required = listOf("path", "text"),
        )
    },
    needsApproval = { needsApproval("workspace_write_file") || it.pathOutsideWritableRoots("path") },
    execute = {
        val params = it.jsonObject
        val path = params.absolutePath("path")
        val text = params.string("text") ?: error("text is required")
        val overwrite = params["overwrite"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: true
        val entry = workspaceRepository.writeTextInRootfs(workspaceId, path, text, overwrite)
        listOf(UIMessagePart.Text(entry.toJson().toString()))
    },
)

private fun createEditFileTool(
    workspaceId: String,
    needsApproval: (String) -> Boolean,
    workspaceRepository: WorkspaceRepository,
) = Tool(
    name = "workspace_edit_file",
    description = """
        Edit a UTF-8 text file using the assistant's bound workspace Rootfs. Paths must be absolute inside Rootfs.
        Use /workspace for the workspace files area.
        Provide old_text and new_text. By default old_text must occur exactly once; set replace_all=true to replace every occurrence.
        If no exact match is found, whitespace-tolerant line matching is attempted automatically.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                putPathProperty(required = true)
                put("old_text", buildJsonObject {
                    put("type", "string")
                    put("description", "Exact text to replace")
                })
                put("new_text", buildJsonObject {
                    put("type", "string")
                    put("description", "Replacement text")
                })
                put("replace_all", buildJsonObject {
                    put("type", "boolean")
                    put("description", "Whether to replace every occurrence. Defaults to false.")
                })
            },
            required = listOf("path", "old_text", "new_text"),
        )
    },
    needsApproval = { needsApproval("workspace_edit_file") || it.pathOutsideWritableRoots("path") },
    execute = {
        val params = it.jsonObject
        val path = params.absolutePath("path")
        val oldText = params.string("old_text") ?: error("old_text is required")
        val newText = params.string("new_text") ?: error("new_text is required")
        val replaceAll = params["replace_all"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
        require(oldText.isNotEmpty()) { "old_text must not be empty" }

        val original = workspaceRepository.readTextInRootfs(workspaceId, path)
        // 逐级尝试 exact -> line_trimmed -> block_anchor 替换器, 见 TextReplacers.kt
        val result = try {
            replaceText(original, oldText, newText, replaceAll)
        } catch (e: IllegalArgumentException) {
            error("${e.message} (path: $path)")
        }
        val entry = workspaceRepository.writeTextInRootfs(workspaceId, path, result.updated, overwrite = true)
        val diff = generateUnifiedDiff(original, result.updated, entry.path)
        listOf(
            UIMessagePart.Text(
                text = buildJsonObject {
                    put("path", entry.path)
                    put("replacements", result.replacements)
                    if (result.strategy != ExactReplacer.name) put("matchStrategy", result.strategy)
                    put("sizeBytes", entry.sizeBytes)
                    put("updatedAt", entry.updatedAt)
                }.toString(),
                // diff 存入 metadata 供 UI 渲染 diff view, 不会随工具结果发送给 API
                metadata = diff?.let { d -> DiffMetadata(diff = d).toMetadata() },
            )
        )
    },
)

private fun createShellTool(
    workspaceId: String,
    needsApproval: (String) -> Boolean,
    workspaceRepository: WorkspaceRepository,
    defaultCwd: String? = null,
) = Tool(
    name = "workspace_shell",
    description = buildString {
        append("Run a shell command in the assistant's bound workspace Rootfs. The workspace files area is mounted at /workspace. ")
        append("Use cwd for a path relative to the workspace files root. ")
        if (!defaultCwd.isNullOrBlank()) {
            append("Defaults to '$defaultCwd'. ")
        }
        append("Requires Rootfs to be installed and ready.")
    },
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("command", buildJsonObject {
                    put("type", "string")
                    put("description", "Shell command to run")
                })
                put("cwd", buildJsonObject {
                    put("type", "string")
                    put(
                        "description",
                        if (!defaultCwd.isNullOrBlank()) {
                            "Working directory relative to the workspace files root. Defaults to '$defaultCwd'."
                        } else {
                            "Working directory relative to the workspace files root. Defaults to root."
                        }
                    )
                })
                put("timeout", buildJsonObject {
                    put("type", "integer")
                    put(
                        "description",
                        "Command timeout in seconds. Defaults to 30, max $SHELL_TIMEOUT_MAX_SECONDS."
                    )
                })
            },
            required = listOf("command"),
        )
    },
    needsApproval = { needsApproval("workspace_shell") },
    execute = {
        val params = it.jsonObject
        val command = params.string("command") ?: error("command is required")
        val cwd = (params.string("cwd") ?: defaultCwd.orEmpty())
            .removePrefix("/workspace/").removePrefix("/workspace")
        val timeoutMillis = params.string("timeout")?.toLongOrNull()
            ?.coerceIn(1L, SHELL_TIMEOUT_MAX_SECONDS)
            ?.times(1_000L)
            ?: WorkspaceManager.DEFAULT_COMMAND_TIMEOUT_MS
        val result = workspaceRepository.executeCommand(workspaceId, command, cwd, timeoutMillis)
        listOf(
            UIMessagePart.Text(
                buildJsonObject {
                    put("exitCode", result.exitCode)
                    put("stdout", result.stdout)
                    put("stderr", result.stderr)
                    put("timedOut", result.timedOut)
                    if (result.truncated) put("truncated", true)
                }.toString()
            )
        )
    },
)

private fun kotlinx.serialization.json.JsonObject.string(name: String): String? =
    this[name]?.jsonPrimitive?.contentOrNull

private data class RootfsEntry(
    val path: String,
    val type: String,
    val sizeBytes: Long?,
)

private data class RootfsEntries(
    val entries: List<RootfsEntry>,
    /** 扫描到安全上限就停了, 真正的总数未知 */
    val truncated: Boolean,
)

private data class RootfsGrepHit(
    val path: String,
    val line: Int,
    val text: String,
    val before: List<String>,
    val after: List<String>,
)

private data class RootfsGrepResult(
    val hits: List<RootfsGrepHit>,
    val omitted: Int,
    val omittedIsLowerBound: Boolean,
    /** 文件扫描撞上 QUERY_SCAN_FILE_CAP 停了, 没扫完的目录里可能还有命中。 */
    val scanCapped: Boolean = false,
) {
    val truncated: Boolean get() = omitted > 0 || omittedIsLowerBound || scanCapped
}

private fun RootfsGrepResult.truncationHint(): String {
    val parts = mutableListOf<String>()
    if (scanCapped) {
        parts += "file scan stopped after $QUERY_SCAN_FILE_CAP files; narrow path or glob"
    }
    if (omitted > 0 || omittedIsLowerBound) {
        parts += "Raise maxHits, narrow path/glob, or make the pattern more specific."
    }
    return parts.joinToString(" ").ifBlank {
        "Truncated. Narrow path or glob."
    }
}

private const val DEFAULT_QUERY_PATH = "/workspace"

/** 查询类工具(ls / grep)的作用域。它们会递归遍历, 不能放开到整个 Rootfs。 */
private val QUERY_ROOT_PREFIXES = listOf("/workspace", "/skills", "/tmp")

/** 扫描阶段的安全上限: 到了就停, 并标明"总数未知", 而不是假装知道。 */
private const val QUERY_SCAN_FILE_CAP = 5000

/** grep 递归时直接跳过: 搜这些目录是负收益, 还会把配额吃光。搜索根本身在里面则不跳。 */
private val GREP_SKIP_DIR_NAMES = setOf(
    ".git",
    "node_modules",
    "build",
    ".gradle",
    ".idea",
    "dist",
    "__pycache__",
)

private const val LS_SCAN_ENTRY_CAP = 5000

/** 命中数达到 maxHits 后还继续数多少条, 用来给出精确的 omitted; 超过就只报下限。 */
private const val GREP_OMITTED_COUNT_CAP = 50

/** 判定二进制: 前若干字节里出现 NUL 就当二进制, 跳过 */
private const val BINARY_PROBE_BYTES = 8192

private fun String.requireNoNul(name: String): String {
    require(!contains('\u0000')) { "$name contains invalid character" }
    return this
}

/**
 * 词法规范化(不碰文件系统): 先消掉 . 和 .., 再拿白名单比前缀。
 *
 * **不能用 startsWith 直接比**: "/workspace/../etc" 规范化前会骗过字符串前缀检查。
 * 真正的物理逃逸(符号链接指到外面)由 fileSystem.resolve() 的 canonical 校验兜底。
 */
private fun String.requireQueryableRootPath(name: String): String {
    val raw = replace('\\', '/').trim().requireNoNul(name)
    require(raw.startsWith("/")) { "$name must be an absolute path inside Rootfs" }
    val normalized = runCatching {
        java.nio.file.Paths.get(raw).normalize().toString().replace('\\', '/')
    }.getOrElse { error("$name is not a valid path") }
    require(normalized.startsWith("/")) { "$name must stay inside Rootfs" }
    val trimmed = if (normalized.length > 1) normalized.trimEnd('/') else normalized
    val allowed = QUERY_ROOT_PREFIXES.any { prefix ->
        trimmed == prefix || trimmed.startsWith("$prefix/")
    }
    require(allowed) {
        "$name must be under ${QUERY_ROOT_PREFIXES.joinToString(", ")} (got $trimmed)"
    }
    return trimmed
}

/** 把 * / ? 名字匹配编译成正则。不是 shell glob: 没有 {} 展开, 也没有 ! 取反。 */
private fun nameGlobToRegex(glob: String): Regex? = runCatching {
    val sb = StringBuilder("^")
    glob.forEach { ch ->
        when (ch) {
            '*' -> sb.append("[^/]*")
            '?' -> sb.append("[^/]")
            '.', '(', ')', '+', '|', '^', '$', '{', '}', '[', ']', '\\' -> sb.append('\\').append(ch)
            else -> sb.append(ch)
        }
    }
    sb.append('$')
    Regex(sb.toString())
}.getOrNull()

/** 前若干字节里有 NUL 就当二进制。读不出来也当二进制跳过。 */
private fun File.looksBinary(): Boolean = runCatching {
    val probe = ByteArray(BINARY_PROBE_BYTES)
    val read = inputStream().use { stream -> stream.read(probe) }
    (0 until read.coerceAtLeast(0)).any { index -> probe[index] == 0.toByte() }
}.getOrDefault(true)

/** 宿主文件还原成 Rootfs 内的绝对路径, 直接可以喂给 read_file / grep。 */
private fun rootfsChildPath(rootPath: String, child: File, base: File): String {
    val relative = base.canonicalFile.toPath()
        .relativize(child.canonicalFile.toPath())
        .toString()
        .replace(File.separatorChar, '/')
    return if (rootPath == "/") "/$relative" else "$rootPath/$relative"
}

/** 白名单里的符号链接可能指到外面: 每一项都要求 canonical 之后仍在 base 之下。 */
private fun File.staysUnder(baseCanonicalPath: java.nio.file.Path): Boolean = runCatching {
    canonicalFile.toPath().startsWith(baseCanonicalPath)
}.getOrDefault(false)

private suspend fun WorkspaceRepository.scanRootfsEntries(
    workspaceId: String,
    rootPath: String,
    depth: Int,
    glob: String?,
): RootfsEntries = withContext(Dispatchers.IO) {
    val base = resolveRootfsEntry(workspaceId, rootPath)
    require(base.exists()) { "Path does not exist: $rootPath" }
    require(base.isDirectory) { "Path is not a directory: $rootPath" }
    val baseCanonical = base.canonicalFile.toPath()
    val matcher = glob?.let { nameGlobToRegex(it) }

    val entries = mutableListOf<RootfsEntry>()
    var capped = false
    var level = 1
    var current = listOf(base)
    while (level <= depth && current.isNotEmpty() && !capped) {
        val next = mutableListOf<File>()
        for (dir in current) {
            val children = runCatching { dir.listFiles() }.getOrNull() ?: continue
            for (child in children) {
                if (child.name.startsWith(".l2s.")) continue
                if (!child.staysUnder(baseCanonical)) continue
                if (entries.size >= LS_SCAN_ENTRY_CAP) {
                    capped = true
                    break
                }
                val isDirectory = child.isDirectory
                if (!isDirectory && matcher != null && !matcher.matches(child.name)) continue
                entries += RootfsEntry(
                    path = rootfsChildPath(rootPath, child, base),
                    type = if (isDirectory) "dir" else "file",
                    sizeBytes = if (isDirectory) null else child.length(),
                )
                if (isDirectory) next += child
            }
            if (capped) break
        }
        current = next
        level++
    }
    RootfsEntries(entries = entries, truncated = capped)
}

private suspend fun WorkspaceRepository.grepRootfs(
    workspaceId: String,
    rootPath: String,
    pattern: String,
    regex: Boolean,
    ignoreCase: Boolean,
    glob: String?,
    maxHits: Int,
    contextLines: Int,
): RootfsGrepResult = withContext(Dispatchers.IO) {
    val base = resolveRootfsEntry(workspaceId, rootPath)
    require(base.exists()) { "Path does not exist: $rootPath" }
    val baseCanonical = base.canonicalFile.toPath()
    val options = if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()
    val matcher = runCatching {
        if (regex) Regex(pattern, options) else Regex(Regex.escape(pattern), options)
    }.getOrElse { error("Invalid pattern: ${it.message}") }
    val nameMatcher = glob?.let { nameGlobToRegex(it) }

    val files = mutableListOf<File>()
    var scanCapped = false
    if (base.isFile) {
        files += base
    } else {
        var visited = 0
        val stack = ArrayDeque<File>()
        stack.addLast(base)
        while (stack.isNotEmpty()) {
            val dir = stack.removeLast()
            val children = runCatching { dir.listFiles() }.getOrNull() ?: continue
            for (child in children) {
                if (child.name.startsWith(".l2s.")) continue
                if (!child.staysUnder(baseCanonical)) continue
                if (child.isDirectory) {
                    // 搜索根在垃圾目录内时不跳, 否则指定 path=.git 会什么都搜不到。
                    if (child != base && child.name in GREP_SKIP_DIR_NAMES) continue
                    stack.addLast(child)
                    continue
                }
                if (nameMatcher != null && !nameMatcher.matches(child.name)) continue
                if (++visited > QUERY_SCAN_FILE_CAP) {
                    scanCapped = true
                    break
                }
                files += child
            }
            if (scanCapped) break
        }
    }

    val hits = mutableListOf<RootfsGrepHit>()
    var omitted = 0
    var omittedIsLowerBound = false
    outer@ for (file in files) {
        if (file.length() > MAX_READ_FILE_BYTES) continue
        if (file.looksBinary()) continue
        val lines = runCatching { file.readLines(Charsets.UTF_8) }.getOrNull() ?: continue
        val hitPath = rootfsChildPath(rootPath, file, base)
        for ((index, line) in lines.withIndex()) {
            if (!matcher.containsMatchIn(line)) continue
            if (hits.size >= maxHits) {
                omitted++
                if (omitted >= GREP_OMITTED_COUNT_CAP) {
                    omittedIsLowerBound = true
                    break@outer
                }
                continue
            }
            hits += RootfsGrepHit(
                path = hitPath,
                line = index + 1,
                text = line,
                before = if (contextLines > 0) {
                    lines.subList((index - contextLines).coerceAtLeast(0), index)
                } else {
                    emptyList()
                },
                after = if (contextLines > 0) {
                    lines.subList(index + 1, (index + 1 + contextLines).coerceAtMost(lines.size))
                } else {
                    emptyList()
                },
            )
        }
    }
    RootfsGrepResult(
        hits = hits,
        omitted = omitted,
        omittedIsLowerBound = omittedIsLowerBound,
        scanCapped = scanCapped,
    )
}

/** 按行切分, 并且让「末尾换行」不算多一行: "a\nb\n" 与 "a\nb" 都是 2 行. */
private fun String.toWorkspaceLines(): List<String> {
    if (isEmpty()) return emptyList()
    val lines = split('\n')
    return if (endsWith('\n')) lines.dropLast(1) else lines
}

private fun kotlinx.serialization.json.JsonObject.int(name: String): Int? =
    this[name]?.jsonPrimitive?.contentOrNull?.toIntOrNull()

private fun kotlinx.serialization.json.JsonObject.boolean(name: String): Boolean? =
    this[name]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()

/** 可选的 Rootfs 绝对路径. 给了就必须是绝对路径; 没给返回 null, 由调用方决定默认值. */
private fun kotlinx.serialization.json.JsonObject.optionalAbsolutePath(name: String): String? {
    val raw = string(name)?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val path = raw.replace('\\', '/')
    require(path.startsWith("/")) { "$name must be an absolute path inside Rootfs" }
    require(!path.contains('\u0000')) { "$name contains invalid character" }
    return path.trimEnd('/').ifBlank { "/" }
}

private suspend fun WorkspaceRepository.readTextInRootfs(
    workspaceId: String,
    path: String,
): String = readRootfsBuffer(workspaceId, path).toString(Charsets.UTF_8.name())

/**
 * 按 Rootfs 内绝对路径读入内存。路径映射交给 WorkspaceManager, 由它统一处理
 * /workspace、bind mount 与 Rootfs 内部路径。
 */
private suspend fun WorkspaceRepository.readRootfsBuffer(
    workspaceId: String,
    path: String,
): ByteArrayOutputStream {
    val size = rootfsFileSize(workspaceId, path)
    require(size <= MAX_READ_FILE_BYTES) {
        "File is too large to read: $path (${size / 1024 / 1024}MB, max ${MAX_READ_FILE_BYTES / 1024 / 1024}MB). Use shell commands like head, tail, or grep to read parts of it."
    }
    return ByteArrayOutputStream(size.toInt()).also { exportRootfsFile(workspaceId, path, it) }
}

private suspend fun WorkspaceRepository.readImageInRootfs(
    workspaceId: String,
    path: String,
): List<UIMessagePart> {
    val bytes = readRootfsBuffer(workspaceId, path).toByteArray()

    val filesManager = getKoin().get<FilesManager>()
    val uris = filesManager.createChatFilesByByteArrays(listOf(bytes))
    return listOf(
        UIMessagePart.Image(url = uris.first().toString()),
        UIMessagePart.Text(
            buildJsonObject {
                put("path", path)
                put("description", "Image file read successfully")
            }.toString()
        ),
    )
}

private suspend fun WorkspaceRepository.writeTextInRootfs(
    workspaceId: String,
    path: String,
    text: String,
    overwrite: Boolean,
): WorkspaceFileEntry {
    val pathArg = path.shellQuote()
    val result = runRootfsCommand(
        workspaceId = workspaceId,
        action = "Write file",
        command = """
            if [ -e $pathArg ] && [ ${(!overwrite).shellFlag()} = 1 ]; then
              printf '%s\n' ${"File already exists: $path".shellQuote()} >&2
              exit 1
            fi
            if [ -e $pathArg ] && [ ! -f $pathArg ]; then
              printf '%s\n' ${"Path is not a file: $path".shellQuote()} >&2
              exit 1
            fi
            parent=${'$'}(dirname -- $pathArg) || exit 1
            mkdir -p -- "${'$'}parent" || exit 1
            cat > $pathArg || exit 1
            ${statEntryCommand(path)}
        """.trimIndent(),
        stdin = text.toByteArray(Charsets.UTF_8),
    )
    return result.stdout.parseRootfsEntry()
}

private suspend fun WorkspaceRepository.runRootfsCommand(
    workspaceId: String,
    action: String,
    command: String,
    stdin: ByteArray? = null,
): WorkspaceCommandResult {
    val result = executeCommand(
        id = workspaceId,
        command = command,
        timeoutMillis = WorkspaceManager.DEFAULT_COMMAND_TIMEOUT_MS,
        stdin = stdin,
    )
    if (result.timedOut) {
        error("$action timed out")
    }
    if (result.exitCode != 0) {
        val message = result.stderr.ifBlank { result.stdout }.trim()
        error(if (message.isBlank()) "$action failed with exit code ${result.exitCode}" else message)
    }
    if (result.truncated) {
        error("$action output is too large")
    }
    return result
}

private fun statEntryCommand(path: String): String {
    val pathArg = path.shellQuote()
    return """
        if [ -d $pathArg ]; then entry_type=d; else entry_type=f; fi
        entry_size=${'$'}(stat -c '%s' -- $pathArg) || exit 1
        entry_mtime=${'$'}(stat -c '%Y' -- $pathArg) || exit 1
        printf '%s\0%s\0%s\0%s\0' "${'$'}entry_type" "${'$'}entry_size" "${'$'}entry_mtime" $pathArg
    """.trimIndent()
}

private fun String.parseRootfsEntry(): WorkspaceFileEntry =
    parseRootfsEntries().singleOrNull() ?: error("Invalid file metadata output")

private fun String.parseRootfsEntries(): List<WorkspaceFileEntry> {
    val fields = split('\u0000').dropLastWhile { it.isEmpty() }
    require(fields.size % 4 == 0) { "Invalid file metadata output" }
    return fields.chunked(4).map { chunk ->
        val type = chunk[0]
        val size = chunk[1].toLongOrNull() ?: error("Invalid file size: ${chunk[1]}")
        val updatedAt = (chunk[2].toLongOrNull() ?: error("Invalid file mtime: ${chunk[2]}")) * 1_000L
        val path = chunk[3]
        WorkspaceFileEntry(
            path = path,
            name = path.rootfsName(),
            isDirectory = type == "d",
            sizeBytes = size,
            updatedAt = updatedAt,
        )
    }
}

private fun kotlinx.serialization.json.JsonObject.absolutePath(name: String): String {
    val path = string(name)?.replace('\\', '/')?.trim() ?: error("$name is required")
    require(path.isNotBlank()) { "$name is required" }
    require(path.startsWith("/")) { "$name must be an absolute path inside Rootfs" }
    require(!path.contains('\u0000')) { "$name contains invalid character" }
    return path
}

// 免强制审批的可写安全区: 工作区文件目录、临时目录和技能目录
private val WRITABLE_ROOT_PREFIXES = listOf("/workspace", "/tmp", "/skills")

private fun kotlinx.serialization.json.JsonElement.pathOutsideWritableRoots(name: String): Boolean =
    runCatching {
        jsonObject.absolutePath(name).isOutsideWritableRoots()
    }.getOrDefault(true)

private fun String.isOutsideWritableRoots(): Boolean {
    val normalized = trimEnd('/').ifBlank { "/" }
    return WRITABLE_ROOT_PREFIXES.none { prefix ->
        normalized == prefix || normalized.startsWith("$prefix/")
    }
}

private fun String.rootfsName(): String =
    trimEnd('/').substringAfterLast('/').ifBlank { "/" }

private fun String.shellQuote(): String =
    "'" + replace("'", "'\"'\"'") + "'"

private fun Boolean.shellFlag(): Int = if (this) 1 else 0

private fun JsonObjectBuilder.putPathProperty(required: Boolean) {
    put("path", buildJsonObject {
        put("type", "string")
        put(
            "description",
            if (required) {
                "Absolute path inside Rootfs. Use /workspace for the workspace files area."
            } else {
                "Optional absolute path inside Rootfs. Use /workspace for the workspace files area."
            }
        )
    })
}

private fun WorkspaceFileEntry.toJson() = buildJsonObject {
    put("path", path)
    put("name", name)
    put("isDirectory", isDirectory)
    put("sizeBytes", sizeBytes)
    put("updatedAt", updatedAt)
}
