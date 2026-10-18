package com.companyb.companyapp.reporting

import org.dhatim.fastexcel.Workbook
import org.dhatim.fastexcel.Worksheet
import org.openpdf.text.Chunk
import org.openpdf.text.Document
import org.openpdf.text.Element
import org.openpdf.text.Font
import org.openpdf.text.FontFactory
import org.openpdf.text.PageSize
import org.openpdf.text.Paragraph
import org.openpdf.text.Phrase
import org.openpdf.text.pdf.PdfPCell
import org.openpdf.text.pdf.PdfPTable
import org.openpdf.text.pdf.PdfWriter
import java.awt.Color
import java.io.ByteArrayOutputStream
import java.text.DecimalFormat
import java.time.format.DateTimeFormatter

/** Renders an [AccountingReport] as an Excel workbook (one sheet per table) or a PDF. */
object AccountingReportWriter {
    private const val MONEY_FORMAT = "#,##0.00"
    private const val DATE_FORMAT = "yyyy-mm-dd"
    private const val TIME_FORMAT = "hh:mm"
    private const val HEADER_FILL = "DDDDDD"
    private const val HEADING_FILL = "F0F0F0"
    private const val FIRST_TABLE_ROW = 3
    private const val SHEET_NAME_MAX = 31

    fun xlsx(report: AccountingReport): ByteArray {
        val out = ByteArrayOutputStream()
        val wb = Workbook(out, "Company App", "1.0")
        for (table in report.tables) writeSheet(wb.newWorksheet(table.name.take(SHEET_NAME_MAX)), report, table)
        wb.finish()
        return out.toByteArray()
    }

    private fun writeSheet(
        ws: Worksheet,
        report: AccountingReport,
        table: ReportTable,
    ) {
        ws.value(0, 0, "${report.title}: ${table.title}")
        ws
            .style(0, 0)
            .bold()
            .fontSize(TITLE_SIZE)
            .set()
        ws.value(1, 0, report.lines.joinToString("  |  "))
        val head = FIRST_TABLE_ROW
        table.headers.forEachIndexed { c, h ->
            ws.value(head, c, h)
            ws
                .style(head, c)
                .bold()
                .fillColor(HEADER_FILL)
                .set()
        }
        table.widths.forEachIndexed { c, w -> ws.width(c, w) }
        table.rows.forEachIndexed { i, row ->
            val r = head + 1 + i
            row.forEachIndexed { c, cell -> writeCell(ws, r, c, cell, bold = i in table.headingRows) }
            if (i in table.headingRows) {
                for (c in table.headers.indices) {
                    ws
                        .style(r, c)
                        .bold()
                        .fillColor(HEADING_FILL)
                        .set()
                }
            }
        }
        table.totals?.let { totals ->
            val r = head + 1 + table.rows.size
            totals.forEachIndexed { c, cell -> writeCell(ws, r, c, cell, bold = true) }
        }
        ws.freezePane(0, head + 1)
        if (table.headingRows.isEmpty() && table.rows.isNotEmpty()) {
            ws.setAutoFilter(head, 0, table.headers.size - 1)
        }
    }

    private fun writeCell(
        ws: Worksheet,
        r: Int,
        c: Int,
        cell: ReportCell,
        bold: Boolean,
    ) {
        val format =
            when (cell) {
                is ReportCell.Text -> {
                    ws.value(r, c, cell.value)
                    null
                }

                is ReportCell.Money -> {
                    ws.value(r, c, cell.value)
                    MONEY_FORMAT
                }

                is ReportCell.Count -> {
                    ws.value(r, c, cell.value)
                    null
                }

                is ReportCell.Day -> {
                    ws.value(r, c, cell.value)
                    DATE_FORMAT
                }

                is ReportCell.Time -> {
                    ws.value(r, c, cell.value.toSecondOfDay().toDouble() / SECONDS_PER_DAY)
                    TIME_FORMAT
                }

                ReportCell.Blank -> {
                    null
                }
            }
        if (format != null && bold) {
            ws
                .style(r, c)
                .format(format)
                .bold()
                .set()
        } else if (format != null) {
            ws.style(r, c).format(format).set()
        } else if (bold) {
            ws.style(r, c).bold().set()
        }
    }

    fun pdf(report: AccountingReport): ByteArray {
        val out = ByteArrayOutputStream()
        val doc = Document(PageSize.A4.rotate(), MARGIN, MARGIN, MARGIN, MARGIN)
        PdfWriter.getInstance(doc, out)
        doc.open()
        doc.add(Paragraph(report.title, font(TITLE_SIZE.toFloat(), bold = true)))
        report.lines.forEach { doc.add(Paragraph(it, font(BODY_SIZE + 1, bold = false))) }
        for (table in report.tables) {
            val heading = Paragraph(table.title, font(SECTION_SIZE, bold = true))
            heading.spacingBefore = SECTION_GAP
            heading.spacingAfter = CELL_PAD
            doc.add(heading)
            if (table.rows.isEmpty()) {
                doc.add(Paragraph("Nothing in this range.", font(BODY_SIZE, bold = false)))
                continue
            }
            doc.add(pdfTable(table))
        }
        doc.close()
        return out.toByteArray()
    }

    private fun pdfTable(table: ReportTable): PdfPTable {
        val t = PdfPTable(table.headers.size)
        t.widthPercentage = if (table.headers.size <= SUMMARY_COLS) HALF_WIDTH else FULL_WIDTH
        t.horizontalAlignment = Element.ALIGN_LEFT
        if (table.widths.size == table.headers.size) t.setWidths(table.widths.map { it.toFloat() }.toFloatArray())
        t.headerRows = 1
        val headerBg = Color(HEADER_GREY, HEADER_GREY, HEADER_GREY)
        val headingBg = Color(HEADING_GREY, HEADING_GREY, HEADING_GREY)
        table.headers.forEach { t.addCell(pdfCell(it, bold = true, right = false, bg = headerBg)) }
        table.rows.forEachIndexed { i, row ->
            val heading = i in table.headingRows
            row.forEach { cell -> t.addCell(pdfCell(cell, heading, if (heading) headingBg else null)) }
        }
        table.totals?.forEach { cell -> t.addCell(pdfCell(cell, bold = true, bg = null)) }
        return t
    }

    private fun pdfCell(
        cell: ReportCell,
        bold: Boolean,
        bg: Color?,
    ): PdfPCell {
        val right = cell is ReportCell.Money || cell is ReportCell.Count
        return pdfCell(display(cell), bold, right, bg)
    }

    private fun pdfCell(
        text: String,
        bold: Boolean,
        right: Boolean,
        bg: Color?,
    ): PdfPCell {
        val c = PdfPCell(Phrase(Chunk(text, font(BODY_SIZE, bold))))
        c.horizontalAlignment = if (right) Element.ALIGN_RIGHT else Element.ALIGN_LEFT
        c.setPadding(CELL_PAD)
        c.borderColor = Color(BORDER_GREY, BORDER_GREY, BORDER_GREY)
        if (bg != null) c.backgroundColor = bg
        return c
    }

    private val moneyText = DecimalFormat("#,##0.00")
    private val timeText = DateTimeFormatter.ofPattern("HH:mm")

    internal fun display(cell: ReportCell): String =
        when (cell) {
            is ReportCell.Text -> cell.value
            is ReportCell.Money -> moneyText.format(cell.value)
            is ReportCell.Count -> cell.value.toString()
            is ReportCell.Day -> cell.value.toString()
            is ReportCell.Time -> cell.value.format(timeText)
            ReportCell.Blank -> ""
        }

    private fun font(
        size: Float,
        bold: Boolean,
    ): Font = FontFactory.getFont(FontFactory.HELVETICA, size, if (bold) Font.BOLD else Font.NORMAL)

    private const val TITLE_SIZE = 14
    private const val SECTION_SIZE = 11f
    private const val BODY_SIZE = 7f
    private const val MARGIN = 24f
    private const val SECTION_GAP = 12f
    private const val CELL_PAD = 3f
    private const val SUMMARY_COLS = 3
    private const val HALF_WIDTH = 60f
    private const val FULL_WIDTH = 100f
    private const val HEADER_GREY = 220
    private const val HEADING_GREY = 240
    private const val BORDER_GREY = 190
    private const val SECONDS_PER_DAY = 86_400.0
}
