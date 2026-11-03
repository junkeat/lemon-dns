package com.junkeat.dnsswitcher.storage

/**
 * Validates IPv4 addresses and UDP ports for DNS profiles.
 */
object IpAddressValidator {

  fun isValidIpv4(input: String): Boolean {
    val trimmed = input.trim()
    val parts = trimmed.split('.')
    if (parts.size != 4) {
      return false
    }
    for (part in parts) {
      if (part.isEmpty() || part.length > 3) {
        return false
      }
      if (part.length > 1 && part.startsWith('0')) {
        return false
      }
      val value = part.toIntOrNull() ?: return false
      if (value !in 0..255) {
        return false
      }
    }
    // Disallow 0.0.0.0/8, loopback 127.0.0.0/8, multicast/broadcast 224.0.0.0+, and our virtual TUN subnet 10.111.222.x
    val firstOctet = parts[0].toInt()
    if (firstOctet == 0 || firstOctet == 127 || firstOctet >= 224 || trimmed.startsWith("10.111.222.")) {
      return false
    }
    return true
  }

  fun isValidPort(port: Int): Boolean = port in 1..65535
}
