# Netrik

**Netrik** is an open source network toolkit for Android, made for network analysts, pentesters and anyone curious about the network they're on. It runs entirely on the phone, without root, and doesn't track you.

## Screenshots

<img width="30%" vspace="20" src="https://github.com/user-attachments/assets/5da888d2-4a16-45a3-a0bb-e7e6ac61cbb0" />
<img width="30%" vspace="20" src="https://github.com/user-attachments/assets/7fe13df8-8428-44ee-b08f-f1a790276992" />
<img width="30%" vspace="20" src="https://github.com/user-attachments/assets/579b981e-399a-4050-9987-ad121001218b" />
<img width="30%" vspace="20" src="https://github.com/user-attachments/assets/ff9ff985-8d40-46ec-98c2-ce57098bfd39" />
<img width="30%" vspace="20" src="https://github.com/user-attachments/assets/db745634-b6fd-4518-a056-d5eeca9131f6" />
<img width="30%" vspace="20" src="https://github.com/user-attachments/assets/dc4ef25d-448d-4f35-b788-23db615784ba" />

# Download

Netrik is available on GitHub Releases page.  

[<img height="75" src="https://github.com/user-attachments/assets/ea9ecc83-372a-4ef5-8595-1423873f2faf" />](https://github.com/henriquesebastiao/netrik/releases)
[<img height="75" src="https://github.com/user-attachments/assets/33762cb0-6efe-4082-bd45-56c49fb0cc27" />](https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22com.henriquesebastiao.netrik%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fhenriquesebastiao%2Fnetrik%22%2C%22author%22%3A%22henriquesebastiao%22%2C%22name%22%3A%22Netrik%22%2C%22preferredApkIndex%22%3A0%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Afalse%2C%5C%22fallbackToOlderReleases%5C%22%3Atrue%2C%5C%22filterReleaseTitlesByRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22filterReleaseNotesByRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22verifyLatestTag%5C%22%3Atrue%2C%5C%22sortMethodChoice%5C%22%3A%5C%22date%5C%22%2C%5C%22useLatestAssetDateAsReleaseDate%5C%22%3Afalse%2C%5C%22releaseTitleAsVersion%5C%22%3Afalse%2C%5C%22trackOnly%5C%22%3Afalse%2C%5C%22versionExtractionRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22matchGroupToUse%5C%22%3A%5C%22%5C%22%2C%5C%22versionDetection%5C%22%3Atrue%2C%5C%22releaseDateAsVersion%5C%22%3Afalse%2C%5C%22useVersionCodeAsOSVersion%5C%22%3Afalse%2C%5C%22apkFilterRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22invertAPKFilter%5C%22%3Afalse%2C%5C%22autoApkFilterByArch%5C%22%3Atrue%2C%5C%22minimumUpdateAgeDays%5C%22%3A%5C%22%5C%22%2C%5C%22appName%5C%22%3A%5C%22%5C%22%2C%5C%22appAuthor%5C%22%3A%5C%22%5C%22%2C%5C%22shizukuPretendToBeGooglePlay%5C%22%3Afalse%2C%5C%22allowInsecure%5C%22%3Afalse%2C%5C%22allowedSigningCertHashes%5C%22%3A%5C%22%5C%22%2C%5C%22exemptFromBackgroundUpdates%5C%22%3Afalse%2C%5C%22skipUpdateNotifications%5C%22%3Afalse%2C%5C%22about%5C%22%3A%5C%22%5C%22%2C%5C%22refreshBeforeDownload%5C%22%3Afalse%2C%5C%22includeZips%5C%22%3Afalse%2C%5C%22zippedApkFilterRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22includeTarballs%5C%22%3Afalse%2C%5C%22tarballedApkFilterRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22github-creds%5C%22%3A%5C%22%5C%22%2C%5C%22GHReqPrefix%5C%22%3A%5C%22%5C%22%2C%5C%22checkRepoRename%5C%22%3Afalse%7D%22%2C%22overrideSource%22%3A%22GitHub%22%7D)


## Features

| Tool | What it does |
| --- | --- |
| **Current network** | Local IP, gateway and public IP at a glance; a details sheet with network name, signal, band, channel, BSSID, mask/CIDR, DNS and IPv6, with one-tap copy. The public IP is only looked up when you ask for it (or always, if you turn that on in Settings). |
| **Ping** | Real-time replies with seq, TTL and time, count or continuous mode, latency chart and statistics (loss, min/avg/max, jitter). |
| **Traceroute** | Hop-by-hop route with reverse DNS and the latency of each router. |
| **Devices** | Finds the devices on your local network (ping + TCP probing, mDNS, NetBIOS, UPnP, MNDP and Ubiquiti discovery), with vendor, hostname and quick actions. Checks the Top 100 ports of each device to tell cameras, printers, TVs, NAS and other devices apart (can be turned off in Settings). |
| **Neighbor Discovery** | Finds MikroTik routers via MNDP (identity, board, RouterOS version, MAC, interface, uptime) and Ubiquiti devices via their discovery protocol, with quick actions. The Devices scan uses the same announcements to fill in names, MACs and models. |
| **Wi-Fi** | Nearby networks with security (flagging WPS), channel, width and signal quality, plus a 2.4/5/6 GHz spectrum chart. A live signal meter to walk around with (optional beeps), the least congested channel per band, and the distance to access points that support Wi-Fi RTT (802.11mc). |
| **Port Scanner** | TCP and UDP scans of a single host or a whole network (up to /22), with Top 100/Top 1000/custom port lists and service names. Reads what each open service says (SSH/FTP/SMTP greetings, HTTP Server header, TLS certificate), flags services that are risky when exposed (Telnet, SMB, RDP, VNC, UPnP, unauthenticated databases...) and opens web services in the browser. |
| **MAC/OUI Lookup** | Vendor of a MAC address from the offline IEEE database (MA-L, MA-M and MA-S), updatable from the official files. |
| **Port Knocking** | Saved knock sequences (TCP, UDP and ICMP steps) in groups, with a delay between knocks, an optional check that the port opened, and JSON export/import. |
| **Subnet Calculator** | IPv4 and IPv6: network, mask, wildcard, broadcast, host range and count, address type and reverse DNS zone; split a network into equal parts or with VLSM; summarize networks, addresses and ranges into the fewest CIDR blocks and a single supernet. Works offline. |
| **SSH** | Saved hosts in groups, password or key authentication (Ed25519, RSA, ECDSA), host key verification and an xterm terminal with tabs, extra keys and background sessions. |

Settings let you lock Netrik with a 4-digit PIN or your fingerprint, pick the theme (system, light or dark), use your wallpaper colors (Material You) or the Netrik colors, enable pure black for AMOLED screens and choose the app language.

## Privacy and security

- No ads, no analytics, no tracking.
- The app only reaches the internet when you ask it to: the public IP lookup (`api.ipify.org`, on tap or automatically if you enable it), the OUI database update (`standards-oui.ieee.org`) and, of course, the hosts you ping, scan or connect to.
- SSH passwords and private keys are encrypted with a key kept in the Android Keystore and never leave the device.
- Read the full [security policy](SECURITY.md), also available in Settings → Security policy. Found a vulnerability? Report it privately as described there, not in a public issue.

## Installing

Download `netrik-release.apk` from the [latest release](https://github.com/henriquesebastiao/netrik/releases/latest) and open it on your phone. Android asks you to allow installing apps from your browser or file manager the first time.

Before installing, you can check that the file is genuine:

- **Checksum:** each release also has `netrik-release.apk.sha256`. On a computer, run `sha256sum -c netrik-release.apk.sha256` in the folder where you saved both files.
- **Signature:** every release is signed with the same key. `apksigner verify --print-certs netrik-release.apk` must show the certificate fingerprint published in [SECURITY.md](SECURITY.md#verifying-a-release). Android itself refuses an update signed with a different key.

Google Play Protect may offer to scan the app, since it doesn't come from Google Play. In some countries (Brazil since September 2026, more later) certified Android devices also ask for apps from verified developers; if Android blocks the install, see [Verifying a release](SECURITY.md#verifying-a-release) for the options.

## Requirements

- Android 8.0 (API 26) or later.
- Some data depends on Android permissions, and the app explains each one before asking:
  - **Location** (Wi-Fi tab): Android only gives the list of Wi-Fi networks and the network name to apps with precise location.
  - **Nearby devices / local network** (Android 17+): needed to talk to devices on your local network.
  - **Notifications** (Android 13+): shows the "active SSH sessions" notification.
- Android doesn't let apps read the ARP table, so a device's MAC address only shows up when the device announces it itself. Netrik says so instead of guessing.

## Building

You need [Android Studio](https://developer.android.com/studio) (or the Android SDK command line tools) and JDK 17.

```bash
git clone https://github.com/henriquesebastiao/netrik.git
cd netrik
./gradlew assembleDebug        # APK in app/build/outputs/apk/debug/
./gradlew test                 # unit tests
```

New to Android development? [CONTRIBUTING.md](CONTRIBUTING.md) walks you through everything, from installing the tools to opening a pull request.

## Tech stack

Kotlin · Jetpack Compose · Material 3 · Hilt · Room · DataStore · Navigation Compose · Coroutines/Flow · JSch · Bouncy Castle · Termux terminal-emulator.

The code is organized by feature (`feature/ping`, `feature/ssh`, …) on top of shared modules in `core/` (design system, network, database, settings). See [CONTRIBUTING.md](CONTRIBUTING.md#how-the-code-is-organized) for a tour.

## Contributing

Bug reports, ideas, translations and code are all welcome.

- Found a bug? [Open an issue](https://github.com/henriquesebastiao/netrik/issues) (also reachable from Settings → Report a bug).
- Want to help with code or translations? Read [CONTRIBUTING.md](CONTRIBUTING.md).

## License

Netrik is free software, licensed under the [GNU General Public License v3.0](LICENSE).

It includes third-party work under their own licenses (also listed in Settings → Open source licenses):

- Roboto Flex and JetBrains Mono fonts — SIL Open Font License 1.1.
- Material Symbols icons — Apache License 2.0.
- JSch (mwiede fork) — BSD license; Bouncy Castle — MIT license.
- Termux terminal-emulator and terminal-view — Apache License 2.0.
- Vendor data from the IEEE MA-L, MA-M and MA-S public registries; port ranking from the Nmap project (numbers only); service names from the IANA port registry.
