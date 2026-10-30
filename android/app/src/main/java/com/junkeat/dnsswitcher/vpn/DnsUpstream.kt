package com.junkeat.dnsswitcher.vpn

/**
 * Represents a plain UDP DNS upstream endpoint.
 */
data class DnsUpstream(
  val id: String,
  val name: String,
  val address: String,
  val port: Int = 53,
) {
  companion object {
    val CLOUDFLARE = DnsUpstream(
      id = "cloudflare",
      name = "Cloudflare",
      address = "1.1.1.1",
      port = 53,
    )

    val GOOGLE = DnsUpstream(
      id = "google",
      name = "Google",
      address = "8.8.8.8",
      port = 53,
    )

    val HARDCODED_UPSTREAMS: List<DnsUpstream> = listOf(CLOUDFLARE, GOOGLE)
  }
}
