# termux-terminal

Terminal emulation and rendering used by Netrik's SSH terminal, vendored from
[termux-app](https://github.com/termux/termux-app) tag `v0.118.3`
(`terminal-emulator` and `terminal-view` modules, Apache License 2.0, see `LICENSE`).
They are derived from Jack Palevich's Terminal Emulator for Android.

Netrik builds them from source instead of pulling the JitPack artifacts, so the app has no
prebuilt dependency and no native library (needed for F-Droid).

## What is included

- `com.termux.terminal`: `KeyHandler`, `Logger`, `TerminalBuffer`, `TerminalColorScheme`,
  `TerminalColors`, `TerminalEmulator`, `TerminalOutput`, `TerminalRow`, `TerminalSessionClient`,
  `TextStyle`, `WcWidth`, plus the upstream unit tests.
- `com.termux.view`: `TerminalRenderer` only.

## Changes from upstream

- Removed `TerminalSession`, `JNI` and `jni/termux.c` (they spawn a local process; Netrik's session
  is the SSH channel in `SshTerminal`), and `ByteQueue` (only used by `TerminalSession`) with its test.
- `TerminalSessionClient`: removed the callbacks that took a `TerminalSession`.
- Removed `TerminalView`, `TerminalViewClient`, `GestureAndScaleRecognizer` and the text selection
  classes (Netrik's View is `TerminalCanvasView`).
- Gradle build rewritten in Kotlin DSL (Java 17, minSdk 26).

Every other file is unchanged. To update, copy the same files from a newer tag and reapply the
changes above.
