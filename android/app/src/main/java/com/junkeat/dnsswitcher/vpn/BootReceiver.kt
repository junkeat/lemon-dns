package com.junkeat.dnsswitcher.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.junkeat.dnsswitcher.storage.DnsProfileRepository

/**
 * Starts [DnsVpnService] automatically after device boot when "Start on Device Boot" is enabled
 * in [DnsProfileRepository] and the user has already granted VPN consent.
 */
class BootReceiver : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent?) {
    val action = intent?.action ?: return
    if (
      action != Intent.ACTION_BOOT_COMPLETED &&
      action != Intent.ACTION_MY_PACKAGE_REPLACED
    ) {
      return
    }

    val repository = DnsProfileRepository.getInstance(context)
    if (!repository.isStartOnBootEnabled()) {
      return
    }

    try {
      DnsVpnService.start(context)
    } catch (_: Exception) {
      // Guard against OS restrictions if background start is disallowed on custom OEM ROMs
    }
  }
}
