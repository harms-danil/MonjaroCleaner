package app.mcleaner.domain

import app.mcleaner.core.junk.JunkCategory
import app.mcleaner.core.junk.JunkItem
import app.mcleaner.core.junk.JunkScanner
import app.mcleaner.core.model.ShellResult
import app.mcleaner.privileged.FakePrivileged
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class JunkCleanerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val later = System.currentTimeMillis() + 2 * 3_600_000L
    private val whitelist = setOf("ru.yandex.yandexnavi")

    private fun cleaner(fake: FakePrivileged) = JunkCleaner(fake, JunkScanner(now = { later }), root = { tmp.root })

    private fun write(path: String): File =
        File(tmp.root, path).apply { parentFile!!.mkdirs(); writeBytes(ByteArray(10)) }

    @Test
    fun scan_dropsWhitelistedAndCarAppFolders() = runTest {
        write("Android/data/ru.yandex.yandexnavi/files/a.log")
        write("Android/media/com.geely.music/b.log")
        File(tmp.root, "Android/media/ru.yandex.yandexnavi/empty").mkdirs()
        write("Android/data/com.example.game/files/c.log")
        write("Download/d.log")
        val root = tmp.root.path
        val fake = FakePrivileged()
        fake.responses["du -s -k '$root/Android/data'/*/cache 2>/dev/null"] = ShellResult(
            0,
            "2048\t$root/Android/data/com.example.game/cache\n100\t$root/Android/data/ru.yandex.yandexnavi/cache\n",
        )

        val paths = cleaner(fake).scan(whitelist).map { it.path.removePrefix("$root/") }.toSet()

        assertEquals(
            setOf("Android/data/com.example.game/files/c.log", "Download/d.log", "Android/data/com.example.game/cache"),
            paths,
        )
    }

    @Test
    fun delete_androidData_viaShell_narrowCommands() = runTest {
        val fake = FakePrivileged()
        val items = listOf(
            JunkItem("/s/Android/data/com.x.y/files/empty", 0, JunkCategory.EMPTY_DIRS),
            JunkItem("/s/Android/data/com.x.y/files/a.log", 10, JunkCategory.LOGS),
            JunkItem("/s/Android/data/com.x.y/cache", 10, JunkCategory.TEMP),
            JunkItem("/s/Android/data/com.geely.radio/cache", 10, JunkCategory.TEMP),
            JunkItem("/s/Android/data/../cache", 10, JunkCategory.TEMP),
        )

        val removed = cleaner(fake).delete(items)

        assertEquals(
            listOf(
                "rmdir '/s/Android/data/com.x.y/files/empty'",
                "rm -f '/s/Android/data/com.x.y/files/a.log'",
                "rm -rf '/s/Android/data/com.x.y/cache'",
            ),
            fake.calls,
        )
        assertEquals(3, removed)
    }

    @Test
    fun delete_local_onlyEmptyDirsAndPlainFiles() = runTest {
        val empty = File(tmp.root, "empty").apply { mkdirs() }
        val notEmpty = File(tmp.root, "full").apply { mkdirs() }
        write("full/keep.jpg")
        val log = write("x.log")
        val dirAsFile = File(tmp.root, "dir.log").apply { mkdirs() }
        File(dirAsFile, "inner.txt").writeText("x")

        val removed = cleaner(FakePrivileged(available = false)).delete(
            listOf(
                JunkItem(empty.path, 0, JunkCategory.EMPTY_DIRS),
                JunkItem(notEmpty.path, 0, JunkCategory.EMPTY_DIRS),
                JunkItem(log.path, 10, JunkCategory.LOGS),
                JunkItem(dirAsFile.path, 10, JunkCategory.LOGS),
            ),
        )

        assertEquals(2, removed)
        assertFalse(empty.exists())
        assertFalse(log.exists())
        assertTrue(File(notEmpty, "keep.jpg").exists())
        assertTrue(File(dirAsFile, "inner.txt").exists())
    }

    @Test
    fun delete_appCacheWithoutShell_refused() = runTest {
        val fake = FakePrivileged(available = false)
        val removed = cleaner(fake).delete(listOf(JunkItem("/s/Android/data/com.x.y/cache", 10, JunkCategory.TEMP)))
        assertEquals(0, removed)
        assertTrue(fake.calls.isEmpty())
    }
}
