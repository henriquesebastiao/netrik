# Security Policy

Netrik handles sensitive material: SSH passwords and private keys, the list of servers you manage, the fingerprints you trust, and information about the networks and devices around you. This document explains what the app stores, how it protects it, what it sends over the network, its known limits, and how to report a vulnerability.

The short version: **everything stays on your device, secrets are encrypted with a hardware-backed key when the device supports it, nothing is logged or sent to the developer, and the app never connects anywhere you didn't ask it to.**

## Contents

1. [Supported versions](#supported-versions)
2. [Reporting a vulnerability](#reporting-a-vulnerability)
3. [What Netrik stores](#what-netrik-stores)
4. [How SSH credentials are protected](#how-ssh-credentials-are-protected)
5. [App lock (PIN and fingerprint)](#app-lock-pin-and-fingerprint)
6. [SSH connections and host key verification](#ssh-connections-and-host-key-verification)
7. [Network activity](#network-activity)
8. [Permissions](#permissions)
9. [Logging, clipboard and screenshots](#logging-clipboard-and-screenshots)
10. [Backups](#backups)
11. [Known limitations](#known-limitations)
12. [Recommendations for users](#recommendations-for-users)
13. [Responsible use](#responsible-use)
14. [Third-party components](#third-party-components)

---

## Supported versions

Netrik is in early development. Security fixes are made on the `main` branch and shipped in the **latest release only**. Please update before reporting an issue.

| Version | Supported |
| --- | --- |
| Latest release | ✅ |
| Older releases | ❌ |

## Reporting a vulnerability

**Please do not report security problems in public issues.** Public issues are visible to everyone, including people who could abuse the problem before it's fixed.

Report privately through GitHub: **[Report a vulnerability](https://github.com/henriquesebastiao/netrik/security/advisories/new)** (repository → *Security* tab → *Report a vulnerability*).

Please include:

- the Netrik version (Settings → Version), device model and Android version;
- what the problem is and what an attacker could do with it;
- step-by-step instructions to reproduce it, and a proof of concept if you have one;
- whether the problem needs physical access, another app on the device, a position on the same network, or a malicious server.

> Never include real passwords, private keys, the content of SSH sessions or anyone's IP/MAC addresses in a report. Use throwaway keys and test servers.

### What to expect

| Step | Target time |
| --- | --- |
| Acknowledgement of your report | within 7 days |
| First assessment (confirmed or not, severity) | within 14 days |
| Fix for confirmed high/critical issues | as soon as possible, usually within 30 days |

Netrik is maintained by volunteers, so these are goals, not guarantees. You'll be kept informed, credited in the advisory and release notes if you wish, and asked before anything is disclosed. Please give us a reasonable time to fix the problem before publishing details (coordinated disclosure, 90 days by default).

### In scope

- Exposure of stored SSH passwords, private keys or key passphrases.
- Bypassing host key verification (connecting to an unknown or changed key without the user's confirmation).
- Secrets or session content reaching logs, other apps, backups or the network.
- Other apps on the device being able to read Netrik's data or trigger its actions.
- Netrik contacting servers the user didn't ask it to contact.
- Crashes or code execution triggered by malicious network replies (ping output, mDNS/NetBIOS/SSDP/UPnP replies, SSH servers, IEEE files).

### Out of scope

- Attacks that require a rooted or compromised device, or an unlocked device in the attacker's hands.
- Vulnerabilities in Android itself or in the SSH servers you connect to.
- Information that Android freely gives to any app with the same permissions.
- Results of scans being visible to the networks you scan (that's how networking works).

## What Netrik stores

All data lives in the app's private storage on your device. Netrik has **no account, no server, no analytics and no crash reporting**: the developer never receives any of it.

| Data | Where | Protection |
| --- | --- | --- |
| SSH passwords, private keys and key passphrases | App database (Room) | **Encrypted** with AES-256-GCM; key in the Android Keystore (see below) |
| SSH hosts: name, address, port, user, group, key file name | App database | Private app storage (not encrypted) |
| Trusted SSH host keys (known_hosts) | App database | Private app storage |
| Port knocking sequences and groups (name, host, protocols, ports) | App database | Private app storage. **Export** writes them as plain JSON to the file you choose; it holds no passwords or keys, but it does reveal your knock sequences, so keep it private |
| Recent targets (Ping, Traceroute, Port Scanner) and MAC lookups | App database | Private app storage |
| IEEE vendor (OUI) database | App database | Public data |
| App lock PIN | Never stored; only a keyed verifier (see below) | HMAC-SHA256 with an Android Keystore key |
| Settings (theme, language, terminal font size, network options) | DataStore / preferences | Private app storage |
| Device scan, neighbor discovery, Wi-Fi scan and port scan results | Memory only | Gone when the app process ends |
| SSH session output (terminal scrollback) | Memory only | Gone when the session closes |

"Private app storage" means other apps can't read it on a normal (non-rooted) Android device. Uninstalling Netrik deletes all of it, including the Keystore key.

## How SSH credentials are protected

- **Encryption at rest.** Passwords, private keys and key passphrases are encrypted before they're written to the database, with **AES-256-GCM** (authenticated encryption, a fresh random IV per value).
- **Non-exportable key.** The encryption key is generated inside the **Android Keystore** and can't be read or exported by the app or anyone else. On devices with a secure element or trusted execution environment, the key never leaves that hardware.
- **The database alone is useless.** A copy of the database (for example from a backup) can't be decrypted without the Keystore key of the original device. In that case Netrik treats the secret as missing and asks you to enter it again.
- **Decrypted only when needed.** Secrets are decrypted right before connecting and the byte arrays are wiped from memory after the attempt.
- **Private keys are validated before use.** The app reads OpenSSH, PEM/PKCS#8 and PuTTY keys (Ed25519, ECDSA, RSA), checks an encrypted key's passphrase locally before going to the network, and rejects files that aren't private keys or are larger than 64 KB.
- **Picking a key doesn't grant broad file access.** Key files are chosen through the system file picker (Storage Access Framework); Netrik doesn't request storage permissions and only reads the file you select, once, to store an encrypted copy.
- **"Save connection" is optional.** If you turn it off, the connection is made once and nothing is stored.

## App lock (PIN and fingerprint)

Settings → Security → **App lock** protects Netrik with a 4-digit PIN, optionally unlocked with a fingerprint or face.

- **When it locks:** every time Netrik starts, and whenever the device screen turns off (that is, when the device locks). Switching to another app without locking the device doesn't lock Netrik.
- **The PIN is never stored.** Netrik keeps a random salt and an HMAC-SHA256 of the PIN computed with a **non-exportable Android Keystore key**. A 4-digit PIN has only 10,000 values; because the key never leaves the device, a copy of the app data is useless for guessing it, and every guess has to go through the app on that device.
- **Attempt limit:** after 5 wrong PINs, the app waits 30 s, doubling on every further mistake, up to 15 min. The counter is stored, so closing the app or rebooting doesn't reset it. PINs are compared in constant time.
- **Biometrics:** only Class 3 ("strong") biometrics through the system `BiometricPrompt`. Biometric data never reaches the app; the system only reports whether the person was recognized. Turning it on requires a successful scan, and the PIN always keeps working.
- **What stays hidden:** while locked, the lock screen is a window above everything else in the app (including open dialogs), takes the keyboard focus and hides the app from accessibility services. The app's state (open screens, running tools) is kept underneath.
- **Changing or turning off the lock** requires the current PIN, counted against the same attempt limit.
- **What keeps running:** open SSH sessions stay connected while the app is locked, and **Disconnect all** in the notification works without unlocking (it only reduces exposure).
- **Forgotten PIN:** there is no recovery or reset inside the app, on purpose. The only way out is clearing Netrik's data in Android settings, which erases everything the app stored.
- The recent apps preview isn't hidden, so the last screen before locking may show there.

## SSH connections and host key verification

Netrik uses [JSch](https://github.com/mwiede/jsch) (maintained fork) with [Bouncy Castle](https://www.bouncycastle.org/) for modern algorithms (Ed25519, X25519, ML-KEM hybrid key exchange where the server supports it).

- **Strict host key checking.** The server's key is checked against the keys you trusted **before any credential is sent**.
- **Unknown host:** the connection is closed and the app shows the key type and its **SHA-256 fingerprint** (OpenSSH format). Only after you confirm is the key saved and the connection made again.
- **Changed key:** the connection is closed and the app shows a man-in-the-middle warning with both fingerprints. Replacing the key requires an explicit action; the default is to cancel.
- **No silent prompts.** Netrik never answers "yes" to a host key or retries a password on its own.
- Keys are stored per `host` or `[host]:port`, like OpenSSH's `known_hosts`.
- The app keeps idle sessions alive (keep-alive every 30 s) and keeps open sessions running in the background with a visible **"N active SSH sessions"** notification, which also offers **Disconnect all**.

## Network activity

Netrik only sends traffic as a direct result of something you do:

| Action | Destination | What is sent |
| --- | --- | --- |
| Tapping **Show public IP**, or on every network change when Settings → **Always show public IP** is on (off by default) | `api.ipify.org` (HTTPS) | A request that reveals your public IP to that service |
| Tapping **Update** in MAC/OUI Lookup | `standards-oui.ieee.org` (HTTPS) | Downloads of the public IEEE registries, with the `Netrik/<version>` user agent |
| Ping, Traceroute | The target you typed | ICMP echo requests (via the system `ping` tool) |
| Port Scanner | The host or network you typed | TCP connections / UDP probes to the chosen ports |
| Devices scan | Your local subnet | Ping, TCP connections to common ports, mDNS, NetBIOS, SSDP/UPnP, MNDP and Ubiquiti discovery queries |
| Neighbor Discovery (while the screen is open) | Broadcast on your local network | Empty MNDP requests (UDP 5678) and Ubiquiti discovery probes (UDP 10001), every 2 s at first, then every 10 s; Netrik also listens on UDP 5678 for MikroTik announcements |
| SSH | The host you saved or typed | An SSH connection |
| Port Knocking | The host of the knock you tap | The sequence you saved: TCP SYNs (a connection attempt closed right away), empty UDP datagrams and ICMP echo requests; then, if set, one TCP connection to the port to test |
| **Source code**, **Report a bug** or **Security policy** in Settings | `github.com` (in your browser) | Opens the repository, its issues page or this policy |

There are no background network requests, update checks, telemetry or ads.

Local network discovery also *receives* data from devices that answer (names, services, sometimes MAC addresses, MikroTik/Ubiquiti identity, model and version). That data is treated as untrusted input: replies are size-limited, device description files are only fetched from the same IP that answered, and nothing received is executed.

## Permissions

Each permission is requested only when the related feature is used, with an explanation first.

| Permission | Why |
| --- | --- |
| `INTERNET`, `ACCESS_NETWORK_STATE` | Network tools and current network details |
| `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE`, `CHANGE_WIFI_MULTICAST_STATE` | Wi-Fi details, Wi-Fi scans, receiving mDNS/SSDP replies |
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | Android only gives the Wi-Fi network list and the network name to apps with precise location. **Netrik never reads, stores or sends your location.** |
| `ACCESS_LOCAL_NETWORK` (Android 17+, "Nearby devices") | Talking to devices on your local network |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS` | Keeping SSH sessions alive in the background, with a visible notification |

Netrik's components are not exported to other apps, except the launcher entry point.

## Logging, clipboard and screenshots

- **No logging of sensitive data.** Netrik never writes passwords, private keys, passphrases or SSH session content to the system log. The terminal emulator's own logging is disabled.
- **Clipboard.** Copy actions (an IP, a result, **Copy output** in the terminal) put the text on the system clipboard, where other apps and the keyboard may read it. Avoid copying terminal output that contains secrets.
- **Screenshots.** Netrik doesn't block screenshots or the recent apps preview, so an open terminal may appear in those. Password fields are masked by default.

## Backups

Android may include app data in device backups (Google backup or device-to-device transfer).

- **Encrypted secrets stay safe**: the Keystore key is never backed up, so restored passwords and keys can't be decrypted and must be entered again.
- **Not encrypted in backups**: the list of SSH hosts (names, addresses, ports, users), trusted host keys, recent targets and settings. If that's a concern for you, disable app backup for Netrik in Android settings.

## Known limitations

Being honest about what Netrik doesn't do:

- The app lock is off by default. Without it, anyone who can unlock your phone can open Netrik and connect to saved servers. With it, a 4-digit PIN is the last line of defense; the attempt limit slows guessing but doesn't make it impossible.
- A dialog that appears while the app is locked (e.g. the result of an SSH connection started just before locking) may show above the lock screen.
- Secrets are protected at rest, but while connecting they're in the app's memory, like in any SSH client.
- Keyboard-interactive authentication with several prompts (e.g. password + one-time code) isn't supported.
- There is no SSH agent forwarding, port forwarding, X11 forwarding or SFTP. This reduces the attack surface.
- The app can't read the ARP table on Android, so MAC addresses come only from what devices announce; a malicious device can announce a false name or MAC.

## Recommendations for users

- Prefer **key authentication** over passwords, and protect your private keys with a passphrase.
- **Check fingerprints** with the server administrator (or `ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub` on the server) before trusting a host. Never accept a changed key without knowing why it changed.
- Use a **screen lock** on your phone, turn on Netrik's **app lock**, and keep Android and Netrik updated.
- Use dedicated keys for your phone, so you can revoke them on the servers if the phone is lost.
- If your phone is lost or stolen, revoke its keys (`authorized_keys`) and change the passwords saved in Netrik.

## Responsible use

Netrik includes tools (port scanning, network discovery) that can be used to attack networks. **Only scan and connect to networks and systems you own or are explicitly authorized to test.** Unauthorized scanning may be illegal where you live and may violate your network provider's terms. The app reminds you of this on the Port Scanner screen.

## Third-party components

Security issues in these libraries are tracked upstream; Netrik updates them when fixes are released:

- [JSch (mwiede fork)](https://github.com/mwiede/jsch) — SSH protocol (BSD).
- [Bouncy Castle](https://www.bouncycastle.org/) — cryptography (MIT).
- [Termux terminal-emulator / terminal-view](https://github.com/termux/termux-app) — terminal emulation and rendering (Apache 2.0). Only the Java emulator and renderer are used; Termux's native library isn't included.
- AndroidX (Compose, Room, DataStore, Navigation), Hilt, Kotlin coroutines and kotlinx.serialization (Apache 2.0).

Dependency versions are listed in [`gradle/libs.versions.toml`](gradle/libs.versions.toml).
