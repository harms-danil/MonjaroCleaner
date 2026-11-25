package app.mcleaner.privileged

import android.content.Context
import app.mcleaner.core.adb.AdbStart
import app.mcleaner.core.adb.AdbStartResult
import app.mcleaner.ui.settings.SHIZUKU_SHELL_COMMAND
import dadb.AdbKeyPair
import dadb.Dadb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Starts Shizuku without a laptop: MCleaner connects to the head unit's own adbd over the network
 * (127.0.0.1:5555 — available only if the firmware keeps network ADB on, e.g. via MConfig+/engineering menu)
 * with its own ADB key and runs the Shizuku starter. The first connection must be confirmed once on the
 * screen («Разрешить отладку» + «Всегда разрешать»).
 *
 * Security: whoever holds this key gets ADB shell on the unit while network ADB is on. The key lives in
 * MCleaner's private files and never leaves the device.
 */
class AdbAutostart(private val context: Context) {
    private val dir = File(context.filesDir, "adb")
    private val privateKey = File(dir, "adbkey")
    private val publicKey = File(dir, "adbkey.pub")

    private fun keyPair(): AdbKeyPair {
        if (!privateKey.isFile || !publicKey.isFile) {
            dir.mkdirs()
            AdbKeyPair.generate(privateKey, publicKey)
        }
        return AdbKeyPair.read(privateKey, publicKey)
    }

    private fun port(): Int = AdbStart.port(getprop("service.adb.tcp.port"), getprop("persist.adb.tcp.port"))

    /** Connects and runs the Shizuku starter. [authWaitMs]: how long to wait for «Разрешить отладку» on screen. */
    suspend fun startShizuku(authWaitMs: Int = AUTH_WAIT_MS): AdbStartResult = withContext(Dispatchers.IO) {
        try {
            Dadb.create(HOST, port(), keyPair(), connectTimeout = CONNECT_TIMEOUT_MS, socketTimeout = authWaitMs).use { adb ->
                AdbStart.fromOutput(adb.shell(SHIZUKU_SHELL_COMMAND).allOutput)
            }
        } catch (e: Exception) {
            AdbStart.fromError(e)
        }
    }

    /** Only checks that network ADB answers and accepts our key (runs `echo ok`). */
    suspend fun probe(authWaitMs: Int = AUTH_WAIT_MS): AdbStartResult = withContext(Dispatchers.IO) {
        try {
            Dadb.create(HOST, port(), keyPair(), connectTimeout = CONNECT_TIMEOUT_MS, socketTimeout = authWaitMs).use { adb ->
                val out = adb.shell("echo mcleaner-ok").allOutput
                if ("mcleaner-ok" in out) AdbStartResult.Started else AdbStartResult.Failed(out.trim())
            }
        } catch (e: Exception) {
            AdbStart.fromError(e)
        }
    }

    private fun getprop(name: String): String = runCatching {
        val p = ProcessBuilder("getprop", name).redirectErrorStream(true).start()
        p.inputStream.bufferedReader().use { it.readText() }.trim().also { p.waitFor() }
    }.getOrDefault("")

    companion object {
        private const val HOST = "127.0.0.1"
        private const val CONNECT_TIMEOUT_MS = 3_000
        /** Interactive: time to tap «Разрешить» on the screen. */
        const val AUTH_WAIT_MS = 30_000
        /** In the background nobody can confirm: fail fast. */
        const val BACKGROUND_AUTH_WAIT_MS = 8_000
    }
}
