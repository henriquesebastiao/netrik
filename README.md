# Netrik

**Netrik** is an open source network toolkit for Android, made for network analysts, pentesters and anyone curious about the network they're on. It runs entirely on the phone, without root, and doesn't track you.

Available in English and Brazilian Portuguese.

## Features

| Tool | What it does |
| --- | --- |
| **Current network** | Local IP, gateway and public IP at a glance; a details sheet with network name, signal, band, channel, BSSID, mask/CIDR, DNS and IPv6, with one-tap copy. The public IP is only looked up when you ask for it (or always, if you turn that on in Settings). |
| **Ping** | Real-time replies with seq, TTL and time, count or continuous mode, latency chart and statistics (loss, min/avg/max, jitter). |
| **Traceroute** | Hop-by-hop route with reverse DNS and the latency of each router. |
| **Devices** | Finds the devices on your local network (ping + TCP probing, mDNS, NetBIOS and UPnP), with vendor, hostname and quick actions. |
| **Wi-Fi** | Nearby networks with security, channel, width and signal quality, plus a 2.4/5/6 GHz spectrum chart. |
| **Port Scanner** | TCP and UDP scans of a single host or a whole network (up to /22), with Top 100/Top 1000/custom port lists and service names. |
| **MAC/OUI Lookup** | Vendor of a MAC address from the offline IEEE database (MA-L, MA-M and MA-S), updatable from the official files. |
| **SSH** | Saved hosts in groups, password or key authentication (Ed25519, RSA, ECDSA), host key verification and an xterm terminal with tabs, extra keys and background sessions. |

Settings let you lock Netrik with a 4-digit PIN or your fingerprint, pick the theme (system, light or dark), use your wallpaper colors (Material You) or the Netrik colors, enable pure black for AMOLED screens and choose the app language.

## Privacy and security

- No ads, no analytics, no tracking.
- The app only reaches the internet when you ask it to: the public IP lookup (`api.ipify.org`, on tap or automatically if you enable it), the OUI database update (`standards-oui.ieee.org`) and, of course, the hosts you ping, scan or connect to.
- SSH passwords and private keys are encrypted with a key kept in the Android Keystore and never leave the device.
- Read the full [security policy](SECURITY.md), also available in Settings → Security policy. Found a vulnerability? Report it privately as described there, not in a public issue.

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
