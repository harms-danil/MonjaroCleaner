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

        /** (interface name, IPv4) → addresses a phone can reach: Wi-Fi and hotspot first, loopback dropped. */
        fun orderAddresses(interfaces: List<Pair<String, String>>): List<String> =
            interfaces.filter { (_, ip) -> !ip.startsWith("127.") }
                .sortedBy { (name, _) -> if (name.startsWith("wlan") || name.startsWith("ap") || name.startsWith("swlan") || name.startsWith("softap")) 0 else 1 }
                .map { it.second }
                .distinct()
    }
}
