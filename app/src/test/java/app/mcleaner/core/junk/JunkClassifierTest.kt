package app.mcleaner.core.junk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JunkClassifierTest {
    private fun file(path: String) = JunkClassifier.classify(path, isDirectory = false, isEmptyDir = false)

    @Test
    fun apk() {
        assertEquals(JunkCategory.APK, file("/sdcard/Download/Navi-1.2.apk"))
        assertEquals(JunkCategory.APK, file("/sdcard/Download/split.APKS"))
    }

    @Test
    fun logs() {
        assertEquals(JunkCategory.LOGS, file("/sdcard/app/debug.log"))
        assertEquals(JunkCategory.LOGS, file("/sdcard/app/debug.log.3"))
        assertEquals(JunkCategory.LOGS, file("/sdcard/Android/data/com.x/files/logs/2026-11-01.txt"))
        assertEquals(JunkCategory.LOGS, file("/sdcard/Android/media/com.x/logs/a.txt"))
    }

    @Test
    fun userLogsFolder_notJunk() {
        assertNull(file("/sdcard/Documents/logs/report.pdf"))
        assertNull(file("/sdcard/DashCam/logs/trip.mp4"))
    }

    @Test
    fun temp() {
        assertEquals(JunkCategory.TEMP, file("/sdcard/x/upload.tmp"))
        assertEquals(JunkCategory.TEMP, file("/sdcard/x/a.temp"))
        assertEquals(JunkCategory.TEMP, file("/sdcard/x/~\$report.docx"))
        assertEquals(JunkCategory.TEMP, file("/sdcard/.tmp/part1"))
    }

    @Test
    fun tildeName_notJunk() {
        assertNull(file("/sdcard/Music/~intro.mp3"))
    }

    @Test
    fun androidDataAndObbDirs_neverEmptyDirJunk() {
        assertNull(JunkClassifier.classify("/sdcard/Android/data", isDirectory = true, isEmptyDir = true))
        assertNull(JunkClassifier.classify("/sdcard/Android/obb", isDirectory = true, isEmptyDir = true))
        assertNull(JunkClassifier.classify("/sdcard/Android", isDirectory = true, isEmptyDir = true))
    }

    @Test
    fun thumbnails() {
        assertEquals(JunkCategory.THUMBNAILS, file("/sdcard/DCIM/.thumbnails/1234.jpg"))
    }

    @Test
    fun emptyDir() {
        assertEquals(JunkCategory.EMPTY_DIRS, JunkClassifier.classify("/sdcard/old", isDirectory = true, isEmptyDir = true))
        assertNull(JunkClassifier.classify("/sdcard/logs", isDirectory = true, isEmptyDir = false))
    }

    @Test
    fun regularPhoto_null() {
        assertNull(file("/sdcard/DCIM/Camera/IMG_0001.jpg"))
        assertNull(file("/sdcard/Music/catalog.txt"))
    }

    @Test
    fun obb_null() {
        assertNull(file("/sdcard/Android/obb/com.game/main.1.com.game.obb"))
        assertNull(file("/sdcard/Android/obb/com.game/patch.tmp"))
        assertNull(JunkClassifier.classify("/sdcard/Android/obb/com.game", isDirectory = true, isEmptyDir = true))
    }
}
