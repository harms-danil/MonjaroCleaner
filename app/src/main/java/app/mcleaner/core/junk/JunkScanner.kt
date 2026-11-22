package app.mcleaner.core.junk

import java.io.File
import java.nio.file.Files

/** Walks [root] without following symlinks, skipping unreadable directories and stopping at [maxDepth]. */
class JunkScanner(private val maxDepth: Int = 12) {
    fun scan(root: File): List<JunkItem> {
        val out = mutableListOf<JunkItem>()
        walkChildren(root, depth = 1, out)
        return out
    }

    private fun walkChildren(dir: File, depth: Int, out: MutableList<JunkItem>) {
        if (depth > maxDepth) return
        val children = dir.listFiles() ?: return
        for (child in children.sortedBy { it.name }) {
            if (Files.isSymbolicLink(child.toPath())) continue
            if (child.isDirectory) {
                val listing = child.listFiles() ?: continue
                if (listing.isEmpty()) {
                    JunkClassifier.classify(child.path, isDirectory = true, isEmptyDir = true)
                        ?.let { out += JunkItem(child.path, 0, it) }
                } else {
                    walkChildren(child, depth + 1, out)
                }
            } else {
                JunkClassifier.classify(child.path, isDirectory = false, isEmptyDir = false)
                    ?.let { out += JunkItem(child.path, child.length(), it) }
            }
        }
    }
}
