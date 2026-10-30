package com.junkeat.dnsswitcher

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.fabricEnabled
import com.facebook.react.defaults.DefaultReactActivityDelegate
import com.junkeat.dnsswitcher.vpn.DnsUpstream
import com.junkeat.dnsswitcher.vpn.DnsVpnService

class MainActivity : ReactActivity() {

  private var debugOverlayContainer: LinearLayout? = null
  private var statusTextView: TextView? = null
  private var swapButton: Button? = null
  private var toggleButton: Button? = null

  private val vpnStateListener: () -> Unit = {
    runOnUiThread {
      updateDebugOverlayState()
    }
  }

  private val vpnPermissionLauncher = registerForActivityResult(
    ActivityResultContracts.StartActivityForResult(),
  ) { result ->
    if (result.resultCode == RESULT_OK) {
      startDnsVpnService()
    }
  }

  private val notificationPermissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestPermission(),
  ) { _ ->
    prepareAndStartVpn()
  }

  override fun getMainComponentName(): String = "DnsSwitcher"

  override fun createReactActivityDelegate(): ReactActivityDelegate =
    DefaultReactActivityDelegate(this, mainComponentName, fabricEnabled)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    DnsVpnService.addStateListener(vpnStateListener)
  }

  override fun onResume() {
    super.onResume()
    ensurePrototypeDebugOverlayAttached()
    updateDebugOverlayState()
  }

  override fun onDestroy() {
    DnsVpnService.removeStateListener(vpnStateListener)
    super.onDestroy()
  }

  private fun ensurePrototypeDebugOverlayAttached() {
    val rootContent = findViewById<FrameLayout>(android.R.id.content) ?: return
    val existing = debugOverlayContainer
    if (existing != null && existing.parent === rootContent) {
      existing.bringToFront()
      return
    }
    existing?.let { (it.parent as? ViewGroup)?.removeView(it) }

    val density = resources.displayMetrics.density
    val paddingPx = (16 * density).toInt()
    val marginPx = (16 * density).toInt()

    val cardBackground = GradientDrawable().apply {
      setColor(Color.parseColor("#1E293B"))
      cornerRadius = 12 * density
    }

    val container = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      background = cardBackground
      setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
      elevation = 16 * density
    }

    val titleView = TextView(this).apply {
      text = "Phase 2 Prototype: Zero-Drop Hot-Swap"
      setTextColor(Color.WHITE)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
    }

    val statusView = TextView(this).apply {
      setTextColor(Color.parseColor("#94A3B8"))
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
      setPadding(0, (6 * density).toInt(), 0, (12 * density).toInt())
    }
    statusTextView = statusView

    val hotSwapBtn = Button(this).apply {
      isAllCaps = false
      setOnClickListener {
        DnsVpnService.toggleHardcodedUpstream()
      }
    }
    swapButton = hotSwapBtn

    val actionButton = Button(this).apply {
      isAllCaps = false
      setOnClickListener {
        if (DnsVpnService.isRunning) {
          stopDnsVpnService()
        } else {
          requestNotificationAndStartVpn()
        }
      }
    }
    toggleButton = actionButton

    container.addView(titleView)
    container.addView(statusView)
    container.addView(hotSwapBtn)
    container.addView(actionButton)

    val layoutParams = FrameLayout.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.WRAP_CONTENT,
      Gravity.BOTTOM,
    ).apply {
      setMargins(marginPx, marginPx, marginPx, (48 * density).toInt())
    }

    rootContent.addView(container, layoutParams)
    container.bringToFront()
    debugOverlayContainer = container
  }

  private fun updateDebugOverlayState() {
    val active = DnsVpnService.isRunning
    val current = DnsVpnService.currentUpstream
    val next = if (current.id == DnsUpstream.CLOUDFLARE.id) {
      DnsUpstream.GOOGLE
    } else {
      DnsUpstream.CLOUDFLARE
    }

    statusTextView?.text = if (active) {
      "Status: ACTIVE (Tunnel Open)\nActive Upstream: ${current.name} (${current.address}:${current.port})"
    } else {
      "Status: STOPPED\nSelected Upstream: ${current.name} (${current.address}:${current.port})"
    }

    swapButton?.text = "Hot-Swap to ${next.name} (${next.address})"

    toggleButton?.text = if (active) {
      "Stop DNS VPN"
    } else {
      "Start DNS VPN (${current.name} ${current.address})"
    }
  }

  private fun requestNotificationAndStartVpn() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      val granted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
      if (!granted) {
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        return
      }
    }
    prepareAndStartVpn()
  }

  private fun prepareAndStartVpn() {
    val prepareIntent = VpnService.prepare(this)
    if (prepareIntent != null) {
      vpnPermissionLauncher.launch(prepareIntent)
    } else {
      startDnsVpnService()
    }
  }

  private fun startDnsVpnService() {
    val intent = Intent(this, DnsVpnService::class.java).apply {
      action = DnsVpnService.ACTION_START
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      startForegroundService(intent)
    } else {
      startService(intent)
    }
  }

  private fun stopDnsVpnService() {
    val intent = Intent(this, DnsVpnService::class.java).apply {
      action = DnsVpnService.ACTION_STOP
    }
    startService(intent)
  }
}
