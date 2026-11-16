package com.radardisco.android.core.scanner

import com.radardisco.android.model.BreadcrumbItem
import com.radardisco.android.model.FileCategory
import com.radardisco.android.model.ItemType
import com.radardisco.android.model.ScanChildPreview
import com.radardisco.android.model.ScanItemSnapshot
import com.radardisco.android.model.ScanSnapshot
import com.radardisco.android.model.ScanSourceType
import com.radardisco.android.model.ScanStats
import com.radardisco.android.model.ScanStatus
import com.radardisco.android.model.SpaceHogEntry
import com.radardisco.android.model.TopFileEntry
import com.radardisco.android.model.TopFolderEntry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.nio.file.Files
import kotlin.math.max

data class ScanFileEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val isSymbolicLink: Boolean,
    val sizeBytes: Long
)

interface ScanFileSystem {
    val sourceType: ScanSourceType
    fun resolveRootDisplayName(rootPath: String): String
    fun listChildren(dirPath: String): List<ScanFileEntry>
    fun parentPathOf(path: String): String?
    fun canonicalKey(path: String): String? = null
}

class JavaIoScanFileSystem : ScanFileSystem {
    override val sourceType: ScanSourceType = ScanSourceType.FILE_SYSTEM

    override fun resolveRootDisplayName(rootPath: String): String {
        val norm = FolderClassifier.normalizePosixPath(rootPath)
        return when (norm) {
            "/storage/emulated/0", "/sdcard" -> "Armazenamento Interno"
            "/" -> "/"
            else -> File(norm).name.ifBlank { norm }
        }
    }

    override fun listChildren(dirPath: String): List<ScanFileEntry> {
        val dir = File(dirPath)
        val children = dir.listFiles()
            ?: throw SecurityException("Não foi possível ler o diretório: $dirPath")

        // No Android 11+ (API 30+), o Scoped Storage bloqueia silenciosamente Android/data e Android/obb
        // mesmo com MANAGE_EXTERNAL_STORAGE, retornando lista vazia ou apenas a pasta do próprio app.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val lowerNorm = FolderClassifier.normalizePosixPath(dirPath).lowercase()
            if (lowerNorm.endsWith("/android/data") || lowerNorm.endsWith("/android/obb")) {
                val externalAppsVisible = children.any { it.name != "com.radardisco.android" }
                if (!externalAppsVisible) {
                    throw SecurityException("Pasta protegida pelo Android 11+ (Scoped Storage): $dirPath")
                }
            }
        }

        return children.mapNotNull { child ->
            val path = child.absolutePath
            val isSymlink = runCatching { Files.isSymbolicLink(child.toPath()) }.getOrDefault(false)
            if (isSymlink) return@mapNotNull null

            val isDir = child.isDirectory
            val size = if (isDir) 0L else max(0L, child.length())
            ScanFileEntry(
                name = child.name,
                path = path,
                isDirectory = isDir,
                isSymbolicLink = false,
                sizeBytes = size
            )
        }
    }

    override fun parentPathOf(path: String): String? = File(path).parent

    override fun canonicalKey(path: String): String? =
        runCatching { File(path).canonicalPath }.getOrNull()
}

data class DirectFileNode(
    val name: String,
    val path: String,
    val size: Long,
    val category: FileCategory
)

class DirNode(
    val name: String,
    val path: String,
    val parent: DirNode?,
    val depth: Int,
    var ownFilesSize: Long = 0L,
    var totalSize: Long = 0L,
    var ownFileCount: Int = 0,
    var totalFileCount: Int = 0,
    var totalFolderCount: Int = 0,
    var pendingSubdirs: Int = 0,
    var accessDenied: Boolean = false,
    var specialBadge: String? = null,
    var hogKind: String? = null,
    val categorySizes: MutableMap<FileCategory, Long> = FolderClassifier.createEmptyCategoryMap(),
    val children: LinkedHashMap<String, DirNode> = LinkedHashMap(),
    val directFiles: MutableList<DirectFileNode> = mutableListOf()
)

class DiskScanEngine(
    rawRootPath: String,
    private val fileSystem: ScanFileSystem = JavaIoScanFileSystem(),
    private val concurrency: Int = 10,
    private val maxDirectFilesPerFolder: Int = 150,
    private val maxTopFiles: Int = 50,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    val rootPath: String = normalizeKeyPath(rawRootPath)
    @Volatile
    var focusPath: String = rootPath
        private set

    @Volatile
    var status: ScanStatus = ScanStatus.IDLE
        private set

    @Volatile
    var errorMessage: String? = null
        private set

    private val lock = Any()
    private val rootNode: DirNode
    private val nodeByKey = HashMap<String, DirNode>()
    private val spaceHogs = HashMap<String, DirNode>()
    private val visitedCanonicalDirs = HashSet<String>()
    private val topFiles = mutableListOf<TopFileEntry>()
    private val categoryTotals = FolderClassifier.createEmptyCategoryMap()

    private var scannedFiles = 0
    private var scannedFolders = 0
    private var skippedCount = 0
    private var currentPath = ""
    private var startTimeMs = 0L
    private var pausedAccumulatedMs = 0L
    private var pauseStartedAt: Long? = null

    private val priorityQueue = ArrayDeque<DirNode>()
    private val normalQueue = ArrayDeque<DirNode>()

    @Volatile
    private var cancelled = false

    @Volatile
    private var resumeSignal: CompletableDeferred<Unit>? = null

    init {
        val rootName = fileSystem.resolveRootDisplayName(rootPath)
        rootNode = createDirNode(rootName, rootPath, parent = null, depth = 0)
        nodeByKey[keyOf(rootPath)] = rootNode
        fileSystem.canonicalKey(rootPath)?.let { visitedCanonicalDirs.add(it) }
    }

    private fun normalizeKeyPath(path: String): String =
        if (fileSystem.sourceType == ScanSourceType.SAF_TREE) path
        else FolderClassifier.normalizePosixPath(path)

    private fun keyOf(path: String): String =
        if (fileSystem.sourceType == ScanSourceType.SAF_TREE) path
        else FolderClassifier.normalizePosixPath(path)

    private fun isNodeInsideFocus(node: DirNode): Boolean {
        val targetFocus = focusPath
        var curr: DirNode? = node
        while (curr != null) {
            if (keyOf(curr.path) == keyOf(targetFocus)) return true
            curr = curr.parent
        }
        return false
    }

    private fun collectSubtreeKeys(root: DirNode, out: MutableSet<String>) {
        out.add(keyOf(root.path))
        for (child in root.children.values) {
            collectSubtreeKeys(child, out)
        }
    }

    private fun createDirNode(
        name: String,
        fullPath: String,
        parent: DirNode?,
        depth: Int
    ): DirNode {
        val detected = FolderClassifier.detectFolderKind(name, fullPath)
        val node = DirNode(
            name = name,
            path = fullPath,
            parent = parent,
            depth = depth,
            specialBadge = detected.badge ?: parent?.specialBadge,
            hogKind = detected.hogKind
        )
        if (detected.hogKind != null && !hasAncestorHog(parent)) {
            spaceHogs[keyOf(fullPath)] = node
        }
        return node
    }

    private fun hasAncestorHog(node: DirNode?): Boolean {
        var curr = node
        while (curr != null) {
            if (curr.hogKind != null) return true
            curr = curr.parent
        }
        return false
    }

    private fun addPending(node: DirNode, delta: Int) {
        var curr: DirNode? = node
        while (curr != null) {
            curr.pendingSubdirs = max(0, curr.pendingSubdirs + delta)
            curr = curr.parent
        }
    }

    private fun propagateMetrics(
        node: DirNode,
        deltaBytes: Long,
        deltaFiles: Int,
        deltaFolders: Int,
        batchCategories: Map<FileCategory, Long>
    ) {
        var curr: DirNode? = node
        while (curr != null) {
            curr.totalSize = max(0L, curr.totalSize + deltaBytes)
            curr.totalFileCount = max(0, curr.totalFileCount + deltaFiles)
            curr.totalFolderCount = max(0, curr.totalFolderCount + deltaFolders)
            for ((cat, valDelta) in batchCategories) {
                if (valDelta != 0L) {
                    curr.categorySizes[cat] = max(0L, (curr.categorySizes[cat] ?: 0L) + valDelta)
                }
            }
            curr = curr.parent
        }

        for ((cat, valDelta) in batchCategories) {
            if (valDelta != 0L) {
                categoryTotals[cat] = max(0L, (categoryTotals[cat] ?: 0L) + valDelta)
            }
        }
    }

    private fun recordTopFile(file: TopFileEntry) {
        if (file.size <= 0L) return
        if (topFiles.size >= maxTopFiles && file.size <= topFiles.last().size) return
        topFiles.add(file)
        topFiles.sortByDescending { it.size }
        if (topFiles.size > maxTopFiles) {
            topFiles.subList(maxTopFiles, topFiles.size).clear()
        }
    }

    private fun enqueueDirLocked(node: DirNode) {
        addPending(node, 1)
        if (node.depth <= 2 || isNodeInsideFocus(node)) {
            priorityQueue.addLast(node)
        } else {
            normalQueue.addLast(node)
        }
    }

    private fun dequeueDirLocked(): DirNode? {
        if (priorityQueue.isNotEmpty()) return priorityQueue.removeFirst()
        if (normalQueue.isNotEmpty()) return normalQueue.removeLast()
        return null
    }

    fun setFocusPath(targetPath: String) {
        val norm = normalizeKeyPath(targetPath)
        synchronized(lock) {
            if (!nodeByKey.containsKey(keyOf(norm))) return
            focusPath = norm
            if (normalQueue.isNotEmpty()) {
                val remaining = ArrayList<DirNode>(normalQueue.size)
                while (normalQueue.isNotEmpty()) {
                    val item = normalQueue.removeFirst()
                    if (isNodeInsideFocus(item)) {
                        priorityQueue.addLast(item)
                    } else {
                        remaining.add(item)
                    }
                }
                remaining.forEach { normalQueue.addLast(it) }
            }
        }
    }

    fun pause() {
        synchronized(lock) {
            if (status == ScanStatus.SCANNING) {
                status = ScanStatus.PAUSED
                pauseStartedAt = System.currentTimeMillis()
                resumeSignal = CompletableDeferred()
            }
        }
    }

    fun resume() {
        val signalToComplete: CompletableDeferred<Unit>?
        synchronized(lock) {
            if (status != ScanStatus.PAUSED) return
            pauseStartedAt?.let { started ->
                pausedAccumulatedMs += max(0L, System.currentTimeMillis() - started)
                pauseStartedAt = null
            }
            status = ScanStatus.SCANNING
            signalToComplete = resumeSignal
            resumeSignal = null
        }
        signalToComplete?.complete(Unit)
    }

    fun cancel() {
        val signalToComplete: CompletableDeferred<Unit>?
        synchronized(lock) {
            cancelled = true
            status = ScanStatus.CANCELLED
            priorityQueue.clear()
            normalQueue.clear()
            signalToComplete = resumeSignal
            resumeSignal = null
        }
        signalToComplete?.complete(Unit)
    }

    private suspend fun waitIfPaused() {
        while (!cancelled) {
            val signal = synchronized(lock) {
                if (status == ScanStatus.PAUSED && !cancelled) resumeSignal else null
            } ?: break
            signal.await()
        }
    }

    suspend fun run(onProgress: ((ScanSnapshot) -> Unit)? = null) {
        synchronized(lock) {
            status = ScanStatus.SCANNING
            startTimeMs = System.currentTimeMillis()
            enqueueDirLocked(rootNode)
        }

        var lastEmitAt = 0L
        val emitLock = Any()
        fun maybeEmit(force: Boolean = false) {
            if (onProgress == null) return
            val now = System.currentTimeMillis()
            val shouldEmit = synchronized(emitLock) {
                if (force || now - lastEmitAt >= 120L) {
                    lastEmitAt = now
                    true
                } else {
                    false
                }
            }
            if (shouldEmit) {
                onProgress(getSnapshot())
            }
        }

        maybeEmit(force = true)

        coroutineScope {
            val workerCount = concurrency.coerceIn(1, 24)
            repeat(workerCount) {
                launch(ioDispatcher) {
                    while (!cancelled) {
                        waitIfPaused()
                        if (cancelled) break

                        val dirNode: DirNode? = synchronized(lock) {
                            dequeueDirLocked()
                        }

                        if (dirNode == null) {
                            val allDone = synchronized(lock) { rootNode.pendingSubdirs == 0 }
                            if (allDone) break
                            delay(8)
                            continue
                        }

                        scanSingleDirectory(dirNode)
                        maybeEmit(force = false)
                    }
                }
            }
        }

        synchronized(lock) {
            if (!cancelled && status != ScanStatus.ERROR) {
                status = ScanStatus.COMPLETED
                currentPath = ""
            }
        }
        maybeEmit(force = true)
    }

    private suspend fun scanSingleDirectory(dirNode: DirNode) {
        synchronized(lock) {
            currentPath = dirNode.path
            scannedFolders++
        }

        val entries = try {
            fileSystem.listChildren(dirNode.path)
        } catch (_: Throwable) {
            synchronized(lock) {
                dirNode.accessDenied = true
                skippedCount++
                addPending(dirNode, -1)
            }
            return
        }

        var batchBytes = 0L
        var batchFiles = 0
        var batchFolders = 0
        val batchCategories = FolderClassifier.createEmptyCategoryMap()
        val pendingSubNodes = mutableListOf<DirNode>()
        val pendingDirectFiles = mutableListOf<DirectFileNode>()
        val pendingTopFiles = mutableListOf<TopFileEntry>()

        try {
            for (entry in entries) {
                if (cancelled) break
                if (status == ScanStatus.PAUSED) {
                    waitIfPaused()
                }
                if (entry.isSymbolicLink) continue

                if (entry.isDirectory) {
                    val canonical = fileSystem.canonicalKey(entry.path)
                    val alreadyVisited = if (canonical != null) {
                        synchronized(lock) { !visitedCanonicalDirs.add(canonical) }
                    } else {
                        false
                    }
                    if (alreadyVisited) continue

                    val childNode = createDirNode(entry.name, entry.path, dirNode, dirNode.depth + 1)
                    pendingSubNodes.add(childNode)
                    batchFolders++
                } else {
                    val size = max(0L, entry.sizeBytes)
                    val category = FolderClassifier.classifyFile(entry.name, dirNode.specialBadge)
                    batchBytes += size
                    batchFiles++
                    batchCategories[category] = (batchCategories[category] ?: 0L) + size

                    pendingDirectFiles.add(
                        DirectFileNode(
                            name = entry.name,
                            path = entry.path,
                            size = size,
                            category = category
                        )
                    )
                    pendingTopFiles.add(
                        TopFileEntry(
                            name = entry.name,
                            path = entry.path,
                            parentPath = dirNode.path,
                            size = size,
                            category = category
                        )
                    )
                }
            }

            synchronized(lock) {
                for (childNode in pendingSubNodes) {
                    val childKey = keyOf(childNode.path)
                    dirNode.children[childKey] = childNode
                    nodeByKey[childKey] = childNode
                    enqueueDirLocked(childNode)
                }

                if (pendingDirectFiles.isNotEmpty()) {
                    dirNode.directFiles.addAll(pendingDirectFiles)
                    if (dirNode.directFiles.size > maxDirectFilesPerFolder) {
                        dirNode.directFiles.sortByDescending { it.size }
                        dirNode.directFiles.subList(maxDirectFilesPerFolder, dirNode.directFiles.size).clear()
                    }
                }

                for (topFile in pendingTopFiles) {
                    recordTopFile(topFile)
                }

                scannedFiles += batchFiles
                if (batchBytes > 0L || batchFiles > 0 || batchFolders > 0) {
                    dirNode.ownFilesSize += batchBytes
                    dirNode.ownFileCount += batchFiles
                    propagateMetrics(dirNode, batchBytes, batchFiles, batchFolders, batchCategories)
                }
            }
        } catch (_: Throwable) {
            synchronized(lock) {
                skippedCount++
            }
        } finally {
            synchronized(lock) {
                addPending(dirNode, -1)
            }
        }
    }

    fun removeNodeByPath(targetPath: String): Long {
        val norm = normalizeKeyPath(targetPath)
        val key = keyOf(norm)

        synchronized(lock) {
            val dirNode = nodeByKey[key]
            if (dirNode != null && dirNode.parent != null) {
                val parent = dirNode.parent
                val removedBytes = dirNode.totalSize
                val removedFiles = dirNode.totalFileCount
                val removedFolders = dirNode.totalFolderCount + 1
                val negCategories = dirNode.categorySizes.mapValues { -it.value }

                parent.children.remove(key)
                propagateMetrics(parent, -removedBytes, -removedFiles, -removedFolders, negCategories)
                scannedFiles = max(0, scannedFiles - removedFiles)
                scannedFolders = max(0, scannedFolders - removedFolders)

                val keysToRemove = HashSet<String>()
                collectSubtreeKeys(dirNode, keysToRemove)
                for (k in keysToRemove) {
                    nodeByKey.remove(k)
                    spaceHogs.remove(k)
                }

                topFiles.removeAll { f ->
                    val fk = keyOf(f.path)
                    val parentKey = keyOf(f.parentPath)
                    fk == key || parentKey in keysToRemove
                }

                if (keyOf(focusPath) in keysToRemove) {
                    focusPath = parent.path
                }
                return removedBytes
            }

            val parentPath = fileSystem.parentPathOf(norm)?.let { normalizeKeyPath(it) } ?: return 0L
            val parentNode = nodeByKey[keyOf(parentPath)] ?: return 0L
            val idx = parentNode.directFiles.indexOfFirst { keyOf(it.path) == key }
            if (idx != -1) {
                val removedFile = parentNode.directFiles.removeAt(idx)
                val negCategories = mapOf(removedFile.category to -removedFile.size)
                parentNode.ownFilesSize = max(0L, parentNode.ownFilesSize - removedFile.size)
                parentNode.ownFileCount = max(0, parentNode.ownFileCount - 1)
                propagateMetrics(parentNode, -removedFile.size, -1, 0, negCategories)
                scannedFiles = max(0, scannedFiles - 1)
                topFiles.removeAll { keyOf(it.path) == key }
                return removedFile.size
            }
        }

        return 0L
    }

    fun getSnapshot(): ScanSnapshot {
        synchronized(lock) {
            val focusNode = nodeByKey[keyOf(focusPath)] ?: rootNode
            val rootSize = max(1L, rootNode.totalSize)
            val parentSize = max(1L, focusNode.totalSize)

            val breadcrumbs = mutableListOf<BreadcrumbItem>()
            var curr: DirNode? = focusNode
            while (curr != null) {
                breadcrumbs.add(0, BreadcrumbItem(name = curr.name, path = curr.path, size = curr.totalSize))
                curr = curr.parent
            }

            val items = ArrayList<ScanItemSnapshot>(focusNode.children.size + focusNode.directFiles.size)

            for (childDir in focusNode.children.values) {
                val dominantCat = FolderClassifier.getDominantCategory(childDir.categorySizes, childDir.specialBadge)
                val preview = childDir.children.values
                    .asSequence()
                    .filter { it.totalSize > 0L }
                    .sortedByDescending { it.totalSize }
                    .take(6)
                    .map { c ->
                        ScanChildPreview(
                            name = c.name,
                            path = c.path,
                            size = c.totalSize,
                            category = FolderClassifier.getDominantCategory(c.categorySizes, c.specialBadge),
                            type = ItemType.FOLDER
                        )
                    }
                    .toList()

                items.add(
                    ScanItemSnapshot(
                        name = childDir.name,
                        path = childDir.path,
                        type = ItemType.FOLDER,
                        size = childDir.totalSize,
                        percentOfParent = if (focusNode.totalSize > 0L) (childDir.totalSize.toFloat() / parentSize) * 100f else 0f,
                        percentOfRoot = if (rootNode.totalSize > 0L) (childDir.totalSize.toFloat() / rootSize) * 100f else 0f,
                        fileCount = childDir.totalFileCount,
                        folderCount = childDir.totalFolderCount,
                        isScanning = childDir.pendingSubdirs > 0,
                        accessDenied = childDir.accessDenied,
                        category = dominantCat,
                        specialBadge = childDir.specialBadge,
                        childrenPreview = preview
                    )
                )
            }

            for (file in focusNode.directFiles) {
                items.add(
                    ScanItemSnapshot(
                        name = file.name,
                        path = file.path,
                        type = ItemType.FILE,
                        size = file.size,
                        percentOfParent = if (focusNode.totalSize > 0L) (file.size.toFloat() / parentSize) * 100f else 0f,
                        percentOfRoot = if (rootNode.totalSize > 0L) (file.size.toFloat() / rootSize) * 100f else 0f,
                        fileCount = 1,
                        folderCount = 0,
                        isScanning = false,
                        category = file.category
                    )
                )
            }

            items.sortByDescending { it.size }
            val boundedItems = if (items.size > 250) items.subList(0, 250).toList() else items

            val hogsList = spaceHogs.values
                .asSequence()
                .filter { it.totalSize > 10L * 1024L * 1024L }
                .sortedByDescending { it.totalSize }
                .take(40)
                .map { h ->
                    SpaceHogEntry(
                        name = h.name,
                        path = h.path,
                        size = h.totalSize,
                        fileCount = h.totalFileCount,
                        kind = h.hogKind ?: "Pasta Pesada",
                        category = FolderClassifier.getDominantCategory(h.categorySizes, h.specialBadge)
                    )
                }
                .toList()

            val topFolders = nodeByKey.values
                .asSequence()
                .filter { it.depth in 1..4 && it.totalSize > 5L * 1024L * 1024L }
                .sortedByDescending { it.totalSize }
                .take(35)
                .map { d ->
                    TopFolderEntry(
                        name = d.name,
                        path = d.path,
                        size = d.totalSize,
                        fileCount = d.totalFileCount,
                        category = FolderClassifier.getDominantCategory(d.categorySizes, d.specialBadge),
                        specialBadge = d.specialBadge
                    )
                }
                .toList()

            val now = pauseStartedAt ?: System.currentTimeMillis()
            val elapsedMs = if (startTimeMs > 0L) max(1L, now - startTimeMs - pausedAccumulatedMs) else 0L
            val filesPerSec = if (elapsedMs > 0L) ((scannedFiles.toDouble() / elapsedMs.toDouble()) * 1000.0).toInt() else 0

            return ScanSnapshot(
                status = status,
                sourceType = fileSystem.sourceType,
                rootPath = rootNode.path,
                focusPath = focusNode.path,
                focusSize = focusNode.totalSize,
                breadcrumbs = breadcrumbs,
                items = boundedItems,
                topFolders = topFolders,
                topFiles = topFiles.toList(),
                spaceHogs = hogsList,
                categoryTotals = categoryTotals.toMap(),
                errorMessage = errorMessage,
                stats = ScanStats(
                    scannedFiles = scannedFiles,
                    scannedFolders = scannedFolders,
                    skippedCount = skippedCount,
                    totalBytes = rootNode.totalSize,
                    elapsedMs = elapsedMs,
                    filesPerSec = filesPerSec,
                    currentPath = currentPath
                )
            )
        }
    }
}
