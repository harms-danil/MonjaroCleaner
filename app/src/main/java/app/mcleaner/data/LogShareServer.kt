package app.mcleaner.data

import app.mcleaner.core.log.L
import app.mcleaner.core.log.ShareRoute
import app.mcleaner.domain.LogExporter
import kotlinx.coroutines.runBlocking
import java.io.BufferedOutputStream
import java.io.File
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.security.SecureRandom
import java.util.concurrent.Executors

/**
 * «Журнал на телефон»: a tiny HTTP server in the local network while the QR dialog is open.
 * Only `GET /<random token>/` (a page) and `/<token>/mcleaner-log.zip` are answered; it stops by itself
 * after [LIFETIME_MS]. Plain HTTP inside the car's own Wi-Fi; the token keeps strangers out.
 */
class LogShareServer(private val exporter: LogExporter, private val cacheDir: File) {
    data class Session(val urls: List<String>, val stopsAtMs: Long)

    private var socket: ServerSocket? = null
    private val pool = Executors.newFixedThreadPool(2)

    @Synchronized
    fun start(): Session {
        stop()
        val token = ByteArray(8).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        val server = ServerSocket(0)
        socket = server
        val port = server.localPort
        val stopsAt = System.currentTimeMillis() + LIFETIME_MS
        Thread({
            while (!server.isClosed) {
                val client = try { server.accept() } catch (_: SocketException) { break }
                if (System.currentTimeMillis() > stopsAt) { runCatching { client.close() }; stop(); break }
                pool.execute { handle(client, token) }
            }
        }, "mcleaner-share").apply { isDaemon = true }.start()
        val urls = ShareRoute.orderAddresses(addresses()).map { "http://$it:$port/$token/" }
        L.i("Share", "journal server started on port $port, addresses ${urls.size}")
        return Session(urls, stopsAt)
    }

    @Synchronized
    fun stop() {
        socket?.let { s ->
            runCatching { s.close() }
            L.i("Share", "journal server stopped")
        }
        socket = null
    }

    private fun handle(client: Socket, token: String) {
        client.use { c ->
            runCatching {
                c.soTimeout = 15_000
                val line = c.getInputStream().bufferedReader().readLine() ?: return
                val route = ShareRoute.of(line, token)
                val head = line.startsWith("HEAD")
                L.i("Share", "request from ${c.inetAddress.hostAddress}: $route")
                val out = BufferedOutputStream(c.getOutputStream())
                when (route) {
                    ShareRoute.PAGE -> respond(out, 200, "text/html; charset=utf-8", PAGE.toByteArray(), head)
                    ShareRoute.ZIP -> sendZip(out, head)
                    ShareRoute.NOT_ALLOWED -> respond(out, 405, "text/plain", "Method not allowed".toByteArray(), head)
                    ShareRoute.NOT_FOUND -> respond(out, 404, "text/plain", "Not found".toByteArray(), head)
                }
            }.onFailure { L.w("Share", "request failed", it) }
        }
    }

    private fun sendZip(out: OutputStream, head: Boolean) {
        val f = File(cacheDir, "share-" + System.nanoTime() + ".zip")
        try {
            f.outputStream().use { runBlocking { exporter.writeZip(it) } }
            val name = exporter.fileName()
            out.write(
                ("HTTP/1.1 200 OK\r\nContent-Type: application/zip\r\nContent-Length: ${f.length()}\r\n" +
                    "Content-Disposition: attachment; filename=\"$name\"\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n").toByteArray(),
            )
            if (!head) f.inputStream().use { it.copyTo(out) }
            out.flush()
            L.i("Share", "zip sent: ${f.length()} B")
        } finally {
            f.delete()
        }
    }

    private fun respond(out: OutputStream, code: Int, type: String, body: ByteArray, head: Boolean) {
        val status = when (code) { 200 -> "OK"; 404 -> "Not Found"; else -> "Method Not Allowed" }
        out.write("HTTP/1.1 $code $status\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n".toByteArray())
        if (!head) out.write(body)
        out.flush()
    }

    private fun addresses(): List<Pair<String, String>> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp }.flatMap { ni ->
            ni.inetAddresses.toList().filterIsInstance<Inet4Address>().map { ni.name to it.hostAddress.orEmpty() }
        }
    }.getOrDefault(emptyList())

    companion object {
        const val LIFETIME_MS = 15 * 60_000L

        private val PAGE = """
            <!doctype html><html lang="ru"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
            <title>MCleaner — журнал</title>
            <style>body{font-family:system-ui,sans-serif;margin:24px;max-width:560px;color:#1b1b1f;background:#f6f6fa}
            a.b{display:block;text-align:center;background:#1565c0;color:#fff;padding:16px;border-radius:12px;text-decoration:none;font-size:18px;margin:24px 0}
            p{color:#444;line-height:1.45}</style></head><body>
            <h1>MCleaner — журнал</h1>
            <p>Архив собирается на магнитоле в момент скачивания: журнал MCleaner, сводка об устройстве и настройках, а при запущенном Shizuku — системный журнал, память и накопитель.</p>
            <a class="b" href="${ShareRoute.ZIP_NAME}">Скачать журнал (zip)</a>
            <p>Ссылка работает, пока на магнитоле открыто окно с QR-кодом, и не дольше 15 минут.</p>
            </body></html>
        """.trimIndent()
    }
}
