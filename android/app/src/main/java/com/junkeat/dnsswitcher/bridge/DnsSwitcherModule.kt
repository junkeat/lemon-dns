package com.junkeat.dnsswitcher.bridge

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import com.facebook.react.bridge.ActivityEventListener
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableMap
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.junkeat.dnsswitcher.MainActivity
import com.junkeat.dnsswitcher.storage.DnsProfile
import com.junkeat.dnsswitcher.storage.DnsProfileRepository
import com.junkeat.dnsswitcher.vpn.DnsTileService
import com.junkeat.dnsswitcher.vpn.DnsVpnService

/**
 * Thin React Native bridge exposing VPN controls, profile CRUD, active profile hot-swapping,
 * and state change events. All source-of-truth state lives in [DnsProfileRepository] and [DnsVpnService].
 */
class DnsSwitcherModule(
  private val reactContext: ReactApplicationContext,
) : ReactContextBaseJavaModule(reactContext), ActivityEventListener {

  private val repository: DnsProfileRepository
    get() = DnsProfileRepository.getInstance(reactContext)

  private var pendingStartPromise: Promise? = null

  private val vpnStateListener: () -> Unit = {
    emitStateChanged()
  }

  init {
    reactContext.addActivityEventListener(this)
    DnsVpnService.addStateListener(vpnStateListener)
    // Sync initial upstream in DnsVpnService with persisted active profile
    val active = repository.getActiveProfile()
    if (!DnsVpnService.isRunning) {
      DnsVpnService.hotSwapUpstream(active.toUpstream())
    }
  }

  override fun getName(): String = NAME

  override fun invalidate() {
    DnsVpnService.removeStateListener(vpnStateListener)
    reactContext.removeActivityEventListener(this)
    pendingStartPromise = null
    super.invalidate()
  }

  @ReactMethod
  fun addListener(eventName: String?) {
    // Required for RN NativeEventEmitter
  }

  @ReactMethod
  fun removeListeners(count: Double) {
    // Required for RN NativeEventEmitter
  }

  @ReactMethod
  fun getState(promise: Promise) {
    try {
      promise.resolve(buildStateMap())
    } catch (e: Exception) {
      promise.reject("ERR_GET_STATE", e.message, e)
    }
  }

  @ReactMethod
  fun startVpn(promise: Promise) {
    try {
      val activity = reactContext.currentActivity
      if (activity is MainActivity) {
        activity.ensureNotificationPermission {
          prepareAndLaunchVpn(activity, promise)
        }
      } else if (activity != null) {
        prepareAndLaunchVpn(activity, promise)
      } else {
        // No foreground activity; only works if VPN consent was already granted previously
        if (DnsVpnService.start(reactContext)) {
          promise.resolve(buildStateMap())
        } else {
          promise.reject("ERR_NO_ACTIVITY", "Cannot request VPN permission without an active screen.")
        }
      }
    } catch (e: Exception) {
      promise.reject("ERR_START_VPN", e.message, e)
    }
  }

  @ReactMethod
  fun stopVpn(promise: Promise) {
    try {
      DnsVpnService.stop(reactContext)
      promise.resolve(buildStateMap())
    } catch (e: Exception) {
      promise.reject("ERR_STOP_VPN", e.message, e)
    }
  }

  @ReactMethod
  fun setActiveProfile(profileId: String, promise: Promise) {
    try {
      val profile = repository.setActiveProfile(profileId)
      DnsVpnService.hotSwapUpstream(profile.toUpstream())
      DnsTileService.requestTileSync(reactContext)
      promise.resolve(buildStateMap())
    } catch (e: Exception) {
      promise.reject("ERR_SET_ACTIVE_PROFILE", e.message, e)
    }
  }

  @ReactMethod
  fun saveProfile(profileMap: ReadableMap, promise: Promise) {
    try {
      val id = if (profileMap.hasKey("id") && !profileMap.isNull("id")) {
        profileMap.getString("id")
      } else {
        null
      }
      val name = profileMap.getString("name") ?: ""
      val primaryDns = profileMap.getString("primaryDns") ?: ""
      val secondaryDns = if (profileMap.hasKey("secondaryDns") && !profileMap.isNull("secondaryDns")) {
        profileMap.getString("secondaryDns")
      } else {
        null
      }
      val port = if (profileMap.hasKey("port") && !profileMap.isNull("port")) {
        profileMap.getInt("port")
      } else {
        53
      }

      val saved = repository.saveProfile(
        id = id,
        name = name,
        primaryDns = primaryDns,
        secondaryDns = secondaryDns,
        port = port,
      )

      if (repository.getActiveProfileId() == saved.id) {
        DnsVpnService.hotSwapUpstream(saved.toUpstream())
      } else {
        DnsVpnService.notifyExternalStateChange()
      }
      DnsTileService.requestTileSync(reactContext)

      promise.resolve(buildStateMap())
    } catch (e: Exception) {
      promise.reject("ERR_SAVE_PROFILE", e.message, e)
    }
  }

  @ReactMethod
  fun deleteProfile(profileId: String, promise: Promise) {
    try {
      val activeAfterDelete = repository.deleteProfile(profileId)
      DnsVpnService.hotSwapUpstream(activeAfterDelete.toUpstream())
      DnsTileService.requestTileSync(reactContext)
      promise.resolve(buildStateMap())
    } catch (e: Exception) {
      promise.reject("ERR_DELETE_PROFILE", e.message, e)
    }
  }

  @ReactMethod
  fun updateSettings(settingsMap: ReadableMap, promise: Promise) {
    try {
      if (settingsMap.hasKey("startOnBoot") && !settingsMap.isNull("startOnBoot")) {
        repository.setStartOnBootEnabled(settingsMap.getBoolean("startOnBoot"))
      }
      DnsVpnService.notifyExternalStateChange()
      promise.resolve(buildStateMap())
    } catch (e: Exception) {
      promise.reject("ERR_UPDATE_SETTINGS", e.message, e)
    }
  }

  @ReactMethod
  fun restoreDefaultProfiles(promise: Promise) {
    try {
      val active = repository.restoreDefaultProfiles()
      DnsVpnService.hotSwapUpstream(active.toUpstream())
      DnsTileService.requestTileSync(reactContext)
      promise.resolve(buildStateMap())
    } catch (e: Exception) {
      promise.reject("ERR_RESTORE_DEFAULTS", e.message, e)
    }
  }

  private fun prepareAndLaunchVpn(activity: Activity, promise: Promise) {
    val prepareIntent = VpnService.prepare(activity)
    if (prepareIntent != null) {
      pendingStartPromise?.reject("ERR_SUPERSEDED", "New VPN start request initiated.")
      pendingStartPromise = promise
      activity.startActivityForResult(prepareIntent, REQUEST_CODE_VPN_PERMISSION)
    } else {
      DnsVpnService.start(reactContext)
      promise.resolve(buildStateMap())
    }
  }

  override fun onActivityResult(
    activity: Activity,
    requestCode: Int,
    resultCode: Int,
    data: Intent?,
  ) {
    if (requestCode != REQUEST_CODE_VPN_PERMISSION) {
      return
    }
    val promise = pendingStartPromise ?: return
    pendingStartPromise = null

    if (resultCode == Activity.RESULT_OK) {
      DnsVpnService.start(reactContext)
      promise.resolve(buildStateMap())
    } else {
      promise.reject("ERR_VPN_PERMISSION_DENIED", "VPN permission request was denied by user.")
    }
  }

  override fun onNewIntent(intent: Intent) = Unit

  private fun emitStateChanged() {
    if (!reactContext.hasActiveReactInstance()) {
      return
    }
    try {
      val stateMap = buildStateMap()
      reactContext
        .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
        .emit(EVENT_STATE_CHANGED, stateMap)
    } catch (_: Exception) {
      // Ignore emission before JS bundle is ready
    }
  }

  private fun buildStateMap(): WritableMap {
    val profiles = repository.getProfiles()
    val activeProfile = repository.getActiveProfile()

    val profilesArray = Arguments.createArray()
    for (profile in profiles) {
      profilesArray.pushMap(profile.toWritableMap())
    }

    val settingsMap = Arguments.createMap().apply {
      putBoolean("startOnBoot", repository.isStartOnBootEnabled())
    }

    return Arguments.createMap().apply {
      putBoolean("isRunning", DnsVpnService.isRunning)
      putString("activeProfileId", activeProfile.id)
      putMap("activeProfile", activeProfile.toWritableMap())
      putArray("profiles", profilesArray)
      putMap("settings", settingsMap)
    }
  }

  private fun DnsProfile.toWritableMap(): WritableMap = Arguments.createMap().apply {
    putString("id", id)
    putString("name", name)
    putString("primaryDns", primaryDns)
    putString("secondaryDns", secondaryDns ?: "")
    putInt("port", port)
    putBoolean("isBuiltIn", isBuiltIn)
  }

  companion object {
    const val NAME = "DnsSwitcher"
    const val EVENT_STATE_CHANGED = "DnsSwitcherStateChanged"
    private const val REQUEST_CODE_VPN_PERMISSION = 4010
  }
}
