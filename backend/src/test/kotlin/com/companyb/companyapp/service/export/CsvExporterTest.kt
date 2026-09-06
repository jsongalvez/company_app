package com.companyb.companyapp.service.export

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CsvExporterTest {
    @Test
    fun `formula prefixes gain single-quote marker`() {
        assertEquals("'=1+1", CsvExporter.encodeText("=1+1"))
        assertEquals("'+1+1", CsvExporter.encodeText("+1+1"))
        assertEquals("'@SUM(1)", CsvExporter.encodeText("@SUM(1)"))
        assertEquals("'-1+1", CsvExporter.encodeText("-1+1"))
    }

    @Test
    fun `leading spaces and controls before formula still prefixed`() {
        assertEquals("'  =1+1", CsvExporter.encodeText("  =1+1"))
        assertTrue(CsvExporter.encodeText("\t=1+1").startsWith("'"))
        assertEquals("\"'\r=1+1\"", CsvExporter.encodeText("\r=1+1"))
        assertEquals("\"'\n=1+1\"", CsvExporter.encodeText("\n=1+1"))
        assertTrue(CsvExporter.encodeText("\tHello").startsWith("'"))
    }

    @Test
    fun `plain text untouched`() {
        assertEquals("Hello", CsvExporter.encodeText("Hello"))
        assertEquals("", CsvExporter.encodeText(""))
        assertEquals("   ", CsvExporter.encodeText("   "))
        assertEquals("123", CsvExporter.encodeText("123"))
    }

    @Test
    fun `text negatives prefixed but numeric negatives stay numeric`() {
        assertEquals("'-123", CsvExporter.encodeCell(CsvCell.Text("-123")))
        assertEquals("-123", CsvExporter.encodeCell(CsvCell.Numeric("-123")))
        assertEquals("-300.00", CsvExporter.encodeCell(CsvCell.Numeric("-300.00")))
        assertEquals("2500.00", CsvExporter.encodeCell(CsvCell.Numeric("2500.00")))
    }

    @Test
    fun `non-numeric in numeric cell falls back to text encoding`() {
        assertEquals("'=1+1", CsvExporter.encodeCell(CsvCell.Numeric("=1+1")))
    }

    @Test
    fun `delimiters quotes and line breaks quoted`() {
        assertEquals("\"a,b\"", CsvExporter.encodeText("a,b"))
        assertEquals("\"a\"\"b\"", CsvExporter.encodeText("a\"b"))
        assertEquals("\"a\nb\"", CsvExporter.encodeText("a\nb"))
        assertEquals("\"a\rb\"", CsvExporter.encodeText("a\rb"))
        assertEquals("\"a\r\nb\"", CsvExporter.encodeText("a\r\nb"))
    }

    @Test
    fun `formula with delimiter prefixed then quoted`() {
        assertEquals("\"'=1,1\"", CsvExporter.encodeText("=1,1"))
    }

    @Test
    fun `generate round trip preserves marker and numerics`() {
        val headers = listOf("Branch", "Net Income")
        val rows =
            listOf(
                listOf(CsvCell.Text("=1+1"), CsvCell.Numeric("1500.00")),
                listOf(CsvCell.Text("+1+1"), CsvCell.Numeric("-300.00")),
                listOf(CsvCell.Text("@SUM(1)"), CsvCell.Numeric("0.00")),
                listOf(CsvCell.Text("Com,ma \"Q\""), CsvCell.Numeric("10.00")),
            )
        val csv = CsvExporter.generate(headers, rows).toString(Charsets.UTF_8)
        val parsed = parseCsv(csv)
        assertEquals(listOf("Branch", "Net Income"), parsed[0])
        assertEquals("'=1+1", parsed[1][0])
        assertEquals("1500.00", parsed[1][1])
        assertEquals("'+1+1", parsed[2][0])
        assertEquals("-300.00", parsed[2][1])
        assertFalse(parsed[2][1].startsWith("'"))
        assertEquals("'@SUM(1)", parsed[3][0])
        assertEquals("Com,ma \"Q\"", parsed[4][0])
        assertTrue(csv.contains("-300.00"))
        assertFalse(csv.contains("'-300.00"))
    }

    @Test
    fun `lone CR and CRLF survive quoted round trip`() {
        val headers = listOf("Branch", "Net Income")
        val rows =
            listOf(
                listOf(CsvCell.Text("lone\rcr"), CsvCell.Numeric("1.00")),
                listOf(CsvCell.Text("crlf\r\nbreak"), CsvCell.Numeric("2.00")),
            )
        val csv = CsvExporter.generate(headers, rows).toString(Charsets.UTF_8)
        assertTrue(csv.contains("\"lone\rcr\""))
        assertTrue(csv.contains("\"crlf\r\nbreak\""))
        val parsed = parseCsv(csv)
        assertEquals("lone\rcr", parsed[1][0])
        assertEquals("crlf\r\nbreak", parsed[2][0])
    }

    private fun parseCsv(text: String): List<List<String>> = splitRows(text).map { splitLine(it) }

    private fun splitRows(text: String): List<String> {
        val rows = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuotes = false
        for (c in text) {
            if (c == '"') {
                inQuotes = !inQuotes
                cur.append(c)
            } else if (c == '\n' && !inQuotes) {
                rows.add(cur.toString())
                cur.clear()
            } else {
                cur.append(c)
            }
        }
        if (cur.isNotEmpty()) {
            rows.add(cur.toString())
        }
        return rows
    }

    private fun splitLine(line: String): List<String> {
        val cells = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            if (inQuotes && isEscapedQuote(line, i)) {
                cur.append('"')
                i += 2
            } else if (line[i] == '"') {
                inQuotes = !inQuotes
                i++
            } else if (line[i] == ',' && !inQuotes) {
                cells.add(cur.toString())
                cur.clear()
                i++
            } else {
                cur.append(line[i])
                i++
            }
        }
        cells.add(cur.toString())
        return cells
    }

    private fun isEscapedQuote(
        line: String,
        i: Int,
    ): Boolean = i + 1 < line.length && line[i] == '"' && line[i + 1] == '"'
}
