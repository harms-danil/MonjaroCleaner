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

    /** Files written by the test are "now"; the scanner looks two hours later so they are old enough. */
    private val later = System.currentTimeMillis() + 2 * 3_600_000L

    private fun scanner(maxDepth: Int = 12) = JunkScanner(maxDepth = maxDepth, now = { later })

    private fun write(path: String, bytes: Int = 10): File =
        File(tmp.root, path).apply { parentFile!!.mkdirs(); writeBytes(ByteArray(bytes)) }

    @Test
    fun findsApkLogTempAndEmptyDir() {
        write("Download/a.apk", 100)
        write("app/x.log", 20)
        write("app/y.tmp", 5)
        write("DCIM/photo.jpg", 50)
        File(tmp.root, "empty").mkdirs()

        val items = scanner().scan(tmp.root).associateBy { File(it.path).relativeTo(tmp.root).path }

        assertEquals(setOf("Download/a.apk", "app/x.log", "app/y.tmp", "empty"), items.keys)
        assertEquals(JunkItem(File(tmp.root, "Download/a.apk").path, 100, JunkCategory.APK), items["Download/a.apk"])
        assertEquals(JunkCategory.EMPTY_DIRS, items["empty"]!!.category)
    }

    @Test
    fun rootItself_neverReported() {
        assertTrue(scanner().scan(tmp.root).isEmpty())
    }

    @Test
    fun symlinkLoop_terminates() {
        val dir = File(tmp.root, "d").apply { mkdirs() }
        write("d/x.log")
        Files.createSymbolicLink(File(dir, "loop").toPath(), dir.toPath())

        val items = scanner().scan(tmp.root)

        assertEquals(listOf(File(dir, "x.log").path), items.map { it.path })
    }

    @Test
    fun unreadableDir_skipped() {
        val locked = File(tmp.root, "locked").apply { mkdirs() }
        write("locked/x.log")
        write("open/y.log")
        locked.setReadable(false)
        try {
            val items = scanner().scan(tmp.root)
            assertEquals(listOf(File(tmp.root, "open/y.log").path), items.map { it.path })
        } finally {
            locked.setReadable(true)
        }
    }

    @Test
    fun depthLimited() {
        write("a/b/c/d/deep.log")
        assertTrue(scanner(maxDepth = 2).scan(tmp.root).isEmpty())
    }

    @Test
    fun recentlyModified_skipped() {
        val now = System.currentTimeMillis()
        write("Download/old.apk").setLastModified(now - 2 * 3_600_000L)
        write("Download/partial.apk").setLastModified(now - 10 * 60_000L)
        File(tmp.root, "freshdir").apply { mkdirs(); setLastModified(now - 60_000L) }
        File(tmp.root, "olddir").apply { mkdirs(); setLastModified(now - 3 * 3_600_000L) }

        val items = JunkScanner(now = { now }).scan(tmp.root).map { File(it.path).relativeTo(tmp.root).path }.toSet()

        assertEquals(setOf("Download/old.apk", "olddir"), items)
    }
}
