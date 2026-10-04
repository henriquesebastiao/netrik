# Contributing to Netrik

Thanks for wanting to help! This guide assumes **no experience with Kotlin or Android**. It explains every step, from installing the tools to getting your change merged. If you already know Android, skim the [project rules](#project-rules) and the [pull request checklist](#pull-request-checklist).

## Contents

1. [Ways to contribute (with and without code)](#ways-to-contribute)
2. [Reporting a bug](#reporting-a-bug)
3. [Setting up your computer](#setting-up-your-computer)
4. [Getting the code with Git and GitHub](#getting-the-code-with-git-and-github)
5. [Running the app](#running-the-app)
6. [A crash course: the minimum Kotlin and Android you need](#a-crash-course)
7. [How the code is organized](#how-the-code-is-organized)
8. [Recipes for common changes](#recipes-for-common-changes)
9. [Project rules](#project-rules)
10. [Testing your change](#testing-your-change)
11. [Commit messages](#commit-messages)
12. [Opening a pull request](#opening-a-pull-request)
13. [Glossary](#glossary)

---

## Ways to contribute

You don't need to write code to help:

- **Report bugs** you find (see below).
- **Test on your phone.** The emulator can't simulate everything (real Wi-Fi networks, traceroute hops, devices announcing themselves on the LAN). Telling us "it works on my Pixel 7 with Android 15" or "it crashes on my Samsung" is very valuable.
- **Improve translations.** All the texts live in two files (English and Brazilian Portuguese). Fixing a typo or an awkward sentence is a great first contribution. Adding a new language is also welcome.
- **Improve the docs**, including this file.
- **Write code**: fix bugs, polish screens, add tests or new tools.

Not sure where to start? Look at the [open issues](https://github.com/henriquesebastiao/netrik/issues) or open one describing what you'd like to do before you start, so we can agree on the approach.

## Reporting a bug

Open a new issue at <https://github.com/henriquesebastiao/netrik/issues> (the app also opens this page from **Settings → Report a bug**). Please include:

1. **What you did**, step by step ("opened Ping, typed 8.8.8.8, tapped Start").
2. **What you expected** and **what happened instead**.
3. **Your phone and Android version** (Settings → About phone) and the **Netrik version** (Netrik → Settings → Version).
4. Screenshots or a screen recording, if they help.

> ⚠️ Never paste passwords, private keys, the contents of your SSH sessions or your public IP in an issue. Issues are public.

## Setting up your computer

You need three things: **Git**, **JDK 17** and **Android Studio**. They work on Linux, macOS and Windows.

### 1. Git

Git is the tool that keeps the history of the code.

- **Linux**: install it with your package manager, e.g. `sudo apt install git` (Debian/Ubuntu) or `sudo pacman -S git` (Arch).
- **macOS**: run `git --version` in the Terminal; macOS offers to install it.
- **Windows**: download it from <https://git-scm.com/download/win> and keep the default options. Use the "Git Bash" app it installs to run the commands in this guide.

Then tell Git who you are (this goes into your commits):

```bash
git config --global user.name "Your Name"
git config --global user.email "you@example.com"
```

### 2. JDK 17

Android's build tools run on Java. The project is pinned to **JDK 17** (see `gradle/gradle-daemon-jvm.properties`), because the Android Gradle plugin may not support newer versions yet.

- Download **Eclipse Temurin 17** from <https://adoptium.net/temurin/releases/?version=17> and install it.
- Check it in a terminal: `java -version` should mention `17`. If your computer has several JDKs, that's fine: Gradle looks for a JDK 17 on its own.

### 3. Android Studio

Android Studio is the editor (IDE) for Android apps. It also installs the **Android SDK** (the libraries and tools to build apps) and the **emulator** (a virtual phone).

1. Download it from <https://developer.android.com/studio> and run the installer. Accept the default "Standard" setup.
2. Open **Settings → Languages & Frameworks → Android SDK** (on macOS: **Android Studio → Settings**) and, in **SDK Platforms**, make sure the latest Android version is installed (the project compiles against **API 37**). Android Studio usually offers to install missing pieces when you open the project; just accept.

## Getting the code with Git and GitHub

GitHub hosts the code. You can't change the main repository directly; instead you make your own copy (a **fork**), change it, and ask for your changes to be merged (a **pull request**).

1. Create a GitHub account at <https://github.com> if you don't have one.
2. Open <https://github.com/henriquesebastiao/netrik> and click **Fork** (top right). You now have `github.com/YOUR-USER/netrik`.
3. Download your fork (**clone** it):

   ```bash
   git clone https://github.com/YOUR-USER/netrik.git
   cd netrik
   git remote add upstream https://github.com/henriquesebastiao/netrik.git
   ```

   `upstream` is a nickname for the original repository, so you can get the latest changes later with:

   ```bash
   git checkout main
   git pull upstream main
   ```

4. Create a **branch** for your change. A branch is a separate line of work, so `main` stays clean:

   ```bash
   git checkout -b fix-ping-crash
   ```

   Use a short name that says what you're doing.

## Running the app

### Open the project

In Android Studio: **File → Open**, choose the `netrik` folder. The first time, Android Studio runs a **Gradle sync** (bottom status bar): it downloads every library the project needs. It may take a few minutes. Wait until it finishes without errors.

> If the sync complains about the JDK, open **Settings → Build, Execution, Deployment → Build Tools → Gradle** and set **Gradle JDK** to your JDK 17.

### Create a virtual phone (emulator)

1. **Tools → Device Manager → Create Virtual Device**.
2. Pick a phone (e.g. **Pixel 9**) and the latest system image (download it if needed).
3. In **Advanced Settings**, set **RAM to 4096 MB**. With 2 GB the system kills apps right after boot.
4. Finish and start the device with the ▶ button.

### Or use your own phone

1. On the phone: **Settings → About phone**, tap **Build number** 7 times to unlock the developer options.
2. **Settings → System → Developer options** → turn on **USB debugging**.
3. Plug the phone into the computer and accept the prompt on the phone.

Your own phone is the best way to test Wi-Fi, LAN and traceroute features.

### Build and run

Pick the device in the top toolbar and click the green **Run** ▶ button. Android Studio builds the app, installs it and opens it.

From a terminal, the same things are done with **Gradle**, the build tool (the `./gradlew` script downloads the right Gradle version by itself; on Windows use `gradlew.bat`):

```bash
./gradlew assembleDebug   # builds app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug    # builds and installs on the connected phone/emulator
./gradlew test            # runs the unit tests
./gradlew lintDebug       # looks for common Android mistakes
```

## A crash course

You don't need to master Kotlin to contribute. These are the concepts you'll meet in almost every file.

### Kotlin in five minutes

```kotlin
val name = "Netrik"          // val = can't be reassigned (prefer it)
var count = 0                // var = can change
count += 1

val maybe: String? = null    // "?" means the value may be null (missing)
val length = maybe?.length ?: 0   // ?. = "only if not null"; ?: = "otherwise use this"

fun greet(who: String): String = "Hello, $who"   // a function; $who inserts a value in the text

data class Host(val ip: String, val port: Int = 22)   // a simple data holder with a default value

val ports = listOf(22, 80, 443)
val high = ports.filter { it > 100 }   // { ... } is a lambda; "it" is the current item

when (port) {                 // like "switch" in other languages
    22 -> "SSH"
    80, 443 -> "Web"
    else -> "Other"
}
```

**Coroutines** are how Kotlin runs slow work (network, disk) without freezing the screen. You'll see `suspend fun` (a function that may wait), `viewModelScope.launch { ... }` (start background work tied to a screen) and `Flow` (a stream of values over time, e.g. each ping reply). The official tour is great: <https://kotlinlang.org/docs/kotlin-tour-welcome.html>.

### Jetpack Compose (the screens)

The UI is written in Kotlin with **Jetpack Compose**. A screen is made of functions marked `@Composable` that describe what to show for the current state. When the state changes, Compose redraws what changed.

```kotlin
@Composable
fun Counter() {
    var clicks by remember { mutableStateOf(0) }   // state that survives redraws
    Button(onClick = { clicks++ }) {
        Text(stringResource(R.string.action_start) + " ($clicks)")   // texts come from resources
    }
}
```

`Modifier` changes size, padding, click behavior and so on: `Modifier.padding(16.dp).fillMaxWidth()`. The colors and fonts come from `MaterialTheme` and `NetrikTheme` (our extras, like the monospaced `dataTypography` for IPs and MACs). Learn more at <https://developer.android.com/develop/ui/compose/tutorial>.

### How a screen gets its data (MVVM)

Each tool follows the same pattern, called **MVVM** with unidirectional data flow:

```
Screen (Compose)  ──events──▶  ViewModel  ──calls──▶  Repository / core logic
       ▲                           │
       └──────── UI state ◀────────┘   (a StateFlow the screen observes)
```

- The **Screen** only draws the state and forwards user actions (taps, typing) to the ViewModel.
- The **ViewModel** keeps the screen state, survives screen rotation and runs the work in coroutines.
- **Repositories** and `core/` classes do the real work (run ping, query the database, open sockets). They know nothing about the UI, which makes them easy to test.

Other libraries you'll see:

- **Hilt** (`@Inject`, `@HiltViewModel`, `@Module`): creates objects and hands them to whoever needs them ("dependency injection"), so classes don't build their own dependencies.
- **Room** (`@Entity`, `@Dao`): the local database (OUI vendors, SSH hosts, histories).
- **DataStore**: small settings (theme, terminal font size).
- **Navigation Compose**: moves between screens using the routes in `navigation/Routes.kt`.

## How the code is organized

```
app/src/main/java/com/netrik/
├── MainActivity.kt          the single Activity: applies theme and language, shows NetrikApp
├── navigation/              tabs, routes and the NavHost that connects every screen
│   ├── NetrikTool.kt        the catalog of tools shown in the hub
│   ├── Routes.kt            one route per screen (with its arguments)
│   └── NetrikNavHost.kt     which screen each route opens
├── feature/                 one folder per screen/tool: Screen + ViewModel (+ UI state)
│   ├── hub/                 Tools tab with the "Current network" card
│   ├── ping/  traceroute/  devices/  wifi/  portscan/  oui/  ssh/  settings/
└── core/                    shared code, no screens
    ├── designsystem/        theme (colors, fonts) and reusable components
    ├── network/  lan/  wifi/  portscan/  oui/  ssh/  terminal/   the real network logic
    ├── database/            Room database, entities and DAOs
    ├── settings/            appearance settings and app language
    └── ui/                  small UI helpers (copy to clipboard, permissions, run state)

app/src/main/res/
├── values/strings.xml           English texts (default)
├── values-pt-rBR/strings.xml    Brazilian Portuguese texts
├── drawable/                    icons (Material Symbols as vector drawables)
└── font/                        Roboto Flex and JetBrains Mono

app/src/main/assets/          bundled data: OUI database, port lists, licenses
app/src/test/                 unit tests (run on your computer, no phone needed)
app/schemas/                  Room database schema history (needed for migrations)
gradle/libs.versions.toml     every library and its version
scripts/                      helper scripts (e.g. downloading icons)
```

**Example: following Ping from the screen to the system.** `feature/ping/PingScreen.kt` draws the field, the Start button and the results. Tapping Start calls `PingViewModel.onStart()`, which validates the options (`PingOptionsValidator` in `core/network/ping/PingOptions.kt`) and runs the system `ping` through `PingRunner`. The runner turns each output line into an event with `PingOutputParser`, and the ViewModel folds those events into the state. The screen observes that state and redraws. The parser and the statistics are plain Kotlin, so they have unit tests in `app/src/test/.../core/network/ping/`.

## Recipes for common changes

### Change or add a text

Never write user-facing text directly in Kotlin. Every text has a key and lives in **both** files:

```xml
<!-- app/src/main/res/values/strings.xml (English) -->
<string name="ping_empty">Enter an IP or domain and tap Start.</string>

<!-- app/src/main/res/values-pt-rBR/strings.xml (Brazilian Portuguese) -->
<string name="ping_empty">Informe um IP ou domínio e toque em Iniciar.</string>
```

Use it in Compose with `stringResource(R.string.ping_empty)`. Values can have placeholders (`%1$s` for text, `%1$d` for numbers): `stringResource(R.string.trace_ip_copied, ip)`. For texts that depend on a count, use `<plurals>` and `pluralStringResource(...)`; Portuguese needs the `one`, `many` and `other` quantities.

> Tip: an apostrophe must be escaped in these files: `Couldn\'t`.

### Add a new language

1. Create `app/src/main/res/values-XX/strings.xml` (e.g. `values-es` for Spanish) with **every** key from `values/strings.xml` translated.
2. Add the language to `app/src/main/res/xml/locales_config.xml`.
3. Add it to the `AppLanguage` enum in `core/settings/AppLanguages.kt`, and its name (written in that language) to `languageLabel` in `feature/settings/SettingsScreen.kt`.
4. Run `./gradlew lintDebug`: it reports missing translations.

### Add an icon

The app uses [Material Symbols Rounded](https://fonts.google.com/icons). Find the icon name there and run:

```bash
python3 scripts/material_symbol.py icon_name            # outline version
python3 scripts/material_symbol.py --filled icon_name   # filled version
```

It creates `res/drawable/ic_icon_name.xml`, used as `painterResource(R.drawable.ic_icon_name)`.

### Add a new tool

1. Create `feature/yourtool/` with a `YourToolScreen.kt` (Compose) and a `YourToolViewModel.kt` (`@HiltViewModel`). Copying the structure of a small existing tool (e.g. `feature/oui/` or `feature/traceroute/`) is the easiest start.
2. Put the network/logic code in `core/` and cover the pure parts with unit tests.
3. Add a route in `navigation/Routes.kt` and register it in `navigation/NetrikNavHost.kt` (`composable<YourToolRoute> { ... }` and the `NavController.openTool` function).
4. Add an entry to the `NetrikTool` enum (title, description, icon, group) so it shows up in the hub.
5. Add the texts in English and Portuguese, and the icon with the script above.

### Add a library

1. Add the version and the library to `gradle/libs.versions.toml`.
2. Use it in `app/build.gradle.kts` as `implementation(libs.your.library)`.
3. Check its license is compatible with GPL-3.0 (Apache 2.0, MIT, BSD, LGPL are fine) and that it's maintained. Explain why it's needed in the pull request.

### Change the database

Room keeps the schema history in `app/schemas/`. To add a table or column: change the `@Entity` classes, increase `version` in `core/database/NetrikDatabase.kt` and add an `AutoMigration(from = old, to = new)`. Building generates the new schema JSON; commit it too. Never edit an old schema file.

## Project rules

- **English everywhere in the repository**: code, names, comments, tests, docs and commit messages. The app itself is translated through the string files.
- **No hardcoded user-facing texts or locales.** Format numbers and dates with `Locale.getDefault()`, which follows the app language.
- **Never make up data.** If Android doesn't allow getting something (a MAC address, a network name without permission), say so in the UI instead of showing a fake value.
- **Never log secrets**: no passwords, private keys or SSH session content in logs, crash reports or issues.
- **Respect the design.** Colors, spacing and components follow the existing design system (`core/designsystem/`). Reuse the components already there before creating new ones, and attach screenshots of UI changes to your pull request.
- **Comments explain why**, not what. Write them in plain English, as you would explain the code to a colleague.
- **Keep the style of the surrounding code.** Android Studio's **Code → Reformat Code** (Ctrl+Alt+L / ⌘⌥L) applies the standard Kotlin style (4 spaces, trailing commas).

## Testing your change

### Unit tests

Unit tests live in `app/src/test/` and run on your computer in seconds. They cover the pure logic (parsers, calculations, validation). If you change logic, add or update a test:

```kotlin
class PortListTest {
    @Test
    fun `ranges are expanded`() {
        val list = PortList.parse("80,8000-8002") as PortList.Valid
        assertEquals(listOf(80, 8000, 8001, 8002), list.ports)
    }
}
```

Run them all with `./gradlew test`, or only some of them:

```bash
./gradlew testDebugUnitTest --tests "com.netrik.core.portscan.*"
```

In Android Studio you can also click the green ▶ next to a test.

### Checks before opening a pull request

```bash
./gradlew assembleDebug test lintDebug
```

All three must pass (lint may show warnings, but no errors).

### Manual testing

Try your change in the app, ideally covering:

- light **and** dark theme (Settings → Theme), and pure black;
- English **and** Portuguese (Settings → App language);
- a small screen and the phone in landscape;
- permissions denied (what does the screen say?);
- no network (airplane mode).

## Commit messages

A **commit** is a saved step of your work. Make small commits with clear messages following [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>: <short summary in English, at most 72 characters>

- What changed and why, one item per line.
```

Common types: `feat` (new feature), `fix` (bug fix), `refactor` (code change without behavior change), `docs`, `test`, `build` (Gradle/dependencies), `chore` (other maintenance).

```bash
git add path/to/changed/files
git commit
```

Example:

```
fix: keep the ping chart scale when the first reply times out

- The Y axis used the first sample as its maximum; a timeout made it 0
  and every later reply was drawn off the chart.
```

## Opening a pull request

1. Send your branch to your fork: `git push -u origin fix-ping-crash`.
2. GitHub shows a **Compare & pull request** button on your fork's page; click it.
3. Describe **what** you changed and **why**, how you tested it, and attach screenshots for UI changes. Link the issue it fixes ("Fixes #12").
4. Wait for the review. It's normal to be asked for changes: commit them to the same branch and push again; the pull request updates by itself.

### Pull request checklist

- [ ] `./gradlew assembleDebug test lintDebug` passes.
- [ ] New logic has unit tests.
- [ ] Every new text is in `values/strings.xml` **and** `values-pt-rBR/strings.xml`.
- [ ] Code, comments and commit messages are in English.
- [ ] Tested on a device or emulator in light/dark theme and both languages.
- [ ] Screenshots attached for UI changes.

## Glossary

- **APK**: the installable file of an Android app.
- **Android SDK / API level**: the Android libraries and tools. Each Android version has an API level (Android 8.0 = 26, Android 17 = 37). `minSdk` is the oldest supported version.
- **AVD / emulator**: a virtual Android phone running on your computer.
- **Gradle**: the build tool that downloads libraries, compiles and packages the app (`./gradlew ...`).
- **Composable**: a function marked `@Composable` that describes a piece of the screen.
- **State / StateFlow**: data that the screen observes; when it changes, the screen redraws.
- **ViewModel**: keeps the state and logic of a screen and survives screen rotation.
- **Coroutine**: lightweight background work that doesn't block the screen.
- **Hilt**: provides each class with the objects it needs (dependency injection).
- **Room / DAO / Entity**: the database library; an Entity is a table, a DAO holds the queries.
- **Resource (`R.string.x`, `R.drawable.y`)**: texts, icons and other files referenced by an id.
- **Fork / branch / pull request**: your copy of the repository / a line of work / a request to merge your changes.

## License

By contributing, you agree that your contributions are licensed under the project's [GNU General Public License v3.0](LICENSE).

Thanks for helping make Netrik better! 💙
