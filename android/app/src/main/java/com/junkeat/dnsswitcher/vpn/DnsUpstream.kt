package com.junkeat.dnsswitcher.vpn

/**
 * Represents a plain UDP DNS upstream endpoint resolved from the active DnsProfile.
 */
data class DnsUpstream(
  val id: String,
  val name: String,
  val address: String,
  val secondaryAddress: String? = null,
  val port: Int = 53,
) {
  companion object {
    val CLOUDFLARE = DnsUpstream(
      id = "cloudflare",
      name = "Cloudflare",
      address = "1.1.1.1",
      secondaryAddress = "1.0.0.1",
      port = 53,
    )
  }
}
