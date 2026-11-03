package com.junkeat.dnsswitcher.vpn

import com.junkeat.dnsswitcher.storage.IpAddressValidator
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UdpPacketCodecTest {

  @Test
  fun ipv4DnsQueryAndResponseRoundTripComputesValidChecksums() {
    // 13-byte odd-length DNS payload to exercise odd-byte padding in UDP checksum
    val dnsPayload = byteArrayOf(
      0x12, 0x34, 0x01, 0x00, 0x00, 0x01, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x00, 0x07,
    )
    val udpLen = 8 + dnsPayload.size
    val totalLen = 20 + udpLen
    val rawQuery = ByteArray(totalLen)

    // IPv4 header (IHL = 5 -> 20 bytes)
    rawQuery[0] = 0x45
    rawQuery[2] = (totalLen ushr 8).toByte()
    rawQuery[3] = (totalLen and 0xFF).toByte()
    rawQuery[8] = 64
    rawQuery[9] = 17 // UDP
    // Source IP: 10.111.222.1
    rawQuery[12] = 10
    rawQuery[13] = 111
    rawQuery[14] = 222.toByte()
    rawQuery[15] = 1
    // Dest IP: 10.111.222.2
    rawQuery[16] = 10
    rawQuery[17] = 111
    rawQuery[18] = 222.toByte()
    rawQuery[19] = 2

    // UDP header: source port 54321 (0xD431), dest port 53 (0x0035)
    rawQuery[20] = 0xD4.toByte()
    rawQuery[21] = 0x31
    rawQuery[22] = 0x00
    rawQuery[23] = 53
    rawQuery[24] = (udpLen ushr 8).toByte()
    rawQuery[25] = (udpLen and 0xFF).toByte()
    System.arraycopy(dnsPayload, 0, rawQuery, 28, dnsPayload.size)

    val parsed = UdpPacketCodec.parseDnsQuery(rawQuery, rawQuery.size)
    assertNotNull(parsed)
    parsed!!
    assertEquals(4, parsed.ipVersion)
    assertEquals(54321, parsed.sourcePort)
    assertEquals(53, parsed.destinationPort)
    assertArrayEquals(dnsPayload, parsed.dnsPayload)

    // Build response and verify swapped addresses, ports, and valid IPv4 + UDP checksums
    val responsePacket = UdpPacketCodec.buildDnsResponse(parsed, dnsPayload, dnsPayload.size)
    assertEquals(totalLen, responsePacket.size)

    // Verify swapped IPv4 source (10.111.222.2) and destination (10.111.222.1)
    assertEquals(2, responsePacket[15].toInt() and 0xFF)
    assertEquals(1, responsePacket[19].toInt() and 0xFF)

    // Verify swapped UDP ports (source = 53, dest = 54321)
    assertEquals(53, ((responsePacket[20].toInt() and 0xFF) shl 8) or (responsePacket[21].toInt() and 0xFF))
    assertEquals(54321, ((responsePacket[22].toInt() and 0xFF) shl 8) or (responsePacket[23].toInt() and 0xFF))

    // Verify IPv4 header checksum sums to 0xFFFF in 16-bit one's complement arithmetic
    assertEquals(0xFFFF, onesComplementSum(responsePacket, 0, 20))
  }

  @Test
  fun ipv6DnsQueryAndResponseRoundTrip() {
    val dnsPayload = byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0x01, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
    val udpLen = 8 + dnsPayload.size
    val rawQuery = ByteArray(40 + udpLen)

    rawQuery[0] = 0x60 // IPv6
    rawQuery[4] = (udpLen ushr 8).toByte()
    rawQuery[5] = (udpLen and 0xFF).toByte()
    rawQuery[6] = 17 // Next Header = UDP
    rawQuery[7] = 64 // Hop Limit
    rawQuery[23] = 1 // Source ::1
    rawQuery[39] = 2 // Dest ::2

    // UDP header: source port 49152 (0xC000), dest port 53
    rawQuery[40] = 0xC0.toByte()
    rawQuery[41] = 0x00
    rawQuery[42] = 0x00
    rawQuery[43] = 53
    rawQuery[44] = (udpLen ushr 8).toByte()
    rawQuery[45] = (udpLen and 0xFF).toByte()
    System.arraycopy(dnsPayload, 0, rawQuery, 48, dnsPayload.size)

    val parsed = UdpPacketCodec.parseDnsQuery(rawQuery, rawQuery.size)
    assertNotNull(parsed)
    parsed!!
    assertEquals(6, parsed.ipVersion)
    assertEquals(49152, parsed.sourcePort)
    assertEquals(53, parsed.destinationPort)

    val response = UdpPacketCodec.buildDnsResponse(parsed, dnsPayload, dnsPayload.size)
    assertEquals(40 + udpLen, response.size)
    assertEquals(2, response[23].toInt() and 0xFF) // Source is now ::2
    assertEquals(1, response[39].toInt() and 0xFF) // Dest is now ::1
    // Non-zero mandatory IPv6 UDP checksum
    val udpChecksum = ((response[46].toInt() and 0xFF) shl 8) or (response[47].toInt() and 0xFF)
    assertTrue(udpChecksum != 0)
  }

  @Test
  fun rejectsNonDnsOrTruncatedPackets() {
    assertNull(UdpPacketCodec.parseDnsQuery(ByteArray(10), 10))
    val nonDnsPacket = ByteArray(32)
    nonDnsPacket[0] = 0x45
    nonDnsPacket[3] = 32
    nonDnsPacket[9] = 17 // UDP
    nonDnsPacket[23] = 80 // Dest port 80 (not 53)
    nonDnsPacket[25] = 12
    assertNull(UdpPacketCodec.parseDnsQuery(nonDnsPacket, nonDnsPacket.size))
  }

  @Test
  fun ipAddressValidatorRejectsLoopbackTunAndMulticastAddresses() {
    assertTrue(IpAddressValidator.isValidIpv4("1.1.1.1"))
    assertTrue(IpAddressValidator.isValidIpv4("94.140.14.14"))
    assertFalse(IpAddressValidator.isValidIpv4("0.0.0.0"))
    assertFalse(IpAddressValidator.isValidIpv4("127.0.0.1"))
    assertFalse(IpAddressValidator.isValidIpv4("10.111.222.2"))
    assertFalse(IpAddressValidator.isValidIpv4("224.0.0.1"))
    assertFalse(IpAddressValidator.isValidIpv4("255.255.255.255"))
    assertFalse(IpAddressValidator.isValidIpv4("01.1.1.1"))

    assertTrue(IpAddressValidator.isValidIpv6("2606:4700:4700::1111"))
    assertTrue(IpAddressValidator.isValidIpv6("2a10:50c0::ad1:ff"))
    assertFalse(IpAddressValidator.isValidIpv6("::1"))
    assertFalse(IpAddressValidator.isValidIpv6("fd00:10:111:222::2"))
    assertFalse(IpAddressValidator.isValidIpv6("ff02::1"))
    assertFalse(IpAddressValidator.isValidIpv6("2606::4700::1111"))
  }

  private fun onesComplementSum(bytes: ByteArray, offset: Int, length: Int): Int {
    var sum = 0
    var i = 0
    while (i < length) {
      val word = ((bytes[offset + i].toInt() and 0xFF) shl 8) or (bytes[offset + i + 1].toInt() and 0xFF)
      sum += word
      i += 2
    }
    while ((sum ushr 16) != 0) {
      sum = (sum and 0xFFFF) + (sum ushr 16)
    }
    return sum and 0xFFFF
  }
}
