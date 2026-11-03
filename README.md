<p align="center">
  <img src="android/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="120" height="120" alt="LemonDNS Logo" />
</p>

<h1 align="center">LemonDNS</h1>

<p align="center">
  <strong>Seamless, zero-drop DNS switcher for Android (No Root)</strong><br/>
  Built with Kotlin <code>VpnService</code> & React Native (TypeScript)
</p>

---

## Overview

**LemonDNS** (`com.junkeat.dnsswitcher`) is a lightweight Android DNS switcher that lets you maintain a list of DNS profiles and switch between them **instantly with zero dropped connections and no VPN restart**.

Unlike full-tunnel VPNs that route all device traffic through a remote server, LemonDNS creates a **local dual-stack split-tunnel interface** that captures *only* IPv4 (`10.111.222.2/32`) and IPv6 (`fd00:10:111:222::2/128`) DNS requests and forwards them directly from your device to your chosen DNS resolver. All other traffic (web browsing, video streams, downloads, gaming, WebSockets) bypasses the tunnel completely.

---

## Key Features

- **Zero-Drop Instant Hot-Swap**: Switch between DNS resolvers on the fly while the VPN is active. The TUN interface (`ParcelFileDescriptor`) never closes or restarts during a switch, so active downloads, calls, and streams never drop.
- **Dual-Stack IPv4 + IPv6 Split-Tunnel Capture**: Captures both IPv4 (`10.111.222.2/32`) and IPv6 (`fd00:10:111:222::2/128`) DNS queries so neither Wi-Fi nor IPv6-first mobile data (4G/5G) networks can bypass or leak DNS lookups. Zero bandwidth overhead on non-DNS traffic.
- **Built-in Fast & Ad-Blocking Presets (IPv4 + IPv6)**:
  - **Cloudflare**: IPv4 `1.1.1.1` • `1.0.0.1` | IPv6 `2606:4700:4700::1111` • `2606:4700:4700::1001`
  - **Google**: IPv4 `8.8.8.8` • `8.8.4.4` | IPv6 `2001:4860:4860::8888` • `2001:4860:4860::8844`
  - **Quad9**: IPv4 `9.9.9.9` • `149.112.112.112` | IPv6 `2620:fe::fe` • `2620:fe::9`
  - **AdGuard DNS (AdBlock)**: IPv4 `94.140.14.14` • `94.140.15.15` | IPv6 `2a10:50c0::ad1:ff` • `2a10:50c0::ad2:ff`
  - **Control D (AdBlock)**: IPv4 `76.76.2.2` • `76.76.10.2` | IPv6 `2606:1a40::2` • `2606:1a40:1::2`
- **Custom DNS Profiles (CRUD)**: Create, edit, and delete custom DNS profiles with primary/secondary IPv4, optional primary/secondary IPv6, and custom UDP/TCP port validation (built-in presets are protected from accidental edits).
- **Automatic Failover, Carrier Port-5353 Fallback & RFC 1035 TCP Fallback**:
  - Automatically falls back across primary/secondary IPv4 and IPv6 servers if an upstream server times out.
  - Automatically retries AdGuard DNS on port `5353` if a mobile carrier blocks or intercepts standard port `53`.
  - Automatically retries truncated UDP DNS responses (`TC=1`) over a protected TCP socket so large DNS records (CDNs, DNSSEC, TXT records) always resolve cleanly.
- **Quick Settings Tile & Boot Receiver**:
  - Toggle LemonDNS directly from your Android Quick Settings shade without opening the app.
  - Optional **Start on Device Boot** (`BOOT_COMPLETED`) support.
  - Native Kotlin `SharedPreferences` storage acts as the single source of truth so the VPN service, Quick Settings tile, and boot receiver work even when the React Native UI is closed.
- **Strict Privacy**: Zero DNS query logging, zero domain inspection, zero analytics, and zero unnecessary third-party dependencies.

---

## Architecture

```text
┌─────────────────────────────────────────────────────────────────┐
│                    React Native UI (App.tsx)                    │
│     • On/Off Control  • Profile List  • Add/Edit  • Settings    │
└───────────────────────────────┬─────────────────────────────────┘
                                │ NativeModules.DnsSwitcher
                                │ + RCTDeviceEventEmitter
┌───────────────────────────────▼─────────────────────────────────┐
│             Kotlin Native Layer (com.junkeat.dnsswitcher)       │
│                                                                 │
│  ┌──────────────────────┐        ┌───────────────────────────┐  │
│  │ DnsProfileRepository │◄───────┤ DnsTileService (QS Tile)  │  │
│  │ (Native Storage SoT) │◄───────┤ BootReceiver (Auto-Start) │  │
│  └──────────┬───────────┘        └───────────────────────────┘  │
│             │                                                   │
│  ┌──────────▼────────────────────────────────────────────────┐  │
│  │ DnsVpnService (Android 14/15 systemExempted FGS)          │  │
│  │  • IPv4 TUN: 10.111.222.1/30  -> DNS: 10.111.222.2/32     │  │
│  │  • IPv6 TUN: fd00:10:111:222::1/126 -> ::2/128            │  │
│  └──────────┬────────────────────────────────────────────────┘  │
│             │                                                   │
│  ┌──────────▼────────────────────────────────────────────────┐  │
│  │ DnsPacketLoop & UdpPacketCodec                            │  │
│  │  • AtomicReference<DnsUpstream> (Hot-Swap in-place)       │  │
│  │  • Raw IPv4 & IPv6 + UDP parser & checksum builder        │  │
│  │  • Protected UDP DatagramSocket + TCP (TC=1) fallback     │  │
│  └───────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

### Key Source Files

| File | Responsibility |
| :--- | :--- |
| [`DnsVpnService.kt`](android/app/src/main/java/com/junkeat/dnsswitcher/vpn/DnsVpnService.kt) | Manages the dual-stack split-tunnel `VpnService`, Android 14/15 `systemExempted` foreground promotion, and live upstream hot-swapping. |
| [`DnsPacketLoop.kt`](android/app/src/main/java/com/junkeat/dnsswitcher/vpn/DnsPacketLoop.kt) | Reads raw IPv4/IPv6 packets from the TUN descriptor, forwards DNS payloads over bounded protected UDP/TCP sockets, and writes responses back. |
| [`UdpPacketCodec.kt`](android/app/src/main/java/com/junkeat/dnsswitcher/vpn/UdpPacketCodec.kt) | Parses IPv4 & IPv6 + UDP port 53 packets and constructs valid response packets with IPv4 header and IPv4/IPv6 UDP pseudo-header checksums. |
| [`DnsProfileRepository.kt`](android/app/src/main/java/com/junkeat/dnsswitcher/storage/DnsProfileRepository.kt) | Thread-safe cached native persistence for profiles, active profile selection, and boot settings. |
| [`DnsTileService.kt`](android/app/src/main/java/com/junkeat/dnsswitcher/vpn/DnsTileService.kt) | Android Quick Settings tile integration. |
| [`BootReceiver.kt`](android/app/src/main/java/com/junkeat/dnsswitcher/vpn/BootReceiver.kt) | Starts the VPN service automatically on device boot when enabled. |
| [`DnsSwitcherModule.kt`](android/app/src/main/java/com/junkeat/dnsswitcher/bridge/DnsSwitcherModule.kt) | React Native bridge exposing VPN controls, profile CRUD, and `DnsSwitcherStateChanged` events. |
| [`App.tsx`](App.tsx) | React Native UI (LemonDNS navy & gold theme) for status control, profile switching, custom profile editor, and settings. |

---

## Prerequisites & Setup Guide

### 1. Requirements
- **Node.js**: `>= 22.11.0`
- **JDK**: Java 17 or Java 21
- **Android SDK**:
  - `minSdkVersion`: `26` (Android 8.0+)
  - `targetSdkVersion`: `36` / `compileSdkVersion`: `37`
  - Android SDK `platform-tools` (`adb`) installed
- **Physical Android Device**: Recommended for testing (`VpnService` is unreliable on emulators).

### 2. Clone & Install Dependencies

```bash
git clone https://github.com/junkeat/lemon-dns.git
cd lemon-dns
npm install
```

### 3. Configure Android SDK Environment

Ensure `ANDROID_HOME` is set and `platform-tools` is in your `PATH`:

**Windows (PowerShell):**
```powershell
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:Path += ";$env:ANDROID_HOME\platform-tools;$env:ANDROID_HOME\emulator"
```

**macOS / Linux (Bash/Zsh):**
```bash
export ANDROID_HOME="$HOME/Library/Android/sdk" # or $HOME/Android/Sdk on Linux
export PATH="$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator"
```

*(Alternatively, create `android/local.properties` with `sdk.dir=/path/to/your/Android/Sdk`.)*

### 4. Connect a Physical Android Device

1. Enable **Developer options** on your Android phone (*Settings > About phone > Software information > tap **Build number** 7 times*).
2. *(Samsung One UI devices)* Turn off **Auto Blocker** under *Settings > Security and privacy > Auto Blocker*.
3. Enable **USB debugging** (or **Wireless debugging**) under *Settings > Developer options*.
4. Verify your device is connected:
   ```bash
   adb devices
   ```

> **Tip**: On your Android device, ensure **Private DNS** (*Settings > Network & Internet / Connections > Private DNS*) is set to **Off** so Android's stub resolver uses the VPN tunnel's DNS server instead of overriding it with system DoT.

### 5. Run in Development Mode

Start the Metro bundler and install the debug build on your connected device:

```bash
# Terminal 1: Start Metro
npm start

# Terminal 2: Build and install on Android device
npm run android
```

### 6. Build a Standalone APK (No Metro Required)

To build a standalone release APK that runs independently without the Metro server:

```bash
cd android
./gradlew assembleRelease
```

The generated APK will be located at:
```text
android/app/build/outputs/apk/release/app-release.apk
```

Install it onto your connected device via ADB:
```bash
adb install -r android/app/build/outputs/apk/release/app-release.apk
```

---

## Verifying DNS Switching & Zero Leaks

1. Open **LemonDNS** and tap **Turn On DNS VPN** (approve the one-time Android VPN connection prompt).
2. Open your browser and visit [https://one.one.one.one/help](https://one.one.one.one/help) or [https://www.dnsleaktest.com](https://www.dnsleaktest.com).
3. Tap any other profile in **LemonDNS** (e.g., **Google**, **Quad9**, **AdGuard DNS**, or **Control D**) while keeping a video stream or download active.
4. Refresh the DNS leak test to verify both IPv4 and IPv6 resolvers switched immediately with zero connection drops.

---

## License

MIT
