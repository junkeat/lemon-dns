package com.junkeat.dnsswitcher.vpn

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.junkeat.dnsswitcher.MainActivity
import com.junkeat.dnsswitcher.storage.DnsProfileRepository

/**
 * Android Quick Settings Tile that toggles the DNS VPN on/off and displays the active DNS profile.
 * Works completely independently of the React Native process using [DnsProfileRepository].
 */
class DnsTileService : TileService() {

  private val stateListener: () -> Unit = {
    updateTileState()
  }

  override fun onStartListening() {
    super.onStartListening()
    DnsVpnService.addStateListener(stateListener)
    updateTileState()
  }

  override fun onStopListening() {
    DnsVpnService.removeStateListener(stateListener)
    super.onStopListening()
  }

  override fun onClick() {
    super.onClick()
    if (DnsVpnService.isRunning) {
      val stopIntent = Intent(this, DnsVpnService::class.java).apply {
        action = DnsVpnService.ACTION_STOP
      }
      startService(stopIntent)
      updateTileState(forceRunning = false)
      return
    }

    // Check if VPN permission is already granted
    val prepareIntent = VpnService.prepare(this)
    if (prepareIntent == null) {
      val repository = DnsProfileRepository.getInstance(applicationContext)
      val activeProfile = repository.getActiveProfile()
      DnsVpnService.hotSwapUpstream(activeProfile.toUpstream())

      val startIntent = Intent(this, DnsVpnService::class.java).apply {
        action = DnsVpnService.ACTION_START
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        startForegroundService(startIntent)
      } else {
        startService(startIntent)
      }
      updateTileState(forceRunning = true)
    } else {
      // VPN consent not yet granted: open MainActivity so the user can approve the VPN dialog
      launchMainActivityAndCollapse()
    }
  }

  private fun launchMainActivityAndCollapse() {
    val activityIntent = Intent(this, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      val pendingIntent = PendingIntent.getActivity(
        this,
        0,
        activityIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
      startActivityAndCollapse(pendingIntent)
    } else {
      @Suppress("DEPRECATION")
      startActivityAndCollapse(activityIntent)
    }
  }

  private fun updateTileState(forceRunning: Boolean? = null) {
    val tile = qsTile ?: return
    val repository = DnsProfileRepository.getInstance(applicationContext)
    val activeProfile = repository.getActiveProfile()
    val running = forceRunning ?: DnsVpnService.isRunning

    if (running) {
      tile.state = Tile.STATE_ACTIVE
      tile.label = "LemonDNS: ${activeProfile.name}"
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        tile.subtitle = activeProfile.primaryDns
      }
    } else {
      tile.state = Tile.STATE_INACTIVE
      tile.label = "LemonDNS"
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        tile.subtitle = "Off (${activeProfile.name})"
      }
    }
    tile.updateTile()
  }

  companion object {
    fun requestTileSync(context: Context) {
      try {
        requestListeningState(
          context.applicationContext,
          ComponentName(context.applicationContext, DnsTileService::class.java),
        )
      } catch (_: Exception) {
        // Ignore if tile is not added to Quick Settings
      }
    }
  }
}
