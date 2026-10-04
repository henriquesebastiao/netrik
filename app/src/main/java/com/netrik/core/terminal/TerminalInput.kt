package com.netrik.core.terminal

import android.view.KeyEvent
import com.termux.terminal.KeyHandler

/** Teclas da barra extra do terminal, na ordem do design (2 linhas de 6). */
enum class ExtraKey(val label: String) {
    Esc("Esc"), Tab("Tab"), Pipe("|"), Slash("/"), Up("↑"), Minus("-"),
    Ctrl("Ctrl"), Alt("Alt"), Tilde("~"), Left("←"), Down("↓"), Right("→"),
    ;

    val isModifier: Boolean get() = this == Ctrl || this == Alt
    val isArrow: Boolean get() = this == Up || this == Down || this == Left || this == Right
}

/** Modificadores "presos" pela barra extra: valem para a próxima tecla e se soltam. */
data class StickyModifiers(val ctrl: Boolean = false, val alt: Boolean = false) {
    val any: Boolean get() = ctrl || alt

    fun toggle(key: ExtraKey): StickyModifiers = when (key) {
        ExtraKey.Ctrl -> copy(ctrl = !ctrl, alt = false)
        ExtraKey.Alt -> copy(alt = !alt, ctrl = false)
        else -> this
    }
}

/** Converte o que o usuário digita em bytes para o servidor (como um xterm). */
object TerminalInput {

    private const val ESC = 0x1b

    /** Texto do teclado; Enter vira CR. Com Ctrl, um caractere de controle; com Alt, ESC na frente. */
    fun encodeText(text: String, modifiers: StickyModifiers = StickyModifiers()): ByteArray {
        val normalized = text.replace("\r\n", "\r").replace('\n', '\r')
        if (!modifiers.any || normalized.isEmpty()) return normalized.toByteArray(Charsets.UTF_8)
        // Os modificadores valem só para o primeiro caractere.
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

    /** Ctrl+tecla: a-z → 1..26, e os clássicos @ [ \ ] ^ _ espaço e ?. */
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

    /** Bytes de uma tecla da barra extra (modificadores não geram bytes). */
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

    /** Tecla especial (setas, Tab, Home...) pela tabela do Termux, que respeita o modo de cursor da aplicação. */
    fun special(keyCode: Int, modifiers: StickyModifiers, cursorKeysApplicationMode: Boolean, keypadApplicationMode: Boolean = false): ByteArray? {
        var mods = 0
        if (modifiers.ctrl) mods = mods or KeyHandler.KEYMOD_CTRL
        if (modifiers.alt) mods = mods or KeyHandler.KEYMOD_ALT
        return KeyHandler.getCode(keyCode, mods, cursorKeysApplicationMode, keypadApplicationMode)?.toByteArray(Charsets.UTF_8)
    }
}

/** Tamanho da fonte do terminal (design: 10 a 20, padrão 13). */
object TerminalFont {
    const val MIN = 10
    const val MAX = 20
    const val DEFAULT = 13

    fun clamp(size: Int): Int = size.coerceIn(MIN, MAX)
}

/** Aba que fica ativa depois de fechar a de índice [closed] (mesma regra do protótipo). */
fun activeAfterClose(active: Int, closed: Int, remaining: Int): Int {
    if (remaining <= 0) return 0
    val shifted = if (active > closed) active - 1 else active
    return shifted.coerceIn(0, remaining - 1)
}
