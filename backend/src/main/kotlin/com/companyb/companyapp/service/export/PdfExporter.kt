package com.companyb.companyapp.service.export

import com.lowagie.text.Document
import com.lowagie.text.Font
import com.lowagie.text.FontFactory
import com.lowagie.text.PageSize
import com.lowagie.text.Paragraph
import com.lowagie.text.pdf.PdfPCell
import com.lowagie.text.pdf.PdfPTable
import com.lowagie.text.pdf.PdfWriter
import java.awt.Color
import java.io.ByteArrayOutputStream

object PdfExporter {
    private const val TITLE_FONT_SIZE = 14f
    private const val HEADER_FONT_SIZE = 10f
    private const val TABLE_WIDTH_PERCENTAGE = 100f
    private const val HEADER_BG_RED = 220
    private const val HEADER_BG_GREEN = 220
    private const val HEADER_BG_BLUE = 220
    private const val BORDER_WIDTH = 1

    fun generate(
        title: String,
        headers: List<String>,
        rows: List<List<String>>,
    ): ByteArray {
        val outputStream = ByteArrayOutputStream()
        val document = Document(PageSize.A4)

        PdfWriter.getInstance(document, outputStream)
        document.open()

        val titleFont = FontFactory.getFont(FontFactory.HELVETICA, TITLE_FONT_SIZE, Font.BOLD)
        document.add(Paragraph(title, titleFont))
        document.add(Paragraph(" "))

        val table = PdfPTable(headers.size)
        table.widthPercentage = TABLE_WIDTH_PERCENTAGE

        val headerFont = FontFactory.getFont(FontFactory.HELVETICA, HEADER_FONT_SIZE, Font.BOLD)
        val headerBg = Color(HEADER_BG_RED, HEADER_BG_GREEN, HEADER_BG_BLUE)

        for (header in headers) {
            val cell = PdfPCell()
            cell.phrase = com.lowagie.text.Phrase(header, headerFont)
            cell.backgroundColor = headerBg
            cell.horizontalAlignment = PdfPCell.ALIGN_CENTER
            cell.border = BORDER_WIDTH
            table.addCell(cell)
        }

        for (row in rows) {
            for (cellValue in row) {
                val cell = PdfPCell()
                cell.phrase = com.lowagie.text.Phrase(cellValue)
                cell.border = BORDER_WIDTH
                table.addCell(cell)
            }
        }

        document.add(table)
        document.close()

        return outputStream.toByteArray()
    }
}
