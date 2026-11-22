package app.mcleaner.core.junk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

class JunkScannerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun write(path: String, bytes: Int = 10): File =
        File(tmp.root, path).apply { parentFile!!.mkdirs(); writeBytes(ByteArray(bytes)) }

    @Test
    fun findsApkLogTempAndEmptyDir() {
        write("Download/a.apk", 100)
        write("app/x.log", 20)
        write("app/y.tmp", 5)
        write("DCIM/photo.jpg", 50)
        File(tmp.root, "empty").mkdirs()

        val items = JunkScanner().scan(tmp.root).associateBy { File(it.path).relativeTo(tmp.root).path }

        assertEquals(setOf("Download/a.apk", "app/x.log", "app/y.tmp", "empty"), items.keys)
        assertEquals(JunkItem(File(tmp.root, "Download/a.apk").path, 100, JunkCategory.APK), items["Download/a.apk"])
        assertEquals(JunkCategory.EMPTY_DIRS, items["empty"]!!.category)
    }

    @Test
    fun rootItself_neverReported() {
        assertTrue(JunkScanner().scan(tmp.root).isEmpty())
    }

    @Test
    fun symlinkLoop_terminates() {
        val dir = File(tmp.root, "d").apply { mkdirs() }
        write("d/x.log")
        Files.createSymbolicLink(File(dir, "loop").toPath(), dir.toPath())

        val items = JunkScanner().scan(tmp.root)

        assertEquals(listOf(File(dir, "x.log").path), items.map { it.path })
    }

    @Test
    fun unreadableDir_skipped() {
        val locked = File(tmp.root, "locked").apply { mkdirs() }
        write("locked/x.log")
        write("open/y.log")
        locked.setReadable(false)
        try {
            val items = JunkScanner().scan(tmp.root)
            assertEquals(listOf(File(tmp.root, "open/y.log").path), items.map { it.path })
        } finally {
            locked.setReadable(true)
        }
    }

    @Test
    fun depthLimited() {
        write("a/b/c/d/deep.log")
        assertTrue(JunkScanner(maxDepth = 2).scan(tmp.root).isEmpty())
    }
}
