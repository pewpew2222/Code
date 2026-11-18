package com.chaptervoice.app.core

/**
 * A set of blocked domains. A host is blocked when it, or any parent domain of it,
 * is in the set: `ads.example.com` is blocked by `example.com` in the list.
 */
class HostBlocklist(private val domains: Set<String>) {

    val size: Int get() = domains.size

    fun isBlocked(host: String?): Boolean {
        var current = host?.lowercase()?.trimEnd('.')?.takeIf { it.isNotEmpty() } ?: return false
        while (true) {
            if (current in domains) return true
            val dot = current.indexOf('.')
            if (dot < 0) return false
            current = current.substring(dot + 1)
            if (!current.contains('.')) return false // never match a bare top-level domain
        }
    }

    companion object {
        val EMPTY = HostBlocklist(emptySet())

        private val NOT_DOMAINS = setOf(
            "localhost", "localhost.localdomain", "local", "broadcasthost", "0.0.0.0",
        )

        /** Reads plain domain lists and hosts files (`0.0.0.0 ads.example.com`). */
        fun parse(lines: Sequence<String>): HostBlocklist {
            val domains = HashSet<String>(100_000)
            for (raw in lines) {
                val parts = raw.substringBefore('#').trim().split(Regex("\\s+"))
                val domain = when {
                    parts.size >= 2 && (parts[0] == "0.0.0.0" || parts[0] == "127.0.0.1") -> parts[1]
                    parts.size == 1 -> parts[0]
                    else -> continue
                }.lowercase().trimEnd('.')
                if (domain.contains('.') && domain !in NOT_DOMAINS && !domain.startsWith("ip6-")) {
                    domains += domain
                }
            }
            return HostBlocklist(domains)
        }
    }
}
