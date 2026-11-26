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
    fun `only the Wi-Fi address is shown when there is one`() {
        val ifs = listOf("lo" to "127.0.0.1", "radio0" to "192.168.200.2", "wlan0" to "192.168.232.2")
        assertEquals(ShareAddresses(listOf(ShareAddress(NetKind.WIFI, "192.168.232.2")), fallback = false), ShareRoute.pickAddresses(ifs))
    }

    @Test
    fun `hotspot is labelled as such and comes after Wi-Fi`() {
        val ifs = listOf("ap0" to "192.168.44.1", "wlan0" to "192.168.43.12", "rmnet0" to "10.1.2.3")
        assertEquals(
            ShareAddresses(listOf(ShareAddress(NetKind.WIFI, "192.168.43.12"), ShareAddress(NetKind.HOTSPOT, "192.168.44.1")), fallback = false),
            ShareRoute.pickAddresses(ifs),
        )
    }

    @Test
    fun `without Wi-Fi other addresses are offered as a fallback`() {
        val ifs = listOf("lo" to "127.0.0.1", "eth0" to "172.16.0.5")
        assertEquals(ShareAddresses(listOf(ShareAddress(NetKind.OTHER, "172.16.0.5")), fallback = true), ShareRoute.pickAddresses(ifs))
        assertEquals(ShareAddresses(emptyList(), fallback = true), ShareRoute.pickAddresses(listOf("lo" to "127.0.0.1")))
    }
}
