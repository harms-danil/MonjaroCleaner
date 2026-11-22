package app.mcleaner.privileged

import android.content.Context
import android.os.IBinder
import androidx.annotation.Keep
import app.mcleaner.IShellService
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * Runs inside a Shizuku-spawned app_process as uid shell. Hidden API restrictions do not apply here.
 */
class ShellUserService() : IShellService.Stub() {

    @Keep
    @Suppress("UNUSED_PARAMETER")
    constructor(context: Context) : this()

    override fun destroy() {
        exitProcess(0)
    }

    override fun exec(command: String): String = try {
        val process = ProcessBuilder("sh", "-c", command).redirectErrorStream(true).start()
        val out = process.inputStream.bufferedReader().readText()
        val finished = process.waitFor(60, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            "124\n$out"
        } else {
            "${process.exitValue()}\n$out"
        }
    } catch (t: Throwable) {
        "-1\n${t.javaClass.simpleName}: ${t.message}"
    }

    override fun deleteCache(packageName: String, userId: Int): Boolean = try {
        val binder = Class.forName("android.os.ServiceManager")
            .getMethod("getService", String::class.java)
            .invoke(null, "package") as IBinder
        val pm = Class.forName("android.content.pm.IPackageManager\$Stub")
            .getMethod("asInterface", IBinder::class.java)
            .invoke(null, binder)!!
        val observer = Class.forName("android.content.pm.IPackageDataObserver")
        pm.javaClass
            .getMethod("deleteApplicationCacheFilesAsUser", String::class.java, Int::class.javaPrimitiveType, observer)
            .invoke(pm, packageName, userId, null)
        true
    } catch (t: Throwable) {
        false
    }
}
