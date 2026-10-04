package com.netrik.core.terminal

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Typeface
import android.text.Editable
import android.text.InputType
import android.util.TypedValue
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import androidx.core.content.res.ResourcesCompat
import com.netrik.R
import com.termux.terminal.TerminalColors
import com.termux.view.TerminalRenderer
import java.util.Properties
import kotlin.math.abs
import kotlin.math.max

/**
 * View do terminal: desenha o [com.termux.terminal.TerminalEmulator] da sessão com o
 * [TerminalRenderer] do Termux e traduz teclado (IME e teclas físicas), rolagem e toque.
 * Os modificadores presos da barra extra entram por [stickyModifiers] e são soltos após uma tecla.
 */
@SuppressLint("ViewConstructor")
class TerminalCanvasView(
    context: Context,
    private val stickyModifiers: () -> StickyModifiers,
    private val onModifiersConsumed: () -> Unit,
) : View(context) {

    private val typeface: Typeface = ResourcesCompat.getFont(context, R.font.jetbrains_mono) ?: Typeface.MONOSPACE
    private var renderer = TerminalRenderer(spToPx(TerminalFont.DEFAULT), typeface)
    private var fontSize = TerminalFont.DEFAULT

    /** Linha do topo: 0 = tela atual; negativo = rolando o histórico. */
    private var topRow = 0
    private var scrollRemainder = 0f

    var terminal: SshTerminal? = null
        set(value) {
            if (field === value) return
            field = value
            topRow = 0
            updateSize()
            invalidate()
        }

    init {
        TerminalTheme.install()
        isFocusable = true
        isFocusableInTouchMode = true
        // Sem o véu cinza que o Android desenha em Views focadas ao usar teclado físico/eventos de tecla.
        defaultFocusHighlightEnabled = false
        setBackgroundColor(TerminalTheme.BACKGROUND)
    }

    fun setFontSize(size: Int) {
        if (size == fontSize) return
        fontSize = size
        renderer = TerminalRenderer(spToPx(size), typeface)
        updateSize()
        invalidate()
    }

    /** Chamado quando chega saída nova: mantém a posição se o usuário estiver lendo o histórico. */
    fun onOutput() {
        val emulator = terminal?.emulator ?: return
        val scrolled = emulator.scrollCounter
        if (topRow < 0 && scrolled > 0) topRow = max(-emulator.screen.activeTranscriptRows, topRow - scrolled)
        emulator.clearScrollCounter()
        invalidate()
    }

    fun showKeyboard() {
        requestFocus()
        context.getSystemService(InputMethodManager::class.java)?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = updateSize()

    private fun updateSize() {
        val terminal = terminal ?: return
        val width = width - paddingLeft - paddingRight
        val height = height - paddingTop - paddingBottom
        if (width <= 0 || height <= 0) return
        val cellWidth = renderer.fontWidth
        val cellHeight = renderer.fontLineSpacing
        val columns = max(MIN_SIZE, (width / cellWidth).toInt())
        // Linhas: só as que cabem de verdade, senão a linha do cursor fica escondida (paisagem + teclado).
        val rows = max(1, height / cellHeight)
        terminal.resize(columns, rows, cellWidth.toInt(), cellHeight)
    }

    override fun onDraw(canvas: Canvas) {
        val emulator = terminal?.emulator ?: return
        canvas.save()
        canvas.translate(paddingLeft.toFloat(), paddingTop.toFloat())
        renderer.render(emulator, canvas, topRow, -1, -1, -1, -1)
        canvas.restore()
    }

    // Toque: tocar abre o teclado; arrastar rola o histórico (ou manda setas em programas de tela cheia).

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            showKeyboard()
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            val emulator = terminal?.emulator ?: return false
            scrollRemainder += distanceY
            val lines = (scrollRemainder / renderer.fontLineSpacing).toInt()
            if (lines == 0) return true
            scrollRemainder -= lines * renderer.fontLineSpacing
            if (emulator.isAlternateBufferActive) {
                // vim, less, htop...: o histórico é do programa, então a rolagem vira setas.
                val key = if (lines > 0) KeyEvent.KEYCODE_DPAD_DOWN else KeyEvent.KEYCODE_DPAD_UP
                repeat(abs(lines)) {
                    TerminalInput.special(key, StickyModifiers(), emulator.isCursorKeysApplicationMode)?.let { terminal?.send(it) }
                }
            } else {
                topRow = (topRow + lines).coerceIn(-emulator.screen.activeTranscriptRows, 0)
                invalidate()
            }
            return true
        }
    })

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean = gestures.onTouchEvent(event) || super.onTouchEvent(event)

    // Teclado

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        // Sem sugestões nem autocorreção: cada tecla vai direto para o servidor.
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_ACTION_NONE
        return object : BaseInputConnection(this, true) {
            override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
                super.commitText(text, newCursorPosition)
                flush(editable)
                return true
            }

            override fun finishComposingText(): Boolean {
                super.finishComposingText()
                flush(editable)
                return true
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                if (editable.isNullOrEmpty()) {
                    // Nada em composição: o backspace vai para o servidor.
                    repeat(beforeLength.coerceAtLeast(1)) { sendBytes(byteArrayOf(DEL)) }
                    return true
                }
                return super.deleteSurroundingText(beforeLength, afterLength)
            }

            private fun flush(content: Editable?) {
                if (content.isNullOrEmpty()) return
                val text = content.toString()
                content.clear()
                sendText(text)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) return super.onKeyDown(keyCode, event)
        val emulator = terminal?.emulator ?: return super.onKeyDown(keyCode, event)
        val sticky = stickyModifiers()
        val modifiers = StickyModifiers(ctrl = sticky.ctrl || event.isCtrlPressed, alt = sticky.alt || event.isAltPressed)
        val special = TerminalInput.special(keyCode, modifiers, emulator.isCursorKeysApplicationMode, emulator.isKeypadApplicationMode)
        if (special != null) {
            consumeSticky(sticky)
            sendBytes(special)
            return true
        }
        val plain = event.getUnicodeChar(event.metaState and (KeyEvent.META_CTRL_MASK or KeyEvent.META_ALT_MASK).inv())
        if (plain <= 0) return super.onKeyDown(keyCode, event)
        consumeSticky(sticky)
        sendBytes(TerminalInput.encodeText(String(Character.toChars(plain)), modifiers))
        return true
    }

    private fun sendText(text: String) {
        val sticky = stickyModifiers()
        consumeSticky(sticky)
        sendBytes(TerminalInput.encodeText(text, sticky))
    }

    private fun consumeSticky(sticky: StickyModifiers) {
        if (sticky.any) onModifiersConsumed()
    }

    private fun sendBytes(bytes: ByteArray) {
        val terminal = terminal ?: return
        if (topRow != 0) {
            topRow = 0
            invalidate()
        }
        terminal.send(bytes)
    }

    private fun spToPx(sp: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp.toFloat(), resources.displayMetrics).toInt()

    private companion object {
        const val MIN_SIZE = 4
        const val DEL: Byte = 0x7f
    }
}

/** Paleta do terminal do design: sempre escura, nos temas claro e escuro do app. */
object TerminalTheme {
    const val BACKGROUND = 0xFF0A1112.toInt()
    @Volatile private var installed = false

    /** Ajusta o esquema global do Termux antes de criar emuladores (vale também após um reset do terminal). */
    fun install() {
        if (installed) return
        synchronized(this) {
            if (installed) return
            TerminalColors.COLOR_SCHEME.updateWith(
                Properties().apply {
                    put("background", "#0A1112")
                    put("foreground", "#D7E0E2")
                    put("cursor", "#72D0D0")
                    put("color1", "#F98F87")
                    put("color2", "#7CD591")
                    put("color3", "#EBC573")
                    put("color4", "#79B8ED")
                    put("color6", "#72D0D0")
                    put("color8", "#8E9B9D")
                },
            )
            installed = true
        }
    }
}
