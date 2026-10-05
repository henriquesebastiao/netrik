package com.netrik.feature.knock

import com.netrik.core.knock.KnockGroup
import com.netrik.core.knock.KnockProfile
import com.netrik.core.knock.KnockProtocol
import com.netrik.core.knock.KnockStep
import org.junit.Assert.assertEquals
import org.junit.Test

class KnockListTest {

    private fun knock(id: Long, groupId: Long?) =
        KnockProfile(id, "K$id", "h", groupId, 500, null, listOf(KnockStep(KnockProtocol.Tcp, port = 1)))

    @Test
    fun `groups keep their order, empty ones included, and No group comes last`() {
        val groups = listOf(KnockGroup(1, "A", true), KnockGroup(2, "B", false))
        val list = KnockList.build(groups, listOf(knock(10, 1), knock(11, null), knock(12, 99)), noGroupExpanded = false)
        assertEquals(listOf(1L, 2L, null), list.map { it.id })
        assertEquals(listOf(listOf(10L), emptyList(), listOf(11L, 12L)), list.map { g -> g.knocks.map { it.id } })
        assertEquals(false, list.last().expanded)
    }

    @Test
    fun `No group is hidden when it has no knocks`() {
        val list = KnockList.build(listOf(KnockGroup(1, "A", true)), listOf(knock(10, 1)), noGroupExpanded = true)
        assertEquals(listOf(1L), list.map { it.id })
    }
}
