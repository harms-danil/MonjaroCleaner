package app.mcleaner.core.log

/** Requests to the «journal to phone» server: everything is under a random token; nothing else is answered. */
enum class ShareRoute {
    PAGE, ZIP, NOT_FOUND, NOT_ALLOWED;

    companion object {
        const val ZIP_NAME = "mcleaner-log.zip"

        /** [requestLine]: `GET /path HTTP/1.1`. */
        fun of(requestLine: String, token: String): ShareRoute {
            val parts = requestLine.trim().split(' ')
            if (parts.size < 2) return NOT_FOUND
            if (parts[0] != "GET" && parts[0] != "HEAD") return NOT_ALLOWED
            return when (parts[1].substringBefore('?')) {
                "/$token", "/$token/" -> PAGE
                "/$token/$ZIP_NAME" -> ZIP
                else -> NOT_FOUND
            }
        }

        /**
         * (interface name, IPv4) → what to show the phone. Only Wi-Fi and the head unit's own hotspot are
         * reachable from a phone; modem, mobile-data and other internal links are shown only when there is
         * no Wi-Fi at all, as a fallback.
         */
        fun pickAddresses(interfaces: List<Pair<String, String>>): ShareAddresses {
            val usable = interfaces.filter { (_, ip) -> !ip.startsWith("127.") }.distinctBy { it.second }
            val wifi = usable.mapNotNull { (name, ip) -> kindOf(name)?.let { ShareAddress(it, ip) } }
                .sortedBy { it.kind.ordinal }
            if (wifi.isNotEmpty()) return ShareAddresses(wifi, fallback = false)
            return ShareAddresses(usable.map { ShareAddress(NetKind.OTHER, it.second) }, fallback = true)
        }

        private fun kindOf(name: String): NetKind? = when {
            name.startsWith("ap") || name.startsWith("swlan") || name.startsWith("softap") -> NetKind.HOTSPOT
            name.startsWith("wlan") -> NetKind.WIFI
            else -> null
        }
    }
}

enum class NetKind { WIFI, HOTSPOT, OTHER }

data class ShareAddress(val kind: NetKind, val ip: String)

/** [fallback]: no Wi-Fi; these addresses may not be reachable from a phone. */
data class ShareAddresses(val addresses: List<ShareAddress>, val fallback: Boolean)
