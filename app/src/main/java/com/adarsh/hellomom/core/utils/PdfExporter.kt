package com.adarsh.hellomom.core.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import java.io.FileOutputStream

object PdfExporter {

    fun exportToPdf(
        context: Context,
        uri: Uri,
        title: String,
        userName: String,
        week: Int,
        content: List<PdfRow>
    ) {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 Size
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas
        val paint = Paint()

        var y = 50f

        // Header
        paint.color = Color.parseColor("#FFD1DC") // Primary Pastel Pink
        canvas.drawRect(0f, 0f, 595f, 100f, paint)

        paint.color = Color.BLACK
        paint.textSize = 24f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Hello Mom+ Report", 40f, 45f, paint)

        paint.textSize = 18f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText(title, 40f, 75f, paint)

        y = 130f
        paint.textSize = 14f
        canvas.drawText("User: $userName", 40f, y, paint)
        canvas.drawText("Pregnancy Progress: Week $week", 300f, y, paint)
        
        y += 30f
        paint.color = Color.LTGRAY
        canvas.drawLine(40f, y, 555f, y, paint)
        
        y += 40f
        paint.color = Color.BLACK
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        
        // Content Table Header
        canvas.drawText("Date", 40f, y, paint)
        canvas.drawText("Description", 150f, y, paint)
        canvas.drawText("Details", 400f, y, paint)
        
        y += 10f
        paint.color = Color.GRAY
        canvas.drawLine(40f, y, 555f, y, paint)
        
        y += 30f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.color = Color.BLACK

        content.forEach { row ->
            if (y > 800) {
                // Should start new page in real implementation
            }
            canvas.drawText(row.date, 40f, y, paint)
            canvas.drawText(row.description, 150f, y, paint)
            canvas.drawText(row.details, 400f, y, paint)
            y += 25f
        }

        pdfDocument.finishPage(page)

        try {
            context.contentResolver.openFileDescriptor(uri, "w")?.use { pfd ->
                FileOutputStream(pfd.fileDescriptor).use { fos ->
                    pdfDocument.writeTo(fos)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            pdfDocument.close()
        }
    }

    data class PdfRow(
        val date: String,
        val description: String,
        val details: String
    )
}
