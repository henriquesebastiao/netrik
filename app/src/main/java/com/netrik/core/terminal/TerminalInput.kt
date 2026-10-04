package com.netrik.core.terminal

import android.view.KeyEvent
import com.termux.terminal.KeyHandler

/** Keys of the terminal extra bar, in design order (2 rows of 6). */
enum class ExtraKey(val label: String) {
    Esc("Esc"), Tab("Tab"), Pipe("|"), Slash("/"), Up("↑"), Minus("-"),
    Ctrl("Ctrl"), Alt("Alt"), Tilde("~"), Left("←"), Down("↓"), Right("→"),
    ;

    val isModifier: Boolean get() = this == Ctrl || this == Alt
    val isArrow: Boolean get() = this == Up || this == Down || this == Left || this == Right
}

/** Modifiers "stuck" by the extra bar: they apply to the next key and then release. */
data class StickyModifiers(val ctrl: Boolean = false, val alt: Boolean = false) {
    val any: Boolean get() = ctrl || alt

    fun toggle(key: ExtraKey): StickyModifiers = when (key) {
        ExtraKey.Ctrl -> copy(ctrl = !ctrl, alt = false)
        ExtraKey.Alt -> copy(alt = !alt, ctrl = false)
        else -> this
    }
}

/** Turns what the user types into bytes for the server (like an xterm). */
object TerminalInput {

    private const val ESC = 0x1b

    /** Keyboard text; Enter becomes CR. With Ctrl, a control character; with Alt, ESC in front. */
    fun encodeText(text: String, modifiers: StickyModifiers = StickyModifiers()): ByteArray {
        val normalized = text.replace("\r\n", "\r").replace('\n', '\r')
        if (!modifiers.any || normalized.isEmpty()) return normalized.toByteArray(Charsets.UTF_8)
        // Modifiers only apply to the first character.
        val first = normalized.codePointAt(0)
        val rest = normalized.substring(Character.charCount(first))
        val head = if (modifiers.ctrl) {
            controlCode(first)?.let { byteArrayOf(it.toByte()) } ?: String(Character.toChars(first)).toByteArray(Charsets.UTF_8)
        } else {
            String(Character.toChars(first)).toByteArray(Charsets.UTF_8)
        }
        val prefix = if (modifiers.alt) byteArrayOf(ESC.toByte()) else ByteArray(0)
        return prefix + head + rest.toByteArray(Charsets.UTF_8)
    }

    /** Ctrl+key: a-z → 1..26, plus the classic @ [ \ ] ^ _ space and ?. */
    fun controlCode(codePoint: Int): Int? = when (codePoint) {
        in 'a'.code..'z'.code -> codePoint - 'a'.code + 1
        in 'A'.code..'Z'.code -> codePoint - 'A'.code + 1
        '@'.code, ' '.code, '2'.code -> 0
        '['.code, '3'.code -> 27
        '\\'.code, '4'.code -> 28
        ']'.code, '5'.code -> 29
        '^'.code, '6'.code -> 30
        '_'.code, '-'.code, '7'.code -> 31
        '?'.code, '8'.code -> 127
        else -> null
    }

    /** Bytes of an extra bar key (modifiers produce no bytes). */
    fun encodeExtraKey(key: ExtraKey, modifiers: StickyModifiers, cursorKeysApplicationMode: Boolean): ByteArray? = when (key) {
        ExtraKey.Ctrl, ExtraKey.Alt -> null
        ExtraKey.Esc -> if (modifiers.alt) byteArrayOf(ESC.toByte(), ESC.toByte()) else byteArrayOf(ESC.toByte())
        ExtraKey.Tab -> special(KeyEvent.KEYCODE_TAB, modifiers, cursorKeysApplicationMode)
        ExtraKey.Up -> special(KeyEvent.KEYCODE_DPAD_UP, modifiers, cursorKeysApplicationMode)
        ExtraKey.Down -> special(KeyEvent.KEYCODE_DPAD_DOWN, modifiers, cursorKeysApplicationMode)
        ExtraKey.Left -> special(KeyEvent.KEYCODE_DPAD_LEFT, modifiers, cursorKeysApplicationMode)
        ExtraKey.Right -> special(KeyEvent.KEYCODE_DPAD_RIGHT, modifiers, cursorKeysApplicationMode)
        ExtraKey.Pipe -> encodeText("|", modifiers)
        ExtraKey.Slash -> encodeText("/", modifiers)
        ExtraKey.Minus -> encodeText("-", modifiers)
        ExtraKey.Tilde -> encodeText("~", modifiers)
    }

    /** Special key (arrows, Tab, Home...) via the Termux table, which honors the application cursor mode. */
    fun special(keyCode: Int, modifiers: StickyModifiers, cursorKeysApplicationMode: Boolean, keypadApplicationMode: Boolean = false): ByteArray? {
        var mods = 0
        if (modifiers.ctrl) mods = mods or KeyHandler.KEYMOD_CTRL
        if (modifiers.alt) mods = mods or KeyHandler.KEYMOD_ALT
        return KeyHandler.getCode(keyCode, mods, cursorKeysApplicationMode, keypadApplicationMode)?.toByteArray(Charsets.UTF_8)
    }
}

/** Terminal font size (design: 10 to 20, default 13). */
object TerminalFont {
    const val MIN = 10
    const val MAX = 20
    const val DEFAULT = 13

    fun clamp(size: Int): Int = size.coerceIn(MIN, MAX)
}

/** Tab that becomes active after closing the one at index [closed] (same rule as the prototype). */
fun activeAfterClose(active: Int, closed: Int, remaining: Int): Int {
    if (remaining <= 0) return 0
    val shifted = if (active > closed) active - 1 else active
    return shifted.coerceIn(0, remaining - 1)
}
