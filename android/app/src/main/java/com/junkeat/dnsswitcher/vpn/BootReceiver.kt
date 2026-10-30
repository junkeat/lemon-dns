package com.junkeat.dnsswitcher.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
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

    // Only start if VPN permission was already granted by the user
    if (VpnService.prepare(context) != null) {
      return
    }

    val activeProfile = repository.getActiveProfile()
    DnsVpnService.hotSwapUpstream(activeProfile.toUpstream())

    val serviceIntent = Intent(context, DnsVpnService::class.java).apply {
      this.action = DnsVpnService.ACTION_START
    }

    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(serviceIntent)
      } else {
        context.startService(serviceIntent)
      }
    } catch (_: Exception) {
      // Guard against OS restrictions if background start is disallowed on custom OEM ROMs
    }
  }
}
