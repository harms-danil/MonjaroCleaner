package app.mcleaner.core.junk

import java.io.File
import java.nio.file.Files

/**
 * Walks [root] without following symlinks, skipping unreadable directories and stopping at [maxDepth].
 * Anything modified less than [minAgeMs] before [now] is skipped: likely a download or a file being written.
 */
class JunkScanner(
    private val maxDepth: Int = 12,
    private val now: () -> Long = System::currentTimeMillis,
    private val minAgeMs: Long = MIN_AGE_MS,
) {
    fun scan(root: File): List<JunkItem> {
        val out = mutableListOf<JunkItem>()
        walkChildren(root, depth = 1, now() - minAgeMs, out)
        return out
    }

    private fun walkChildren(dir: File, depth: Int, cutoff: Long, out: MutableList<JunkItem>) {
        if (depth > maxDepth) return
        val children = dir.listFiles() ?: return
        for (child in children.sortedBy { it.name }) {
            if (Files.isSymbolicLink(child.toPath())) continue
            if (child.isDirectory) {
                val listing = child.listFiles() ?: continue
                if (listing.isEmpty()) {
                    if (child.lastModified() > cutoff) continue
                    JunkClassifier.classify(child.path, isDirectory = true, isEmptyDir = true, topLevel = depth == 1)
                        ?.let { out += JunkItem(child.path, 0, it) }
                } else {
                    walkChildren(child, depth + 1, cutoff, out)
                }
            } else {
                if (child.lastModified() > cutoff) continue
                JunkClassifier.classify(child.path, isDirectory = false, isEmptyDir = false)
                    ?.let { out += JunkItem(child.path, child.length(), it) }
            }
        }
    }

    private companion object {
        const val MIN_AGE_MS = 3_600_000L
    }
}
