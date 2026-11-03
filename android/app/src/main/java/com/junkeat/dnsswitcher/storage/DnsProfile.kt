package com.junkeat.dnsswitcher.storage

import com.junkeat.dnsswitcher.vpn.DnsUpstream
import org.json.JSONObject

/**
 * User-configurable or preset DNS profile stored natively on the device.
 */
data class DnsProfile(
  val id: String,
  val name: String,
  val primaryDns: String,
  val secondaryDns: String? = null,
  val primaryIpv6Dns: String? = null,
  val secondaryIpv6Dns: String? = null,
  val port: Int = 53,
  val isBuiltIn: Boolean = false,
) {

  fun toUpstream(): DnsUpstream = DnsUpstream(
    id = id,
    name = name,
    address = primaryDns,
    secondaryAddress = secondaryDns,
    primaryIpv6Address = primaryIpv6Dns,
    secondaryIpv6Address = secondaryIpv6Dns,
    port = port,
  )

  fun toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("name", name)
    put("primaryDns", primaryDns)
    put("secondaryDns", secondaryDns ?: "")
    put("primaryIpv6Dns", primaryIpv6Dns ?: "")
    put("secondaryIpv6Dns", secondaryIpv6Dns ?: "")
    put("port", port)
    put("isBuiltIn", isBuiltIn)
  }

  companion object {
    fun fromJson(json: JSONObject): DnsProfile {
      val secondaryRaw = json.optString("secondaryDns", "").trim()
      val primaryV6Raw = json.optString("primaryIpv6Dns", "").trim()
      val secondaryV6Raw = json.optString("secondaryIpv6Dns", "").trim()
      return DnsProfile(
        id = json.getString("id"),
        name = json.getString("name"),
        primaryDns = json.getString("primaryDns"),
        secondaryDns = secondaryRaw.ifEmpty { null },
        primaryIpv6Dns = primaryV6Raw.ifEmpty { null },
        secondaryIpv6Dns = secondaryV6Raw.ifEmpty { null },
        port = json.optInt("port", 53),
        isBuiltIn = json.optBoolean("isBuiltIn", false),
      )
    }

    fun defaultPresets(): List<DnsProfile> = listOf(
      DnsProfile(
        id = "cloudflare",
        name = "Cloudflare",
        primaryDns = "1.1.1.1",
        secondaryDns = "1.0.0.1",
        primaryIpv6Dns = "2606:4700:4700::1111",
        secondaryIpv6Dns = "2606:4700:4700::1001",
        port = 53,
        isBuiltIn = true,
      ),
      DnsProfile(
        id = "google",
        name = "Google",
        primaryDns = "8.8.8.8",
        secondaryDns = "8.8.4.4",
        primaryIpv6Dns = "2001:4860:4860::8888",
        secondaryIpv6Dns = "2001:4860:4860::8844",
        port = 53,
        isBuiltIn = true,
      ),
      DnsProfile(
        id = "quad9",
        name = "Quad9",
        primaryDns = "9.9.9.9",
        secondaryDns = "149.112.112.112",
        primaryIpv6Dns = "2620:fe::fe",
        secondaryIpv6Dns = "2620:fe::9",
        port = 53,
        isBuiltIn = true,
      ),
      DnsProfile(
        id = "adguard",
        name = "AdGuard DNS (AdBlock)",
        primaryDns = "94.140.14.14",
        secondaryDns = "94.140.15.15",
        primaryIpv6Dns = "2a10:50c0::ad1:ff",
        secondaryIpv6Dns = "2a10:50c0::ad2:ff",
        port = 53,
        isBuiltIn = true,
      ),
      DnsProfile(
        id = "controld-ads",
        name = "Control D (AdBlock)",
        primaryDns = "76.76.2.2",
        secondaryDns = "76.76.10.2",
        primaryIpv6Dns = "2606:1a40::2",
        secondaryIpv6Dns = "2606:1a40:1::2",
        port = 53,
        isBuiltIn = true,
      ),
    )
  }
}
