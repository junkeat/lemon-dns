package com.junkeat.dnsswitcher.vpn

/**
 * Low-level IPv4 + UDP packet parser and builder for DNS packets routed through the TUN interface.
 * Strictly avoids logging or inspecting DNS query names.
 */
object UdpPacketCodec {

  private const val IPV4_VERSION = 4
  private const val PROTOCOL_UDP = 17
  private const val DNS_PORT = 53
  private const val MIN_IPV4_HEADER_LENGTH = 20
  private const val UDP_HEADER_LENGTH = 8
  private const val DEFAULT_TTL = 64

  /**
   * Parses a raw IP packet read from the TUN interface.
   * Returns a [DnsQueryPacket] if it is a valid IPv4 UDP packet destined for port 53, or null otherwise.
   */
  fun parseDnsQuery(rawPacket: ByteArray, length: Int): DnsQueryPacket? {
    if (length < MIN_IPV4_HEADER_LENGTH + UDP_HEADER_LENGTH) {
      return null
    }

    val versionAndIhl = rawPacket[0].toInt() and 0xFF
    val version = versionAndIhl ushr 4
    if (version != IPV4_VERSION) {
      return null
    }

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
      sourceIp = sourceIp,
      destinationIp = destinationIp,
      sourcePort = sourcePort,
      destinationPort = destinationPort,
      dnsPayload = dnsPayload,
    )
  }

  /**
   * Builds a raw IPv4 + UDP response packet ready to be written back to the TUN interface.
   * Swaps source and destination addresses and ports from the original [request].
   */
  fun buildDnsResponse(request: DnsQueryPacket, responsePayload: ByteArray, responseLength: Int): ByteArray {
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
    writeUInt16(packet, udpOffset, request.destinationPort) // Source port (53)
    writeUInt16(packet, udpOffset + 2, request.sourcePort) // Destination port (client port)
    writeUInt16(packet, udpOffset + 4, udpLength)
    writeUInt16(packet, udpOffset + 6, 0) // UDP checksum placeholder

    // Copy DNS response payload
    System.arraycopy(responsePayload, 0, packet, udpOffset + UDP_HEADER_LENGTH, responseLength)

    // Compute UDP checksum over pseudo-header + UDP header + payload
    val udpChecksum = computeUdpChecksum(packet, MIN_IPV4_HEADER_LENGTH, udpLength)
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

  private fun computeUdpChecksum(packet: ByteArray, udpOffset: Int, udpLength: Int): Int {
    var sum = 0

    // Pseudo-header: Source IP (4 bytes) + Destination IP (4 bytes)
    sum += readUInt16(packet, 12)
    sum += readUInt16(packet, 14)
    sum += readUInt16(packet, 16)
    sum += readUInt16(packet, 18)

    // Pseudo-header: Protocol (17) + UDP length
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
    // In UDP over IPv4, a calculated checksum of 0x0000 is transmitted as 0xFFFF
    return if (checksum == 0) 0xFFFF else checksum
  }
}
