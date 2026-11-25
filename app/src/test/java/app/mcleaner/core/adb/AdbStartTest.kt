package app.mcleaner.core.adb

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException

class AdbStartTest {
    @Test
    fun `port comes from adb properties, 5555 otherwise`() {
        assertEquals(5555, AdbStart.port(service = "", persist = ""))
        assertEquals(5037, AdbStart.port(service = "5037", persist = "5555"))
        assertEquals(5556, AdbStart.port(service = "-1", persist = "5556"))
        assertEquals(5555, AdbStart.port(service = "abc", persist = "0"))
    }

    @Test
    fun `starter output decides success`() {
        val ok = "info: starter begin\ninfo: shizuku_server pid is 4512\ninfo: shizuku_starter exit with 0\n"
        assertEquals(AdbStartResult.Started, AdbStart.fromOutput(ok))
        val bad = "/system/bin/sh: /data/app/x/lib/arm64/libshizuku.so: not found\n"
        assertEquals(AdbStartResult.Failed(bad.trim()), AdbStart.fromOutput(bad))
    }

    @Test
    fun `connection errors map to what the user must do`() {
        assertEquals(AdbStartResult.NoNetworkAdb, AdbStart.fromError(ConnectException("Connection refused")))
        assertEquals(AdbStartResult.NotAuthorized, AdbStart.fromError(SocketTimeoutException("Read timed out")))
        assertEquals(AdbStartResult.Failed("boom"), AdbStart.fromError(IOException("boom")))
    }

    @Test
    fun `errors wrapped by the adb client are unwrapped`() {
        val wrapped = IOException("Failed to connect to 127.0.0.1:5555", ConnectException("Connection refused"))
        assertEquals(AdbStartResult.NoNetworkAdb, AdbStart.fromError(wrapped))
        assertEquals(AdbStartResult.NotAuthorized, AdbStart.fromError(RuntimeException("x", SocketTimeoutException("t"))))
    }
}
