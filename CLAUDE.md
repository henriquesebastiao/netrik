# Project: Netrik — Android app development

## Context
Netrik is an Android network toolkit for network analysts, pentesters and enthusiasts. The full design (icon, design system and screens) was created in Claude Design and lives at:

https://claude.ai/design/p/a79c09a3-0c10-41a4-97e4-8fde20361378?file=Netrik+Prot%C3%B3tipo+naveg%C3%A1vel.dc.html

That clickable prototype is the visual source of truth: follow its colors, typography, spacing, components and navigation flows. If something in the design is unfeasible or ambiguous in Compose, pick the most faithful solution and record the decision in the report.

### Accessing the design
- The link requires login; WebFetch returns 403. Use the `claude_design` MCP (server `https://api.anthropic.com/v1/design/mcp`, authenticate with `/design-login`), project `a79c09a3-0c10-41a4-97e4-8fde20361378`: `list_files` and `read_file`.
- Main files: `Netrik Protótipo navegável.dc.html` (flow and navigation rules), `Netrik Etapa 1.dc.html` (icon, seed, design system), `NetrikIcon`, `NetrikHub`, `NetrikTool` (Ping/Traceroute), `NetrikDevices`, `NetrikWifi`, `NetrikPorts`, `NetrikSSH`, `NetrikOUI`. The stage numbers in the design file names differ from the plan below; the plan below wins.
- Before implementing a screen, re-read its design file (states, texts, spacing).
- If the design can't be accessed, STOP and say so. Don't guess or recreate the design on your own.
- Screens that don't exist in the design (e.g. Settings) are built from the existing design system components; call that out in the report.

### Approved design decisions
- Icon 2b ("Ping": a node emitting two waves, replaces 2a), petrol blue seed `#136B79`, hub as a grouped list (Diagnostics, Discovery, Remote access).
- Fallback scheme generated with material-color-utilities (SchemeTonalSpot) in `core/designsystem/theme/Color.kt`; success/warning in `ExtendedColors` (harmonized with the seed).
- Roboto Flex for the UI and JetBrains Mono for all technical data (`NetrikTheme.dataTypography`), bundled in `res/font` (Latin subset).
- Icons: Material Symbols Rounded as vector drawables in `res/drawable` (`ic_<name>` and `ic_<name>_filled`). For a new icon: `scripts/material_symbol.py name [--filled name]`.
- Theme: Settings offers system/light/dark (system is the default), wallpaper colors (dynamic color, on by default, Android 12+) and pure black (AMOLED). The prototype's theme button doesn't go into the app.
- Languages: English (default, `values/`) and Brazilian Portuguese (`values-pt-rBR/`). Settings → App language offers "System default" (default; English when the device language isn't available), English and Português (Brasil). Android 13+ uses the system per-app language (`res/xml/locales_config.xml`); older versions store the choice and wrap the Activity/Service context (`AppLanguages.wrap`). App bundle language splits are disabled so every language ships.
- App lock: Settings → Security. Exactly 4-digit PIN, never stored (HMAC-SHA256 with a non-exportable Keystore key, `KeystorePinHasher`); 5 free attempts, then 30 s doubling up to 15 min, persisted. Locks on cold start and on `ACTION_SCREEN_OFF` only (not when switching apps). Optional Class 3 biometrics via `androidx.biometric` (MainActivity is a `FragmentActivity`). The lock screen is a full-screen Dialog so it sits above any app dialog; the app stays composed underneath. The recent apps preview is not hidden (maintainer's choice).
- Public IP: queried (api.ipify.org) only when the user taps "Show public IP", or automatically on every network change when Settings → Network → "Always show public IP" is on (off by default).
- Hub "Current network" card shows only local IP, gateway and public IP; a "Network details" button opens a bottom sheet (not in the design, built from design system components) with every field and "Copy all".
- Wi-Fi: Settings → Network → "Hide hidden networks" (off by default) drops networks without SSID from the list and spectrum; the connected network always stays. The vendor shows on its own line above the BSSID.
- Local network (Android 17+): with targetSdk 37, any TCP/UDP to LAN IPs (and mDNS) requires the runtime permission `ACCESS_LOCAL_NETWORK` (Nearby devices group); without it traffic is silently dropped (timeout/EPERM), ping included. Use `LocalNetworkAccess`/`LocalAddress` before talking to the LAN and `rememberLocalNetworkPermissionRequest` to ask. Applies to Port Scanner and SSH.
- MACs on the LAN: the app can't read `/proc/net/arp` or `ip neigh` (denied by SELinux; the unbound-netlink `RTM_GETNEIGH` trick is blocked too on Android 13+ by `nlmsg_getneigh`). A MAC only shows when the device itself announces it (NetBIOS, mDNS, MNDP, Ubiquiti discovery); otherwise "MAC unavailable".
- Ports: `assets/ports/top-tcp.txt` and `top-udp.txt` hold only the numbers of the 1000 most common ports in Nmap's order (the `nmap-services` file is NPSL and isn't bundled); service names come from the IANA registry (`services.tsv.gzip`). Sources in `assets/ports/SOURCES.txt`.
- SSH: JSch (`com.github.mwiede:jsch` fork, BSD) + Bouncy Castle (`bcprov-jdk18on`, MIT). `JschSetup.install()` forces the `com.jcraft.jsch.bc.*` classes for Ed25519/Ed448/X25519/ML-KEM: on Android JSch would pick the JCE ones, which depend on the system provider. Password, private key and key passphrase are encrypted (AES-GCM, non-exportable Android Keystore key, `KeystoreSecretCipher`); none of it is ever logged. Trusted host keys live in Room (`ssh_known_host`, id `host` or `[host]:port`, as in OpenSSH); an unknown or changed key closes the connection before authentication and the UI asks; after trusting, the app reconnects.
- SSH terminal: emulation and rendering with Termux `terminal-emulator`/`terminal-view` (Apache 2.0, JitPack, restricted to the `com.github.termux.termux-app` group in `settings.gradle.kts`). Termux's `TerminalView`/`TerminalSession` aren't used (the session is final and spawns a local process via JNI): the View is `TerminalCanvasView` and the session is `SshTerminal` (JSch shell channel). `libtermux.so` is excluded from the APK (unused and not 16 KB aligned). Sessions live in `SshSessionManager` (singleton) and `SshSessionService` (foreground, `specialUse`) keeps them alive with the app in the background. Terminal colors are fixed and dark (design palette in `TerminalTheme`); terminal font size in DataStore.
- Release: R8 renames enums used as type-safe route arguments; `proguard-rules.pro` keeps the enum names in `com.netrik.navigation`. Test the release APK on the emulator when touching routes.
- Publishing: applicationId `com.henriquesebastiao.netrik` (code namespace stays `com.netrik`); debug is `.debug` / "Netrik Debug" so both install side by side. Signing from `NETRIK_KEYSTORE_FILE`/`NETRIK_KEYSTORE_PASSWORD`/`NETRIK_KEY_ALIAS`/`NETRIK_KEY_PASSWORD` or a git-ignored `keystore.properties`; v2+v3 only; unsigned when absent, build fails with `-Pnetrik.requireSigning=true`. Version from the tag: `-Pnetrik.version=v1.2.3` → 1.2.3 / 1002003. `dependenciesInfo` is off (Google-encrypted blob, flagged by F-Droid/IzzyOnDroid). `network_security_config` allows cleartext (UPnP descriptions are plain http on the LAN; Android 9+ blocked them silently) and trusts only system CAs. `.github/workflows/release.yml` builds on "release published", verifies the signature (and `vars.NETRIK_CERT_SHA256` if set) and 16 KB alignment, and uploads `netrik-release.apk` + `.sha256`; actions pinned by SHA. Android developer verification (Brazil since 2026-09-30) is the maintainer's call: register in the Android Developer Console or users need the advanced flow/adb.
- Neighbor discovery (`feature/neighbors`, `core/neighbor`): MikroTik MNDP (UDP 5678, broadcast; 4-byte header + TLVs with big-endian 2-byte type/length, types from the Wireshark dissector, uptime little-endian) and Ubiquiti discovery (UDP 10001, probes v1 `01 00 00 00` and v2 `02 08 00 00`, 1-byte type + 2-byte length TLVs; written from the protocol, no Nmap code since the NSE scripts are NPSL). Port 5678 is bound with SO_REUSEADDR so the tool and the Devices scan can listen together; requests go to 255.255.255.255 and each interface's broadcast, every 2 s for 10 s then every 10 s, only while the screen is visible. The Devices scan uses the same announcements (`InfoSource.Mndp/Ubiquiti`, identity ranks right after reverse DNS). CDP/LLDP are layer 2 frames (raw `AF_PACKET`, root only): not supported, and the screen says so. Not in the design: built from the tool components. Emulator test: `adb emu redir add udp:5678:5678` and send a crafted MNDP packet to 127.0.0.1:5678 (arrives from 10.0.2.2).
- Port Knocking (`feature/knock`, `core/knock`): not in the design, built from the SSH list/form patterns (groups, long-press menu, full-screen form). Steps are TCP (non-blocking connect closed right away: the SYN is the knock), UDP (empty datagram) or ICMP (echo request through an ICMP datagram socket, `/system/bin/ping` as fallback; ICMP has no port, so the step takes an optional payload size). The run sheet only claims "sequence sent"; the optional TCP "port to test" is the only proof the port opened. Saved in Room (`knock_group`, `knock_profile`, `knock_step`, DB v4). Export/import is versioned JSON (`netrik-knock`, v1, `kotlinx-serialization-json`) through the system file picker; import merges groups by name (case-insensitive) and skips identical knocks and invalid entries.
- OUI: the IEEE database ships in `assets/oui/*.csv.gzip` (`.gzip` extension, not `.gz`: AGP decompresses `.gz` at build time) and is imported into Room on first use. IEEE downloads need a custom User-Agent (`Netrik/<version>`): Android's default gets HTTP 418. The IEEE CSVs have no registration date, so none is shown.

## Language rules (MANDATORY)
- Everything in the repository is in English: code, identifiers, comments, KDoc, test names, Gradle/ProGuard/manifest comments, scripts, docs and commit messages.
- No user-facing text in Kotlin: every string goes into `values/strings.xml` (English) **and** `values-pt-rBR/strings.xml` (Brazilian Portuguese), same keys. Plurals use `<plurals>` (Portuguese also needs the `many` quantity).
- Numbers and dates are formatted with the current locale (`Locale.getDefault()` follows the app language), never a hardcoded locale.
- Talk to the maintainer in Brazilian Portuguese (reports, plans, questions).

## Git rules (MANDATORY)
- NEVER run `git commit`, `git add`, `git push`, `git stash`, `git reset`, `git checkout`, `git rebase`, `git merge` or any command that changes history, the index or branches. Read-only commands (`git status`, `git diff`, `git log`) are allowed.
- When a task is done, write the commit message for its changes to `commit-message.md` (repository root), so the maintainer commits by hand.
- If the file already has content, overwrite it: it must always describe only the changes not yet committed.
- Format: Conventional Commits (`feat:`, `fix:`, `refactor:`, `chore:`, `build:`, `test:`, `docs:`), title of at most 72 characters and a body with an objective list of what changed and why, in English.
- If the changes are better split into several commits, write each message separately in the file, saying which files belong to each.
- `commit-message.md` is in `.gitignore`.

## Stack and architecture
- Kotlin, Jetpack Compose, Material 3 (dynamic color with the design's own scheme as fallback), edge-to-edge.
- minSdk 26, target and compile SDK at the latest stable.
- Gradle Kotlin DSL with a version catalog (`libs.versions.toml`).
- MVVM with unidirectional data flow: UI (Compose) → ViewModel (StateFlow of immutable UI state) → data layer (repositories/data sources).
- Coroutines and Flow for everything asynchronous; network work on `Dispatchers.IO`, with bounded concurrency (Semaphore/worker pools) in the scanners and proper cancellation when leaving the screen or tapping "Stop".
- Hilt for dependency injection, Room for structured data (SSH hosts and groups, histories), DataStore for preferences, Navigation Compose with type-safe routes.
- Organized by feature (`feature/ping`, `feature/traceroute`, `feature/devices`, `feature/wifi`, `feature/portscan`, `feature/ssh`, `feature/knock`, `feature/neighbors`, `feature/oui`, `feature/settings`) plus `core/` (design system, network, database, settings, utilities). Adding a new tool should be easy (see `NetrikTool`).
- For every new dependency, justify the choice in the report and prefer maintained libraries with an open source compatible license (the project is GPL-3.0).

## Quality rules
- Never make up data or fake results. If Android doesn't allow getting some information, show that honestly in the UI (e.g. "MAC unavailable") and explain it in the report.
- Implement the design states: empty, running, result, error and no connection.
- Write unit tests for pure logic: ping/traceroute output parsing, MAC normalization, OUI lookup, CIDR/IP range math, port list parsing (e.g. `22,80,8000-8100`), channel ↔ frequency conversion, terminal key encoding, etc.
- Before finishing a task, run `./gradlew assembleDebug` and `./gradlew test` and fix whatever fails. `./gradlew lintDebug` must have no errors.
- Never log passwords, private keys or SSH session content.

## Technical notes per feature
Research and validate each point against the current Android APIs before implementing.

- **Ping**: non-root apps can't open raw ICMP sockets. Use the `/system/bin/ping` binary through ProcessBuilder, reading the output in real time and parsing seq, TTL and time. Compute the statistics (loss, min/avg/max, jitter) in the app.
- **Traceroute**: there is usually no binary on Android. Implement it with `ping` and an increasing TTL (one hop per TTL), capturing the IP that answers "Time to live exceeded". Reverse-resolve hostnames asynchronously.
- **LAN scanner**: since Android 10 apps can't read `/proc/net/arp`, and newer versions also restrict `ip neigh`. Discover by concurrent probing (ICMP via ping and/or TCP connect on common ports) and get names via mDNS (NsdManager) and NetBIOS/SSDP. Handle MAC unavailability gracefully in the UI.
- **Wi-Fi scanner**: use WifiManager/ScanResult (frequency, centerFreq0/1, channelWidth, capabilities and, on API 33+, the security types). Check the right permission combination per API level (location, NEARBY_WIFI_DEVICES) and the location-on requirement. Respect Android's scan throttling and show it in the UI. Draw the spectrum chart with Compose Canvas.
- **Port scanner**: TCP via connect with a configurable timeout. For UDP, no reply doesn't mean open: classify as "open|filtered" when there is no reply and "closed" on port unreachable, making it clear in the UI. Ship Top 100 and Top 1000 port lists with service names.
- **SSH**: JSch fork with Ed25519, RSA and ECDSA keys; host key verification with a fingerprint confirmation screen and known_hosts storage; saved passwords and private keys encrypted with the Android Keystore.
- **OUI**: the IEEE database (MA-L, MA-M and MA-S) bundled and working offline, with an option to update from the IEEE public files. Accepts MACs in many formats and partial prefixes. Shared by the LAN and Wi-Fi scanners.

## Workflow
Work on ONE task at a time. For larger tasks, present a plan before coding and wait for approval. At the end: make sure build and tests pass, write `commit-message.md`, send a short report (what was done, decisions, limitations found, how to test) and STOP. Wait for approval before moving on.

Original plan (all stages are done):
- **Stage 0 — Foundation**: project, version catalog, Hilt, Material 3 theme from the design system, adaptive icon, Navigation Bar, Tools hub with the "Current network" card, `.gitignore` and this `CLAUDE.md`.
- **Stage 1 — OUI Lookup**: database, shared repository and lookup screen.
- **Stage 2 — Ping and Traceroute**.
- **Stage 3 — Wi-Fi scanner**: list, spectrum and permission flow.
- **Stage 4 — LAN device scanner**: list, details and quick actions.
- **Stage 5 — Port Scanner**: TCP/UDP, single host and network.
- **Stage 6 — SSH**: hosts, groups, form, secure storage and host key verification.
- **Stage 7 — SSH terminal**: emulation, extra keys bar and multiple sessions.

## Build environment
- JDK 17 via `gradle/gradle-daemon-jvm.properties` (the machine's default JDK may be newer than AGP supports).
- `./gradlew assembleDebug`, `./gradlew test` and `./gradlew lintDebug`.
- Emulator: AVD `Pixel_9`; start it with `-memory 4096`, because with 2 GB the app is killed for lack of memory right after boot.

## Progress
- Stages 0–7: done and committed.
- Stage 2: traceroute still needs validation on a physical device (on the emulator ICMP is simulated: TTL always 255, no "TTL exceeded").
- Stage 3: the emulator only has the "AndroidWifi" network (2.4 GHz, no 6 GHz).
- Stage 4: on the emulator the network is simulated (no real names or MACs); mDNS/NetBIOS/UPnP need validation on a physical device.
- Stage 5: on the emulator, 10.0.2.2 is the host machine's loopback: a good reference for open TCP ports.
- Stage 6/7: tested on the emulator with a rootless local sshd (key, 10.0.2.2:2222) and the `lscr.io/linuxserver/openssh-server` container (password, 10.0.2.2:2223). Phone in landscape with the keyboard open: the terminal hides the bar and tabs and uses a single row of extra keys (~2 lines of text left).
- English/Portuguese translation, Settings screen, README.md, CONTRIBUTING.md and SECURITY.md: done.
- App lock (PIN + fingerprint): done and committed. Tested on the emulator with an emulated fingerprint (`adb emu finger touch 1` after enrolling it in the system settings; a device screen lock is required, e.g. `adb shell locksettings set-pin 1111`).
- Network details sheet, "Always show public IP", "Hide hidden networks" and Wi-Fi vendor line fix: done and committed.
- Port Knocking (groups, TCP/UDP/ICMP steps, port test, JSON export/import): done and committed. Tested on the emulator against a listener on the host (10.0.2.2): TCP/UDP knocks arrive in order with the delay; ICMP checked with the kernel OutEchos counter (ping sockets count each echo twice, the system ping too).
- Neighbor Discovery (MNDP + Ubiquiti) and its use in the Devices scan: done and committed. Parsing and UI tested on the emulator with injected packets; real discovery (broadcast reaching a MikroTik/Ubiquiti) still needs a physical device on a network with one.
- Release preparation (signing, package name, version from tag, network security config, release workflow): done (waiting for commit/approval). Release APK tested on the emulator with a throwaway key: SSH password and Ed25519 key, Port Knocking import/run, OUI lookup, Neighbor Discovery. The real release key and GitHub secrets are still to be created by the maintainer.
