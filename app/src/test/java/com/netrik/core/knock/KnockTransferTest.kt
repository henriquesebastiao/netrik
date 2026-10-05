package com.netrik.core.knock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KnockTransferTest {

    private val groups = listOf(KnockGroup(1, "Servers", true), KnockGroup(2, "Empty", false))
    private val vps = KnockProfile(
        id = 10, name = "VPS", host = "vps.example.com", groupId = 1, delayMs = 300, verifyPort = 22,
        steps = listOf(KnockStep(KnockProtocol.Tcp, port = 7000), KnockStep(KnockProtocol.Udp, port = 8000), KnockStep(KnockProtocol.Icmp, payloadSize = 64)),
    )
    private val lab = KnockProfile(11, "Lab", "10.0.0.5", null, 500, null, listOf(KnockStep(KnockProtocol.Icmp)))

    private fun parseOk(text: String) = (KnockTransfer.parse(text) as KnockTransfer.Parsed.Ok).file

    @Test
    fun `export and import round trip`() {
        val file = parseOk(KnockTransfer.encode(groups, listOf(vps, lab)))
        assertEquals(listOf("Servers", "Empty"), file.groups)
        assertEquals(0, file.invalid)
        assertEquals(listOf("Servers", null), file.knocks.map { it.groupName })
        // Ids and group ids aren't exported: the group goes by name.
        assertEquals(listOf(vps.copy(id = 0, groupId = null), lab.copy(id = 0)), file.knocks.map { it.profile })
    }

    @Test
    fun `other files are refused`() {
        assertEquals(KnockTransfer.Parsed.NotKnockFile, KnockTransfer.parse("not json"))
        assertEquals(KnockTransfer.Parsed.NotKnockFile, KnockTransfer.parse("""{"hosts": []}"""))
        assertEquals(KnockTransfer.Parsed.NotKnockFile, KnockTransfer.parse("""{"format": "netrik-knock", "version": 0}"""))
        assertEquals(KnockTransfer.Parsed.NewerVersion(2), KnockTransfer.parse("""{"format": "netrik-knock", "version": 2}"""))
    }

    @Test
    fun `invalid entries are counted and left out, unknown fields ignored`() {
        val file = parseOk(
            """
            {"format": "netrik-knock", "version": 1, "extra": true, "knocks": [
              {"name": "", "host": "10.0.0.1", "steps": [{"protocol": "TCP", "port": 1, "note": "x"}]},
              {"name": "bad port", "host": "10.0.0.1", "steps": [{"protocol": "udp", "port": 70000}]},
              {"name": "bad protocol", "host": "10.0.0.1", "steps": [{"protocol": "sctp", "port": 1}]},
              {"name": "no steps", "host": "10.0.0.1", "steps": []},
              {"name": "bad host", "host": "a b", "steps": [{"protocol": "icmp"}]}
            ]}
            """.trimIndent(),
        )
        assertEquals(4, file.invalid)
        val only = file.knocks.single().profile
        // Missing name becomes the host; missing delay gets the default.
        assertEquals("10.0.0.1", only.name)
        assertEquals(KnockForm.DEFAULT_DELAY_MS, only.delayMs)
        assertEquals(listOf(KnockStep(KnockProtocol.Tcp, port = 1)), only.steps)
    }

    @Test
    fun `import merges groups by name and skips duplicates`() {
        val file = parseOk(
            KnockTransfer.encode(
                listOf(KnockGroup(1, "servers", true), KnockGroup(2, "New", true)),
                listOf(
                    vps.copy(groupId = 1),
                    lab.copy(groupId = 2),
                    lab.copy(name = "Lab 2", groupId = 3),
                    lab.copy(id = 99, groupId = 2),
                ),
            ),
        )
        val plan = KnockTransfer.plan(file, existingGroups = groups, existing = listOf(vps))
        // "servers" is the existing "Servers"; "New" is created. Group 3 isn't in the file: no group.
        assertEquals(listOf("New"), plan.newGroups)
        assertEquals(listOf("Lab", "Lab 2"), plan.knocks.map { it.profile.name })
        assertEquals(listOf("New", null), plan.knocks.map { it.groupName })
        // VPS is already saved; the second "Lab" repeats the first one in the file.
        assertEquals(2, plan.duplicates)
        assertEquals(0, plan.invalid)
    }

    @Test
    fun `a group named only by a knock is created too`() {
        val file = parseOk(
            """{"format": "netrik-knock", "version": 1, "knocks": [{"name": "A", "host": "h", "group": " Lab ", "steps": [{"protocol": "icmp", "size": 10}]}]}""",
        )
        val plan = KnockTransfer.plan(file, emptyList(), emptyList())
        assertEquals(listOf("Lab"), plan.newGroups)
        assertEquals("Lab", plan.knocks.single().groupName)
        assertTrue(plan.knocks.single().profile.steps.single().payloadSize == 10)
    }
}
