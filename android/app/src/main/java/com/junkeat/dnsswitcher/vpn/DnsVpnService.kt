package com.junkeat.dnsswitcher.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import com.junkeat.dnsswitcher.MainActivity
import com.junkeat.dnsswitcher.R

/**
 * Split-tunnel Android VpnService that captures only traffic destined for the virtual DNS IP
 * (`10.111.222.2/32`) and forwards DNS queries to the active upstream server (`1.1.1.1`).
 */
class DnsVpnService : VpnService() {

  private var tunInterface: ParcelFileDescriptor? = null
  private var packetLoop: DnsPacketLoop? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_STOP) {
      stopVpnTunnel()
      stopSelf()
      return START_NOT_STICKY
    }

    startVpnTunnel()
    return START_STICKY
  }

  override fun onRevoke() {
    stopVpnTunnel()
    stopSelf()
    super.onRevoke()
  }

  override fun onDestroy() {
    stopVpnTunnel()
    super.onDestroy()
  }

  private fun startVpnTunnel() {
    if (packetLoop != null) {
      return
    }

    // Verify VPN permission is granted before establishing interface and promoting to systemExempted FGS
    if (prepare(this) != null) {
      stopSelf()
      return
    }

    val builder = Builder()
      .setSession(VPN_SESSION_NAME)
      .addAddress(VIRTUAL_CLIENT_IP, VIRTUAL_PREFIX_LENGTH)
      .addDnsServer(VIRTUAL_DNS_IP)
      .addRoute(VIRTUAL_DNS_IP, 32)
      .setBlocking(true)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      builder.setMetered(false)
    }

    val establishedTun = try {
      builder.establish()
    } catch (_: Exception) {
      null
    }

    if (establishedTun == null) {
      stopSelf()
      return
    }

    tunInterface = establishedTun
    promoteToForeground()

    val loop = DnsPacketLoop(
      vpnService = this,
      tunInterface = establishedTun,
      upstreamAddress = HARDCODED_UPSTREAM_DNS,
      upstreamPort = 53,
    )
    packetLoop = loop
    loop.start()
    isRunning = true
    notifyStateChanged()
  }

  private fun stopVpnTunnel() {
    packetLoop?.stop()
    packetLoop = null

    try {
      tunInterface?.close()
    } catch (_: Exception) {
      // Ignore close failure
    }
    tunInterface = null

    if (isRunning) {
      isRunning = false
      notifyStateChanged()
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      stopForeground(STOP_FOREGROUND_REMOVE)
    } else {
      @Suppress("DEPRECATION")
      stopForeground(true)
    }
  }

  private fun promoteToForeground() {
    val notificationManager = getSystemService(NotificationManager::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(
        NOTIFICATION_CHANNEL_ID,
        "DNS Switcher Status",
        NotificationManager.IMPORTANCE_LOW,
      ).apply {
        description = "Shows when DNS Switcher is active"
        setShowBadge(false)
      }
      notificationManager?.createNotificationChannel(channel)
    }

    val launchIntent = Intent(this, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val contentPendingIntent = PendingIntent.getActivity(
      this,
      0,
      launchIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    val stopIntent = Intent(this, DnsVpnService::class.java).apply {
      action = ACTION_STOP
    }
    val stopPendingIntent = PendingIntent.getService(
      this,
      1,
      stopIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    val notificationBuilder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
    } else {
      @Suppress("DEPRECATION")
      Notification.Builder(this)
    }

    val notification = notificationBuilder
      .setContentTitle("DNS Switcher Active")
      .setContentText("Resolver: Cloudflare ($HARDCODED_UPSTREAM_DNS)")
      .setSmallIcon(R.mipmap.ic_launcher)
      .setContentIntent(contentPendingIntent)
      .setOngoing(true)
      .addAction(
        Notification.Action.Builder(
          null,
          "Stop",
          stopPendingIntent,
        ).build(),
      )
      .build()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      startForeground(
        NOTIFICATION_ID,
        notification,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED,
      )
    } else {
      startForeground(NOTIFICATION_ID, notification)
    }
  }

  companion object {
    const val ACTION_START = "com.junkeat.dnsswitcher.action.START_VPN"
    const val ACTION_STOP = "com.junkeat.dnsswitcher.action.STOP_VPN"

    const val HARDCODED_UPSTREAM_DNS = "1.1.1.1"

    private const val VPN_SESSION_NAME = "DNS Switcher"
    private const val VIRTUAL_CLIENT_IP = "10.111.222.1"
    private const val VIRTUAL_DNS_IP = "10.111.222.2"
    private const val VIRTUAL_PREFIX_LENGTH = 30

    private const val NOTIFICATION_CHANNEL_ID = "dns_vpn_channel"
    private const val NOTIFICATION_ID = 1001

    @Volatile
    var isRunning: Boolean = false
      private set

    private val stateListeners = java.util.concurrent.CopyOnWriteArraySet<() -> Unit>()

    fun addStateListener(listener: () -> Unit) {
      stateListeners.add(listener)
    }

    fun removeStateListener(listener: () -> Unit) {
      stateListeners.remove(listener)
    }

    private fun notifyStateChanged() {
      for (listener in stateListeners) {
        listener.invoke()
      }
    }
  }
}
