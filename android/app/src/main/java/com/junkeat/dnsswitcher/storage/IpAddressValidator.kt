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

  fun isValidIpv6(input: String): Boolean {
    val trimmed = input.trim().lowercase()
    if (!trimmed.contains(':') || trimmed.startsWith("fd00:10:111:222:") || trimmed == "::" || trimmed == "::1") {
      return false
    }
    // Split on "::" (at most one zero-compression marker allowed in IPv6)
    val doubleColonParts = trimmed.split("::", limit = 3)
    if (doubleColonParts.size > 2) {
      return false
    }
    val leftGroups = if (doubleColonParts[0].isEmpty()) emptyList() else doubleColonParts[0].split(':')
    val rightGroups = if (doubleColonParts.size == 2 && doubleColonParts[1].isNotEmpty()) {
      doubleColonParts[1].split(':')
    } else {
      emptyList()
    }
    val totalGroups = leftGroups.size + rightGroups.size
    if (doubleColonParts.size == 1 && totalGroups != 8) {
      return false
    }
    if (doubleColonParts.size == 2 && totalGroups > 7) {
      return false
    }
    for (group in leftGroups + rightGroups) {
      if (group.isEmpty() || group.length > 4) {
        return false
      }
      if (!group.all { it in '0'..'9' || it in 'a'..'f' }) {
        return false
      }
    }
    // Disallow multicast ff00::/8
    if (trimmed.startsWith("ff")) {
      return false
    }
    return true
  }

  fun isValidPort(port: Int): Boolean = port in 1..65535
}
