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
    fun `plain text goes as UTF-8 and Enter becomes CR`() {
        assertArrayEquals("ls -la\r".toByteArray(), TerminalInput.encodeText("ls -la\n"))
        assertArrayEquals("ç".toByteArray(Charsets.UTF_8), TerminalInput.encodeText("ç"))
    }

    @Test
    fun `Ctrl produces control characters`() {
        assertArrayEquals(bytes(3), TerminalInput.encodeText("c", ctrl))
        assertArrayEquals(bytes(3), TerminalInput.encodeText("C", ctrl))
        assertArrayEquals(bytes(4), TerminalInput.encodeText("d", ctrl))
        assertArrayEquals(bytes(12), TerminalInput.encodeText("l", ctrl))
        assertArrayEquals(bytes(0), TerminalInput.encodeText(" ", ctrl))
        assertArrayEquals(bytes(27), TerminalInput.encodeText("[", ctrl))
        assertArrayEquals(bytes(127), TerminalInput.encodeText("?", ctrl))
        // Only the first character takes the modifier.
        assertArrayEquals(bytes(3) + "x".toByteArray(), TerminalInput.encodeText("cx", ctrl))
    }

    @Test
    fun `Alt sends ESC in front`() {
        assertArrayEquals(bytes(0x1b) + "b".toByteArray(), TerminalInput.encodeText("b", alt))
        assertArrayEquals(bytes(0x1b, 0x1b), TerminalInput.encodeExtraKey(ExtraKey.Esc, alt, false))
    }

    @Test
    fun `extra bar keys`() {
        assertArrayEquals(bytes(0x1b), TerminalInput.encodeExtraKey(ExtraKey.Esc, none, false))
        assertArrayEquals("\t".toByteArray(), TerminalInput.encodeExtraKey(ExtraKey.Tab, none, false))
        assertArrayEquals("|".toByteArray(), TerminalInput.encodeExtraKey(ExtraKey.Pipe, none, false))
        assertArrayEquals("~".toByteArray(), TerminalInput.encodeExtraKey(ExtraKey.Tilde, none, false))
        assertNull(TerminalInput.encodeExtraKey(ExtraKey.Ctrl, none, false))
        assertNull(TerminalInput.encodeExtraKey(ExtraKey.Alt, none, false))
    }

    @Test
    fun `arrows honor the application cursor mode`() {
        assertArrayEquals("\u001b[A".toByteArray(), TerminalInput.encodeExtraKey(ExtraKey.Up, none, cursorKeysApplicationMode = false))
        assertArrayEquals("\u001bOA".toByteArray(), TerminalInput.encodeExtraKey(ExtraKey.Up, none, cursorKeysApplicationMode = true))
        assertArrayEquals("\u001b[D".toByteArray(), TerminalInput.encodeExtraKey(ExtraKey.Left, none, false))
        // Ctrl+→ (jump word) in xterm format.
        assertArrayEquals("\u001b[1;5C".toByteArray(), TerminalInput.encodeExtraKey(ExtraKey.Right, ctrl, false))
    }

    @Test
    fun `sticky Ctrl and Alt toggle each other`() {
        val ctrlOn = StickyModifiers().toggle(ExtraKey.Ctrl)
        assertTrue(ctrlOn.ctrl)
        val altOn = ctrlOn.toggle(ExtraKey.Alt)
        assertTrue(altOn.alt)
        assertFalse(altOn.ctrl)
        assertFalse(altOn.toggle(ExtraKey.Alt).any)
        assertEquals(altOn, altOn.toggle(ExtraKey.Tab))
    }

    @Test
    fun `active tab after closing one`() {
        // Closing the active one (the last): moves to the previous one.
        assertEquals(1, activeAfterClose(active = 2, closed = 2, remaining = 2))
        // Closing one before the active: the active index moves back.
        assertEquals(1, activeAfterClose(active = 2, closed = 0, remaining = 2))
        // Closing one after the active: nothing changes.
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
