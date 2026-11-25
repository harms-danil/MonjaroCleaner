package app.mcleaner.core.adb

import java.net.ConnectException
import java.net.SocketTimeoutException

/** Outcome of starting Shizuku through the head unit's own network ADB. */
sealed interface AdbStartResult {
    data object Started : AdbStartResult

    /** adbd does not listen on the network port: network ADB is off (or off again after a reboot). */
    data object NoNetworkAdb : AdbStartResult

    /** adbd is there but did not accept our key: «Разрешить отладку?» was not confirmed on the screen. */
    data object NotAuthorized : AdbStartResult

    data class Failed(val details: String) : AdbStartResult
}

object AdbStart {
    const val DEFAULT_PORT = 5555

    /** `service.adb.tcp.port` (set by `adb tcpip`) wins over `persist.adb.tcp.port`; else 5555. */
    fun port(service: String, persist: String): Int =
        listOf(service, persist).firstNotNullOfOrNull { it.trim().toIntOrNull()?.takeIf { p -> p in 1..65535 } } ?: DEFAULT_PORT

    fun fromOutput(output: String): AdbStartResult =
        if ("shizuku_starter exit with 0" in output || "shizuku_server pid is" in output) AdbStartResult.Started
        else AdbStartResult.Failed(output.trim().takeLast(300))

    /** dadb wraps socket errors (e.g. AdbConnectException("Failed to connect…", ConnectException)): look at the causes. */
    fun fromError(e: Throwable): AdbStartResult {
        val chain = generateSequence(e) { it.cause }.take(8).toList()
        return when {
            chain.any { it is ConnectException } -> AdbStartResult.NoNetworkAdb
            chain.any { it is SocketTimeoutException } -> AdbStartResult.NotAuthorized
            else -> AdbStartResult.Failed(e.message ?: e.javaClass.simpleName)
        }
    }
}
