package com.junkeat.dnsswitcher.vpn

/**
 * Decodes an IPv4 or IPv6 + UDP DNS query packet coming from the TUN interface.
 * Never logs or persists any payload data.
 */
class DnsQueryPacket(
  val ipVersion: Int,
  val sourceIp: ByteArray,
  val destinationIp: ByteArray,
  val sourcePort: Int,
  val destinationPort: Int,
  val dnsPayload: ByteArray,
)
