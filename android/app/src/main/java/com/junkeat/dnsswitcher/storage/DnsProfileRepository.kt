package com.junkeat.dnsswitcher.storage

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import java.util.UUID

/**
 * Native Kotlin source-of-truth storage for DNS profiles, the active profile ID, and app settings.
 * Safe to access from VpnService, Quick Settings Tile, BootReceiver, and the React Native bridge
 * even when the React Native JS runtime is not initialized.
 */
class DnsProfileRepository private constructor(context: Context) {

  private val prefs: SharedPreferences =
    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  private var cachedProfiles: List<DnsProfile>? = null

  init {
    ensureSeeded()
  }

  @Synchronized
  private fun ensureSeeded() {
    val raw = prefs.getString(KEY_PROFILES_JSON, null)
    if (raw.isNullOrBlank()) {
      val defaults = DnsProfile.defaultPresets()
      saveProfilesInternal(defaults)
      prefs.edit()
        .putString(KEY_ACTIVE_PROFILE_ID, defaults.first().id)
        .putInt(KEY_SEED_VERSION, CURRENT_SEED_VERSION)
        .apply()
    } else {
      val profiles = getProfiles().toMutableList()
      if (profiles.isEmpty()) {
        val defaults = DnsProfile.defaultPresets()
        saveProfilesInternal(defaults)
        prefs.edit()
          .putString(KEY_ACTIVE_PROFILE_ID, defaults.first().id)
          .putInt(KEY_SEED_VERSION, CURRENT_SEED_VERSION)
          .apply()
      } else {
        val seedVersion = prefs.getInt(KEY_SEED_VERSION, 1)
        if (seedVersion < CURRENT_SEED_VERSION) {
          for (preset in DnsProfile.defaultPresets()) {
            val idx = profiles.indexOfFirst { it.id == preset.id }
            if (idx < 0) {
              profiles.add(preset)
            } else if (profiles[idx].primaryIpv6Dns.isNullOrBlank()) {
              profiles[idx] = profiles[idx].copy(
                primaryIpv6Dns = preset.primaryIpv6Dns,
                secondaryIpv6Dns = preset.secondaryIpv6Dns,
              )
            }
          }
          saveProfilesInternal(profiles)
          prefs.edit().putInt(KEY_SEED_VERSION, CURRENT_SEED_VERSION).apply()
        }
        val activeId = prefs.getString(KEY_ACTIVE_PROFILE_ID, null)
        if (activeId == null || profiles.none { it.id == activeId }) {
          prefs.edit()
            .putString(KEY_ACTIVE_PROFILE_ID, profiles.first().id)
            .apply()
        }
      }
    }
  }

  @Synchronized
  fun getProfiles(): List<DnsProfile> {
    cachedProfiles?.let { return it }
    val raw = prefs.getString(KEY_PROFILES_JSON, null)
      ?: return DnsProfile.defaultPresets().also { cachedProfiles = it }
    val loaded = try {
      val array = JSONArray(raw)
      val list = ArrayList<DnsProfile>(array.length())
      for (i in 0 until array.length()) {
        val obj = array.optJSONObject(i) ?: continue
        list.add(DnsProfile.fromJson(obj))
      }
      if (list.isEmpty()) DnsProfile.defaultPresets() else list
    } catch (_: Exception) {
      DnsProfile.defaultPresets()
    }
    cachedProfiles = loaded
    return loaded
  }

  @Synchronized
  fun getActiveProfileId(): String {
    val profiles = getProfiles()
    val storedId = prefs.getString(KEY_ACTIVE_PROFILE_ID, null)
    if (storedId != null && profiles.any { it.id == storedId }) {
      return storedId
    }
    val fallbackId = profiles.first().id
    prefs.edit().putString(KEY_ACTIVE_PROFILE_ID, fallbackId).apply()
    return fallbackId
  }

  @Synchronized
  fun getActiveProfile(): DnsProfile {
    val profiles = getProfiles()
    val activeId = getActiveProfileId()
    return profiles.find { it.id == activeId } ?: profiles.first()
  }

  @Synchronized
  fun setActiveProfile(profileId: String): DnsProfile {
    val profiles = getProfiles()
    val target = profiles.find { it.id == profileId }
      ?: throw IllegalArgumentException("DNS profile not found: $profileId")
    prefs.edit().putString(KEY_ACTIVE_PROFILE_ID, target.id).apply()
    return target
  }

  @Synchronized
  fun saveProfile(
    id: String?,
    name: String,
    primaryDns: String,
    secondaryDns: String?,
    primaryIpv6Dns: String? = null,
    secondaryIpv6Dns: String? = null,
    port: Int,
  ): DnsProfile {
    val cleanName = name.trim()
    val cleanPrimary = primaryDns.trim()
    val cleanSecondary = secondaryDns?.trim()?.ifEmpty { null }
    val cleanPrimaryV6 = primaryIpv6Dns?.trim()?.ifEmpty { null }
    val cleanSecondaryV6 = secondaryIpv6Dns?.trim()?.ifEmpty { null }

    require(cleanName.isNotEmpty()) { "Profile name cannot be empty." }
    require(IpAddressValidator.isValidIpv4(cleanPrimary)) {
      "Primary DNS must be a valid public IPv4 address (e.g. 1.1.1.1)."
    }
    if (cleanSecondary != null) {
      require(IpAddressValidator.isValidIpv4(cleanSecondary)) {
        "Secondary DNS must be a valid public IPv4 address or left blank."
      }
    }
    if (cleanPrimaryV6 != null) {
      require(IpAddressValidator.isValidIpv6(cleanPrimaryV6)) {
        "Primary IPv6 DNS must be a valid IPv6 address (e.g. 2606:4700:4700::1111) or left blank."
      }
    }
    if (cleanSecondaryV6 != null) {
      require(IpAddressValidator.isValidIpv6(cleanSecondaryV6)) {
        "Secondary IPv6 DNS must be a valid IPv6 address or left blank."
      }
    }
    require(IpAddressValidator.isValidPort(port)) {
      "Port must be between 1 and 65535."
    }

    val currentList = getProfiles().toMutableList()
    val existingIndex = if (!id.isNullOrBlank()) {
      currentList.indexOfFirst { it.id == id }
    } else {
      -1
    }

    val savedProfile = if (existingIndex >= 0) {
      val existing = currentList[existingIndex]
      existing.copy(
        name = cleanName,
        primaryDns = cleanPrimary,
        secondaryDns = cleanSecondary,
        primaryIpv6Dns = cleanPrimaryV6,
        secondaryIpv6Dns = cleanSecondaryV6,
        port = port,
      ).also { currentList[existingIndex] = it }
    } else {
      DnsProfile(
        id = UUID.randomUUID().toString(),
        name = cleanName,
        primaryDns = cleanPrimary,
        secondaryDns = cleanSecondary,
        primaryIpv6Dns = cleanPrimaryV6,
        secondaryIpv6Dns = cleanSecondaryV6,
        port = port,
        isBuiltIn = false,
      ).also { currentList.add(it) }
    }

    saveProfilesInternal(currentList)
    return savedProfile
  }

  @Synchronized
  fun deleteProfile(profileId: String): DnsProfile {
    val currentList = getProfiles().toMutableList()
    require(currentList.size > 1) {
      "Cannot delete the last remaining DNS profile."
    }
    val removed = currentList.removeAll { it.id == profileId }
    require(removed) {
      "Profile not found."
    }
    saveProfilesInternal(currentList)

    val currentActiveId = prefs.getString(KEY_ACTIVE_PROFILE_ID, null)
    if (currentActiveId == profileId || currentList.none { it.id == currentActiveId }) {
      val nextActive = currentList.first()
      prefs.edit().putString(KEY_ACTIVE_PROFILE_ID, nextActive.id).apply()
      return nextActive
    }
    return getActiveProfile()
  }

  @Synchronized
  fun restoreDefaultProfiles(): DnsProfile {
    val currentList = getProfiles().toMutableList()
    val defaults = DnsProfile.defaultPresets()
    for (preset in defaults) {
      val index = currentList.indexOfFirst { it.id == preset.id }
      if (index >= 0) {
        currentList[index] = preset
      } else {
        currentList.add(preset)
      }
    }
    saveProfilesInternal(currentList)
    return getActiveProfile()
  }

  @Synchronized
  fun isStartOnBootEnabled(): Boolean = prefs.getBoolean(KEY_START_ON_BOOT, false)

  @Synchronized
  fun setStartOnBootEnabled(enabled: Boolean) {
    prefs.edit().putBoolean(KEY_START_ON_BOOT, enabled).apply()
  }

  private fun saveProfilesInternal(profiles: List<DnsProfile>) {
    cachedProfiles = profiles.toList()
    val array = JSONArray()
    for (profile in profiles) {
      array.put(profile.toJson())
    }
    prefs.edit().putString(KEY_PROFILES_JSON, array.toString()).apply()
  }

  companion object {
    private const val PREFS_NAME = "dns_switcher_store"
    private const val KEY_PROFILES_JSON = "profiles_json"
    private const val KEY_ACTIVE_PROFILE_ID = "active_profile_id"
    private const val KEY_START_ON_BOOT = "start_on_boot"
    private const val KEY_SEED_VERSION = "seed_version"
    private const val CURRENT_SEED_VERSION = 3

    @Volatile
    private var instance: DnsProfileRepository? = null

    fun getInstance(context: Context): DnsProfileRepository {
      return instance ?: synchronized(this) {
        instance ?: DnsProfileRepository(context).also { instance = it }
      }
    }
  }
}
