package app.mcleaner.core.log

import org.junit.Assert.assertEquals
import org.junit.Test

class ShareRouteTest {
    private val token = "a1b2c3d4e5f60718"

    @Test
    fun `only paths under the secret token are served`() {
        assertEquals(ShareRoute.PAGE, ShareRoute.of("GET /$token/ HTTP/1.1", token))
        assertEquals(ShareRoute.PAGE, ShareRoute.of("GET /$token HTTP/1.1", token))
        assertEquals(ShareRoute.ZIP, ShareRoute.of("GET /$token/mcleaner-log.zip HTTP/1.1", token))
        assertEquals(ShareRoute.NOT_FOUND, ShareRoute.of("GET / HTTP/1.1", token))
        assertEquals(ShareRoute.NOT_FOUND, ShareRoute.of("GET /wrong/mcleaner-log.zip HTTP/1.1", token))
        assertEquals(ShareRoute.NOT_FOUND, ShareRoute.of("GET /$token/../etc HTTP/1.1", token))
        assertEquals(ShareRoute.NOT_FOUND, ShareRoute.of("garbage", token))
    }

    @Test
    fun `only GET and HEAD are accepted`() {
        assertEquals(ShareRoute.NOT_ALLOWED, ShareRoute.of("POST /$token/ HTTP/1.1", token))
        assertEquals(ShareRoute.ZIP, ShareRoute.of("HEAD /$token/mcleaner-log.zip HTTP/1.1", token))
    }

    @Test
    fun `phone-reachable addresses come first`() {
        val ifs = listOf("lo" to "127.0.0.1", "rmnet0" to "10.1.2.3", "wlan0" to "192.168.43.12", "ap0" to "192.168.44.1")
        assertEquals(listOf("192.168.43.12", "192.168.44.1", "10.1.2.3"), ShareRoute.orderAddresses(ifs))
    }
}
