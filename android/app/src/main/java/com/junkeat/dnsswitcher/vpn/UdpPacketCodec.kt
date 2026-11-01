package com.junkeat.dnsswitcher.vpn

/**
 * Low-level IPv4 & IPv6 + UDP packet parser and builder for DNS packets routed through the TUN interface.
 * Strictly avoids logging or inspecting DNS query names.
 */
object UdpPacketCodec {

  private const val IPV4_VERSION = 4
  private const val IPV6_VERSION = 6
  private const val PROTOCOL_UDP = 17
  private const val DNS_PORT = 53
  private const val MIN_IPV4_HEADER_LENGTH = 20
  private const val IPV6_HEADER_LENGTH = 40
  private const val UDP_HEADER_LENGTH = 8
  private const val DEFAULT_TTL = 64

  /**
   * Parses a raw IPv4 or IPv6 packet read from the TUN interface.
   * Returns a [DnsQueryPacket] if it is a valid UDP packet destined for port 53, or null otherwise.
   */
  fun parseDnsQuery(rawPacket: ByteArray, length: Int): DnsQueryPacket? {
    if (length < MIN_IPV4_HEADER_LENGTH + UDP_HEADER_LENGTH) {
      return null
    }

    val version = (rawPacket[0].toInt() and 0xFF) ushr 4
    return when (version) {
      IPV4_VERSION -> parseIpv4DnsQuery(rawPacket, length)
      IPV6_VERSION -> parseIpv6DnsQuery(rawPacket, length)
      else -> null
    }
  }

  private fun parseIpv4DnsQuery(rawPacket: ByteArray, length: Int): DnsQueryPacket? {
    val versionAndIhl = rawPacket[0].toInt() and 0xFF
    val ihlBytes = (versionAndIhl and 0x0F) * 4
    if (ihlBytes < MIN_IPV4_HEADER_LENGTH || length < ihlBytes + UDP_HEADER_LENGTH) {
      return null
    }

    val protocol = rawPacket[9].toInt() and 0xFF
    if (protocol != PROTOCOL_UDP) {
      return null
    }

    val totalLength = readUInt16(rawPacket, 2)
    val packetEnd = minOf(length, totalLength)
    if (packetEnd < ihlBytes + UDP_HEADER_LENGTH) {
      return null
    }

    val sourcePort = readUInt16(rawPacket, ihlBytes)
    val destinationPort = readUInt16(rawPacket, ihlBytes + 2)
    if (destinationPort != DNS_PORT) {
      return null
    }

    val udpLength = readUInt16(rawPacket, ihlBytes + 4)
    val payloadLength = udpLength - UDP_HEADER_LENGTH
    val payloadOffset = ihlBytes + UDP_HEADER_LENGTH
    if (payloadLength <= 0 || payloadOffset + payloadLength > packetEnd) {
      return null
    }

    val sourceIp = rawPacket.copyOfRange(12, 16)
    val destinationIp = rawPacket.copyOfRange(16, 20)
    val dnsPayload = rawPacket.copyOfRange(payloadOffset, payloadOffset + payloadLength)

    return DnsQueryPacket(
      ipVersion = IPV4_VERSION,
      sourceIp = sourceIp,
      destinationIp = destinationIp,
      sourcePort = sourcePort,
      destinationPort = destinationPort,
      dnsPayload = dnsPayload,
    )
  }

  private fun parseIpv6DnsQuery(rawPacket: ByteArray, length: Int): DnsQueryPacket? {
    if (length < IPV6_HEADER_LENGTH + UDP_HEADER_LENGTH) {
      return null
    }

    val nextHeader = rawPacket[6].toInt() and 0xFF
    if (nextHeader != PROTOCOL_UDP) {
      return null
    }

    val payloadTotalLength = readUInt16(rawPacket, 4)
    val packetEnd = minOf(length, IPV6_HEADER_LENGTH + payloadTotalLength)
    if (packetEnd < IPV6_HEADER_LENGTH + UDP_HEADER_LENGTH) {
      return null
    }

    val sourcePort = readUInt16(rawPacket, IPV6_HEADER_LENGTH)
    val destinationPort = readUInt16(rawPacket, IPV6_HEADER_LENGTH + 2)
    if (destinationPort != DNS_PORT) {
      return null
    }

    val udpLength = readUInt16(rawPacket, IPV6_HEADER_LENGTH + 4)
    val dnsLength = udpLength - UDP_HEADER_LENGTH
    val dnsOffset = IPV6_HEADER_LENGTH + UDP_HEADER_LENGTH
    if (dnsLength <= 0 || dnsOffset + dnsLength > packetEnd) {
      return null
    }

    val sourceIp = rawPacket.copyOfRange(8, 24)
    val destinationIp = rawPacket.copyOfRange(24, 40)
    val dnsPayload = rawPacket.copyOfRange(dnsOffset, dnsOffset + dnsLength)

    return DnsQueryPacket(
      ipVersion = IPV6_VERSION,
      sourceIp = sourceIp,
      destinationIp = destinationIp,
      sourcePort = sourcePort,
      destinationPort = destinationPort,
      dnsPayload = dnsPayload,
    )
  }

  /**
   * Builds a raw IPv4 or IPv6 + UDP response packet ready to be written back to the TUN interface.
   * Swaps source and destination addresses and ports from the original [request].
   */
  fun buildDnsResponse(
    request: DnsQueryPacket,
    responsePayload: ByteArray,
    responseLength: Int,
  ): ByteArray {
    return if (request.ipVersion == IPV6_VERSION) {
      buildIpv6DnsResponse(request, responsePayload, responseLength)
    } else {
      buildIpv4DnsResponse(request, responsePayload, responseLength)
    }
  }

  private fun buildIpv4DnsResponse(
    request: DnsQueryPacket,
    responsePayload: ByteArray,
    responseLength: Int,
  ): ByteArray {
    val udpLength = UDP_HEADER_LENGTH + responseLength
    val totalLength = MIN_IPV4_HEADER_LENGTH + udpLength
    val packet = ByteArray(totalLength)

    // IPv4 Header (20 bytes, no options)
    packet[0] = ((IPV4_VERSION shl 4) or (MIN_IPV4_HEADER_LENGTH / 4)).toByte()
    packet[1] = 0 // DSCP / ECN
    writeUInt16(packet, 2, totalLength)
    writeUInt16(packet, 4, 0) // Identification
    writeUInt16(packet, 6, 0x4000) // Flags: Don't Fragment (DF), Fragment Offset = 0
    packet[8] = DEFAULT_TTL.toByte()
    packet[9] = PROTOCOL_UDP.toByte()
    writeUInt16(packet, 10, 0) // Header checksum placeholder

    // Swap IP addresses: response source is request destination, response destination is request source
    System.arraycopy(request.destinationIp, 0, packet, 12, 4)
    System.arraycopy(request.sourceIp, 0, packet, 16, 4)

    // Compute IPv4 header checksum
    val ipChecksum = computeIpHeaderChecksum(packet, MIN_IPV4_HEADER_LENGTH)
    writeUInt16(packet, 10, ipChecksum)

    // UDP Header (8 bytes)
    val udpOffset = MIN_IPV4_HEADER_LENGTH
    writeUInt16(packet, udpOffset, request.destinationPort)
    writeUInt16(packet, udpOffset + 2, request.sourcePort)
    writeUInt16(packet, udpOffset + 4, udpLength)
    writeUInt16(packet, udpOffset + 6, 0)

    // Copy DNS response payload
    System.arraycopy(responsePayload, 0, packet, udpOffset + UDP_HEADER_LENGTH, responseLength)

    // Compute UDP checksum over pseudo-header + UDP header + payload
    val udpChecksum = computeUdpChecksum(
      packet = packet,
      ipAddressOffset = 12,
      ipAddressBytes = 8,
      udpOffset = MIN_IPV4_HEADER_LENGTH,
      udpLength = udpLength,
    )
    writeUInt16(packet, udpOffset + 6, udpChecksum)

    return packet
  }

  private fun buildIpv6DnsResponse(
    request: DnsQueryPacket,
    responsePayload: ByteArray,
    responseLength: Int,
  ): ByteArray {
    val udpLength = UDP_HEADER_LENGTH + responseLength
    val totalLength = IPV6_HEADER_LENGTH + udpLength
    val packet = ByteArray(totalLength)

    // IPv6 Header (40 bytes)
    packet[0] = (IPV6_VERSION shl 4).toByte()
    packet[1] = 0
    packet[2] = 0
    packet[3] = 0
    writeUInt16(packet, 4, udpLength) // Payload length
    packet[6] = PROTOCOL_UDP.toByte() // Next Header (UDP = 17)
    packet[7] = DEFAULT_TTL.toByte() // Hop Limit

    // Swap 16-byte IPv6 addresses
    System.arraycopy(request.destinationIp, 0, packet, 8, 16)
    System.arraycopy(request.sourceIp, 0, packet, 24, 16)

    // UDP Header (8 bytes)
    val udpOffset = IPV6_HEADER_LENGTH
    writeUInt16(packet, udpOffset, request.destinationPort)
    writeUInt16(packet, udpOffset + 2, request.sourcePort)
    writeUInt16(packet, udpOffset + 4, udpLength)
    writeUInt16(packet, udpOffset + 6, 0)

    // Copy DNS response payload
    System.arraycopy(responsePayload, 0, packet, udpOffset + UDP_HEADER_LENGTH, responseLength)

    // Compute mandatory IPv6 UDP checksum over 16-byte source/dest pseudo-header + UDP + payload
    val udpChecksum = computeUdpChecksum(
      packet = packet,
      ipAddressOffset = 8,
      ipAddressBytes = 32,
      udpOffset = IPV6_HEADER_LENGTH,
      udpLength = udpLength,
    )
    writeUInt16(packet, udpOffset + 6, udpChecksum)

    return packet
  }

  private fun readUInt16(buffer: ByteArray, offset: Int): Int {
    val high = buffer[offset].toInt() and 0xFF
    val low = buffer[offset + 1].toInt() and 0xFF
    return (high shl 8) or low
  }

  private fun writeUInt16(buffer: ByteArray, offset: Int, value: Int) {
    buffer[offset] = ((value ushr 8) and 0xFF).toByte()
    buffer[offset + 1] = (value and 0xFF).toByte()
  }

  private fun computeIpHeaderChecksum(packet: ByteArray, headerLength: Int): Int {
    var sum = 0
    var i = 0
    while (i < headerLength) {
      sum += readUInt16(packet, i)
      i += 2
    }
    while ((sum ushr 16) != 0) {
      sum = (sum and 0xFFFF) + (sum ushr 16)
    }
    return sum.inv() and 0xFFFF
  }

  private fun computeUdpChecksum(
    packet: ByteArray,
    ipAddressOffset: Int,
    ipAddressBytes: Int,
    udpOffset: Int,
    udpLength: Int,
  ): Int {
    var sum = 0

    // Pseudo-header: Source IP + Destination IP (8 bytes for IPv4, 32 bytes for IPv6)
    var addrIdx = 0
    while (addrIdx < ipAddressBytes) {
      sum += readUInt16(packet, ipAddressOffset + addrIdx)
      addrIdx += 2
    }

    // Pseudo-header: Protocol/Next-Header (17) + UDP length
    sum += PROTOCOL_UDP
    sum += udpLength

    // UDP header + payload
    var i = 0
    while (i + 1 < udpLength) {
      sum += readUInt16(packet, udpOffset + i)
      i += 2
    }
    if (i < udpLength) {
      val lastByte = packet[udpOffset + i].toInt() and 0xFF
      sum += lastByte shl 8
    }

    while ((sum ushr 16) != 0) {
      sum = (sum and 0xFFFF) + (sum ushr 16)
    }

    val checksum = sum.inv() and 0xFFFF
    return if (checksum == 0) 0xFFFF else checksum
  }
}
