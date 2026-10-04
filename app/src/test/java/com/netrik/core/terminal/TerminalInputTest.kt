package com.netrik.core.terminal

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalInputTest {

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
    private val none = StickyModifiers()
    private val ctrl = StickyModifiers(ctrl = true)
    private val alt = StickyModifiers(alt = true)

    @Test
    fun `texto simples vai em UTF-8 e Enter vira CR`() {
        assertArrayEquals("ls -la\r".toByteArray(), TerminalInput.encodeText("ls -la\n"))
        assertArrayEquals("ç".toByteArray(Charsets.UTF_8), TerminalInput.encodeText("ç"))
    }

    @Test
    fun `Ctrl gera caracteres de controle`() {
        assertArrayEquals(bytes(3), TerminalInput.encodeText("c", ctrl))
        assertArrayEquals(bytes(3), TerminalInput.encodeText("C", ctrl))
        assertArrayEquals(bytes(4), TerminalInput.encodeText("d", ctrl))
        assertArrayEquals(bytes(12), TerminalInput.encodeText("l", ctrl))
        assertArrayEquals(bytes(0), TerminalInput.encodeText(" ", ctrl))
        assertArrayEquals(bytes(27), TerminalInput.encodeText("[", ctrl))
        assertArrayEquals(bytes(127), TerminalInput.encodeText("?", ctrl))
        // Só o primeiro caractere leva o modificador.
        assertArrayEquals(bytes(3) + "x".toByteArray(), TerminalInput.encodeText("cx", ctrl))
    }

    @Test
    fun `Alt manda ESC na frente`() {
        assertArrayEquals(bytes(0x1b) + "b".toByteArray(), TerminalInput.encodeText("b", alt))
        assertArrayEquals(bytes(0x1b, 0x1b), TerminalInput.encodeExtraKey(ExtraKey.Esc, alt, false))
    }

    @Test
    fun `teclas da barra extra`() {
        assertArrayEquals(bytes(0x1b), TerminalInput.encodeExtraKey(ExtraKey.Esc, none, false))
        assertArrayEquals("\t".toByteArray(), TerminalInput.encodeExtraKey(ExtraKey.Tab, none, false))
        assertArrayEquals("|".toByteArray(), TerminalInput.encodeExtraKey(ExtraKey.Pipe, none, false))
        assertArrayEquals("~".toByteArray(), TerminalInput.encodeExtraKey(ExtraKey.Tilde, none, false))
        assertNull(TerminalInput.encodeExtraKey(ExtraKey.Ctrl, none, false))
        assertNull(TerminalInput.encodeExtraKey(ExtraKey.Alt, none, false))
    }

    @Test
    fun `setas respeitam o modo de cursor da aplicação`() {
        assertArrayEquals("\u001b[A".toByteArray(), TerminalInput.encodeExtraKey(ExtraKey.Up, none, cursorKeysApplicationMode = false))
        assertArrayEquals("\u001bOA".toByteArray(), TerminalInput.encodeExtraKey(ExtraKey.Up, none, cursorKeysApplicationMode = true))
        assertArrayEquals("\u001b[D".toByteArray(), TerminalInput.encodeExtraKey(ExtraKey.Left, none, false))
        // Ctrl+→ (pular palavra) no formato xterm.
        assertArrayEquals("\u001b[1;5C".toByteArray(), TerminalInput.encodeExtraKey(ExtraKey.Right, ctrl, false))
    }

    @Test
    fun `Ctrl e Alt presos se alternam`() {
        val ctrlOn = StickyModifiers().toggle(ExtraKey.Ctrl)
        assertTrue(ctrlOn.ctrl)
        val altOn = ctrlOn.toggle(ExtraKey.Alt)
        assertTrue(altOn.alt)
        assertFalse(altOn.ctrl)
        assertFalse(altOn.toggle(ExtraKey.Alt).any)
        assertEquals(altOn, altOn.toggle(ExtraKey.Tab))
    }

    @Test
    fun `aba ativa depois de fechar uma`() {
        // Fechar a ativa (a última): passa para a anterior.
        assertEquals(1, activeAfterClose(active = 2, closed = 2, remaining = 2))
        // Fechar uma antes da ativa: o índice da ativa recua.
        assertEquals(1, activeAfterClose(active = 2, closed = 0, remaining = 2))
        // Fechar uma depois da ativa: nada muda.
        assertEquals(0, activeAfterClose(active = 0, closed = 1, remaining = 2))
        assertEquals(0, activeAfterClose(active = 0, closed = 0, remaining = 0))
    }

    @Test
    fun `fonte entre 10 e 20`() {
        assertEquals(10, TerminalFont.clamp(4))
        assertEquals(20, TerminalFont.clamp(40))
        assertEquals(13, TerminalFont.clamp(TerminalFont.DEFAULT))
    }
}
