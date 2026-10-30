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
  val port: Int = 53,
  val isBuiltIn: Boolean = false,
) {

  fun toUpstream(): DnsUpstream = DnsUpstream(
    id = id,
    name = name,
    address = primaryDns,
    secondaryAddress = secondaryDns,
    port = port,
  )

  fun toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("name", name)
    put("primaryDns", primaryDns)
    put("secondaryDns", secondaryDns ?: "")
    put("port", port)
    put("isBuiltIn", isBuiltIn)
  }

  companion object {
    fun fromJson(json: JSONObject): DnsProfile {
      val secondaryRaw = json.optString("secondaryDns", "").trim()
      return DnsProfile(
        id = json.getString("id"),
        name = json.getString("name"),
        primaryDns = json.getString("primaryDns"),
        secondaryDns = secondaryRaw.ifEmpty { null },
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
        port = 53,
        isBuiltIn = true,
      ),
      DnsProfile(
        id = "google",
        name = "Google",
        primaryDns = "8.8.8.8",
        secondaryDns = "8.8.4.4",
        port = 53,
        isBuiltIn = true,
      ),
      DnsProfile(
        id = "quad9",
        name = "Quad9",
        primaryDns = "9.9.9.9",
        secondaryDns = "149.112.112.112",
        port = 53,
        isBuiltIn = true,
      ),
    )
  }
}
