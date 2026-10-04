package com.netrik.core.oui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.GZIPInputStream

class OuiCsvParserTest {

    private val header = "Registry,Assignment,Organization Name,Organization Address\n"

    @Test
    fun `campos com aspas, vírgulas, aspas escapadas e quebras de linha`() {
        val csv = header +
            "MA-L,286FB9,\"Nokia Shanghai Bell Co., Ltd.\",\"No.388 Ning Qiao Road,Jin Qiao   Shanghai CN 201206 \"\r\n" +
            "MA-L,38E2CA,Katun Corporation,7760 France Ave S Bloomington MN US 55438\n" +
            "MA-L,AABBCC,\"Empresa \"\"Teste\"\"\",\"Rua 1\nSão Paulo BR\"\n"
        val records = OuiCsvParser.parse(csv.reader(), OuiRegistry.MaL)

        assertEquals(3, records.size)
        assertEquals("Nokia Shanghai Bell Co., Ltd.", records[0].organization)
        assertEquals("No.388 Ning Qiao Road,Jin Qiao Shanghai CN 201206", records[0].address)
        assertEquals("Empresa \"Teste\"", records[2].organization)
        assertEquals("Rua 1 São Paulo BR", records[2].address)
    }

    @Test
    fun `registros privados ficam sem endereço`() {
        val records = OuiCsvParser.parse((header + "MA-M,741AE09,Private,\n").reader(), OuiRegistry.MaM)
        assertEquals(1, records.size)
        assertTrue(records[0].isPrivate)
        assertNull(records[0].address)
        assertEquals("741AE09", records[0].prefix)
    }

    @Test
    fun `linhas com prefixo de tamanho errado ou de outro registro são ignoradas`() {
        val csv = header +
            "MA-L,12345,Curto,X\n" +
            "MA-L,12345G,Não hexa,X\n" +
            "MA-M,AABBCCD,Outro registro,X\n" +
            "MA-L,aabbcc,Minúsculas,X\n"
        val records = OuiCsvParser.parse(csv.reader(), OuiRegistry.MaL)
        assertEquals(listOf("AABBCC"), records.map { it.prefix })
    }

    @Test(expected = OuiFormatException::class)
    fun `cabeçalho inesperado é rejeitado`() {
        OuiCsvParser.parse("<html>Request Rejected</html>".reader(), OuiRegistry.MaL)
    }

    @Test
    fun `base embarcada no APK é válida e completa`() {
        val dir = File("src/main/assets/oui")
        val expectedMinimum = mapOf(OuiRegistry.MaL to 30_000, OuiRegistry.MaM to 3_000, OuiRegistry.MaS to 3_000)
        val all = OuiRegistry.entries.flatMap { registry ->
            val file = File(dir, "${registry.label.lowercase()}.csv.gzip")
            val records = GZIPInputStream(file.inputStream()).bufferedReader().use { OuiCsvParser.parse(it, registry) }
            assertTrue("${registry.label}: ${records.size}", records.size >= expectedMinimum.getValue(registry))
            assertTrue(records.all { it.prefix.length == registry.hexDigits })
            records
        }.associateBy { it.prefix }

        assertEquals("Apple, Inc.", all["3C22FB"]?.organization)
        assertEquals("Synology Incorporated", all["001132"]?.organization)
        assertTrue(File(dir, "VERSION").readText().trim().matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
    }
}
