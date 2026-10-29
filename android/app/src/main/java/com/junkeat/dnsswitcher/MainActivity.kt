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
import com.junkeat.dnsswitcher.vpn.DnsVpnService

class MainActivity : ReactActivity() {

  private var statusTextView: TextView? = null
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
    attachPrototypeDebugOverlay()
    DnsVpnService.addStateListener(vpnStateListener)
  }

  override fun onResume() {
    super.onResume()
    updateDebugOverlayState()
  }

  override fun onDestroy() {
    DnsVpnService.removeStateListener(vpnStateListener)
    super.onDestroy()
  }

  private fun attachPrototypeDebugOverlay() {
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
      elevation = 8 * density
    }

    val titleView = TextView(this).apply {
      text = "Phase 1 Prototype: UDP DNS Forwarder"
      setTextColor(Color.WHITE)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
    }

    val statusView = TextView(this).apply {
      setTextColor(Color.parseColor("#94A3B8"))
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
      setPadding(0, (6 * density).toInt(), 0, (12 * density).toInt())
    }
    statusTextView = statusView

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
    container.addView(actionButton)

    val layoutParams = FrameLayout.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.WRAP_CONTENT,
      Gravity.BOTTOM,
    ).apply {
      setMargins(marginPx, marginPx, marginPx, (32 * density).toInt())
    }

    addContentView(container, layoutParams)
    updateDebugOverlayState()
  }

  private fun updateDebugOverlayState() {
    val active = DnsVpnService.isRunning
    statusTextView?.text = if (active) {
      "Status: ACTIVE • Upstream: ${DnsVpnService.HARDCODED_UPSTREAM_DNS}:53\nVirtual DNS: 10.111.222.2/32 (Split-Tunnel)"
    } else {
      "Status: STOPPED • Hardcoded Upstream: ${DnsVpnService.HARDCODED_UPSTREAM_DNS}:53"
    }
    toggleButton?.text = if (active) {
      "Stop DNS VPN"
    } else {
      "Start DNS VPN (1.1.1.1)"
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
