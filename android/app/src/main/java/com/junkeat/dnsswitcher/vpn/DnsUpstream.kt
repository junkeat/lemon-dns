package com.junkeat.dnsswitcher.vpn

/**
 * Represents a plain UDP DNS upstream endpoint resolved from the active DnsProfile.
 */
data class DnsUpstream(
  val id: String,
  val name: String,
  val address: String,
  val secondaryAddress: String? = null,
  val primaryIpv6Address: String? = null,
  val secondaryIpv6Address: String? = null,
  val port: Int = 53,
) {
  companion object {
    val CLOUDFLARE = DnsUpstream(
      id = "cloudflare",
      name = "Cloudflare",
      address = "1.1.1.1",
      secondaryAddress = "1.0.0.1",
      primaryIpv6Address = "2606:4700:4700::1111",
      secondaryIpv6Address = "2606:4700:4700::1001",
      port = 53,
    )
  }
}
