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
import com.junkeat.dnsswitcher.storage.DnsProfileRepository

/**
 * Split-tunnel Android VpnService that captures only traffic destined for the virtual DNS IP
 * (`10.111.222.2/32`) and forwards DNS queries to the active upstream server from [DnsProfileRepository].
 *
 * Supports hot-swapping the upstream server while the tunnel remains open.
 */
class DnsVpnService : VpnService() {

  private var tunInterface: ParcelFileDescriptor? = null
  private var packetLoop: DnsPacketLoop? = null

  override fun onCreate() {
    super.onCreate()
    activeInstance = this
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val repository = DnsProfileRepository.getInstance(applicationContext)
    when (intent?.action) {
      ACTION_STOP -> {
        stopVpnTunnel()
        stopSelf()
        DnsTileService.requestTileSync(applicationContext)
        return START_NOT_STICKY
      }
      ACTION_SWITCH_UPSTREAM -> {
        val targetId = intent.getStringExtra(EXTRA_UPSTREAM_ID)
        val profile = if (targetId != null) {
          runCatching { repository.setActiveProfile(targetId) }.getOrElse { repository.getActiveProfile() }
        } else {
          repository.getActiveProfile()
        }
        applyUpstreamHotSwap(profile.toUpstream())
        DnsTileService.requestTileSync(applicationContext)
        return START_STICKY
      }
      else -> {
        val activeProfile = repository.getActiveProfile()
        currentUpstream = activeProfile.toUpstream()
        startVpnTunnel()
        DnsTileService.requestTileSync(applicationContext)
        return START_STICKY
      }
    }
  }

  override fun onRevoke() {
    stopVpnTunnel()
    DnsTileService.requestTileSync(applicationContext)
    stopSelf()
    super.onRevoke()
  }

  override fun onDestroy() {
    if (activeInstance === this) {
      activeInstance = null
    }
    stopVpnTunnel()
    DnsTileService.requestTileSync(applicationContext)
    super.onDestroy()
  }

  private fun applyUpstreamHotSwap(upstream: DnsUpstream) {
    currentUpstream = upstream
    val loop = packetLoop
    if (loop != null && isRunning) {
      loop.setUpstream(upstream)
      updateForegroundNotification()
    }
    DnsTileService.requestTileSync(applicationContext)
    notifyStateChanged()
  }

  private fun startVpnTunnel() {
    val existingLoop = packetLoop
    if (existingLoop != null) {
      existingLoop.setUpstream(currentUpstream)
      updateForegroundNotification()
      notifyStateChanged()
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
      initialUpstream = currentUpstream,
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
    ensureNotificationChannel()
    val notification = buildForegroundNotification(currentUpstream)

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

  private fun updateForegroundNotification() {
    val notificationManager = getSystemService(NotificationManager::class.java) ?: return
    val notification = buildForegroundNotification(currentUpstream)
    notificationManager.notify(NOTIFICATION_ID, notification)
  }

  private fun ensureNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val notificationManager = getSystemService(NotificationManager::class.java)
      val channel = NotificationChannel(
        NOTIFICATION_CHANNEL_ID,
        "LemonDNS Status",
        NotificationManager.IMPORTANCE_LOW,
      ).apply {
        description = "Shows when LemonDNS is active"
        setShowBadge(false)
      }
      notificationManager?.createNotificationChannel(channel)
    }
  }

  private fun buildForegroundNotification(upstream: DnsUpstream): Notification {
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

    val secondarySuffix = upstream.secondaryAddress?.let { " • $it" } ?: ""

    return notificationBuilder
      .setContentTitle("LemonDNS • ${upstream.name}")
      .setContentText("Resolver: ${upstream.address}$secondarySuffix")
      .setSmallIcon(R.drawable.ic_qs_dns)
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
  }

  companion object {
    const val ACTION_START = "com.junkeat.dnsswitcher.action.START_VPN"
    const val ACTION_STOP = "com.junkeat.dnsswitcher.action.STOP_VPN"
    const val ACTION_SWITCH_UPSTREAM = "com.junkeat.dnsswitcher.action.SWITCH_UPSTREAM"
    const val EXTRA_UPSTREAM_ID = "extra_upstream_id"

    private const val VPN_SESSION_NAME = "LemonDNS"
    private const val VIRTUAL_CLIENT_IP = "10.111.222.1"
    private const val VIRTUAL_DNS_IP = "10.111.222.2"
    private const val VIRTUAL_PREFIX_LENGTH = 30

    private const val NOTIFICATION_CHANNEL_ID = "dns_vpn_channel"
    private const val NOTIFICATION_ID = 1001

    @Volatile
    private var activeInstance: DnsVpnService? = null

    @Volatile
    var isRunning: Boolean = false
      private set

    @Volatile
    var currentUpstream: DnsUpstream = DnsUpstream.CLOUDFLARE
      private set

    private val stateListeners = java.util.concurrent.CopyOnWriteArraySet<() -> Unit>()

    /**
     * Atomically hot-swaps the active upstream DNS server.
     * If the VPN service is running, the running packet loop and notification update immediately
     * without closing or recreating the TUN interface.
     */
    fun hotSwapUpstream(upstream: DnsUpstream) {
      val service = activeInstance
      if (service != null) {
        service.applyUpstreamHotSwap(upstream)
      } else {
        currentUpstream = upstream
        notifyStateChanged()
      }
    }

    fun addStateListener(listener: () -> Unit) {
      stateListeners.add(listener)
    }

    fun removeStateListener(listener: () -> Unit) {
      stateListeners.remove(listener)
    }

    fun notifyExternalStateChange() {
      notifyStateChanged()
    }

    private fun notifyStateChanged() {
      for (listener in stateListeners) {
        listener.invoke()
      }
    }
  }
}
