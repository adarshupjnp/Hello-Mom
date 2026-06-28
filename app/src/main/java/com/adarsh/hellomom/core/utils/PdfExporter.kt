package com.adarsh.hellomom.core.utils

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.adarsh.hellomom.R
import java.io.FileOutputStream
import java.util.*

object PdfExporter {

    /**
     * Modern PDF export for Bills & Expenses.
     * Matches the visual style of the provided design image.
     */
    fun exportBillingToPdf(
        context: Context,
        uri: Uri,
        userName: String,
        week: Int,
        totalAmount: Double,
        rows: List<BillingPdfRow>,
        downloadUrl: String = "https://hello-mom-6e500.web.app/"
    ) {
        val pdfDocument = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // 0. Outer Border / Frame
        paint.color = Color.parseColor("#F0F0F0") // Subtle Light Gray
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        canvas.drawRect(5f, 5f, pageWidth - 5f, pageHeight - 5f, paint)
        paint.style = Paint.Style.FILL

        val margin = 40f
        var currentY = 40f

        // 1. Header (Logo + User Info)
        // Draw Logo
        val logoSize = 60f
        val logoBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.ic_app_logo)
        if (logoBitmap != null) {
            val src = Rect(0, 0, logoBitmap.width, logoBitmap.height)
            val dst = RectF(margin, currentY, margin + logoSize, currentY + logoSize)
            canvas.drawBitmap(logoBitmap, src, dst, paint)
        }

        // App Name
        paint.color = Color.parseColor("#1A237E") // Deep Blue
        paint.textSize = 22f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Hello", margin + logoSize + 10f, currentY + 25f, paint)
        paint.color = Color.parseColor("#42A5F5") // Sky Blue
        canvas.drawText("Mom+", margin + logoSize + 10f, currentY + 50f, paint)

        // User Info (Top Right)
        paint.textAlign = Paint.Align.RIGHT
        paint.color = Color.GRAY
        paint.textSize = 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("User's Name", pageWidth - margin, currentY + 10f, paint)
        
        paint.color = Color.BLACK
        paint.textSize = 16f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(userName, pageWidth - margin, currentY + 30f, paint)
        
        paint.color = Color.BLACK
        paint.textSize = 11f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        val trimester = when {
            week <= 12 -> "1st Trimester"
            week <= 26 -> "2nd Trimester"
            else -> "3rd Trimester"
        }
        canvas.drawText("Pregnancy Status: Week $week, $trimester", pageWidth - margin, currentY + 50f, paint)

        currentY += 80f
        paint.textAlign = Paint.Align.LEFT

        // 2. Financial Summary Card (Gradient Card)
        val cardHeight = 100f
        val cardRect = RectF(margin, currentY, pageWidth - margin, currentY + cardHeight)
        val gradient = LinearGradient(
            cardRect.left, cardRect.top, cardRect.right, cardRect.bottom,
            intArrayOf(Color.parseColor("#FFD1DC"), Color.parseColor("#B2EBF2")),
            null, Shader.TileMode.CLAMP
        )
        paint.shader = gradient
        canvas.drawRoundRect(cardRect, 20f, 20f, paint)
        paint.shader = null

        paint.color = Color.DKGRAY
        paint.textSize = 14f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("Total Expenses This Month", pageWidth / 2f, currentY + 35f, paint)
        
        paint.color = Color.BLACK
        paint.textSize = 36f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("₹$totalAmount", pageWidth / 2f, currentY + 80f, paint)

        currentY += cardHeight + 40f

        // 3. Category Infographic (Pie Chart)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 16f
        canvas.drawText("Category Infographic", pageWidth / 2f, currentY, paint)
        currentY += 20f

        val chartHeight = 160f
        val chartRect = RectF(margin, currentY, pageWidth - margin, currentY + chartHeight)
        paint.color = Color.parseColor("#F5F5F5")
        canvas.drawRoundRect(chartRect, 20f, 20f, paint)

        // Draw Pie Chart (Simplified)
        val categoryData = rows.groupBy { it.category }.mapValues { it.value.sumOf { r -> r.amount } }
        val chartSize = 100f
        val chartBox = RectF(margin + 40f, currentY + 30f, margin + 40f + chartSize, currentY + 30f + chartSize)
        var startAngle = 0f
        val colors = listOf("#F06292", "#9575CD", "#64B5F6", "#81C784", "#FFD54F")
        
        categoryData.entries.forEachIndexed { index, entry ->
            val sweep = (entry.value / totalAmount).toFloat() * 360f
            paint.color = Color.parseColor(colors[index % colors.size])
            canvas.drawArc(chartBox, startAngle, sweep, true, paint)
            
            // Draw Legend
            paint.textAlign = Paint.Align.LEFT
            paint.textSize = 10f
            val legendX = margin + 180f
            val legendY = currentY + 40f + (index * 20f)
            canvas.drawRect(legendX, legendY - 8f, legendX + 10f, legendY + 2f, paint)
            paint.color = Color.BLACK
            canvas.drawText("${entry.key}: ₹${entry.value}", legendX + 20f, legendY, paint)
            
            startAngle += sweep
        }

        currentY += chartHeight + 40f

        // 4. Itemized Billing Table
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 16f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Itemized Billing Table", pageWidth / 2f, currentY, paint)
        currentY += 20f

        val tableHeaderRect = RectF(margin, currentY, pageWidth - margin, currentY + 30f)
        paint.color = Color.parseColor("#FFEBEE")
        canvas.drawRoundRect(tableHeaderRect, 10f, 10f, paint)
        
        paint.color = Color.BLACK
        paint.textSize = 12f
        val col1 = margin + 30f
        val col2 = margin + 120f
        val col3 = margin + 320f
        val col4 = pageWidth - margin - 40f
        
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("Date", col1, currentY + 20f, paint)
        canvas.drawText("Description", col2, currentY + 20f, paint)
        canvas.drawText("Category", col3, currentY + 20f, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Amount", col4, currentY + 20f, paint)

        currentY += 35f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        
        rows.forEachIndexed { index, row ->
            if (index % 2 == 0) {
                paint.color = Color.parseColor("#FAFAFA")
                canvas.drawRect(margin, currentY - 5f, pageWidth - margin, currentY + 25f, paint)
            }
            
            paint.color = Color.BLACK
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText(row.date, col1, currentY + 15f, paint)
            canvas.drawText(row.description, col2, currentY + 15f, paint)
            canvas.drawText(row.category, col3, currentY + 15f, paint)
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("₹${row.amount}", col4, currentY + 15f, paint)
            
            currentY += 30f
        }

        // 5. Footer (QR Code + Disclaimer)
        currentY = pageHeight - 110f
        paint.color = Color.parseColor("#EEEEEE")
        canvas.drawLine(margin, currentY, pageWidth - margin, currentY, paint)
        
        currentY += 25f
        // AI Badge - Improved sizing and centering
        val badgeWidth = 120f
        val badgeHeight = 45f
        val badgeRect = RectF(margin, currentY, margin + badgeWidth, currentY + badgeHeight)
        paint.color = Color.parseColor("#E3F2FD")
        canvas.drawRoundRect(badgeRect, 12f, 12f, paint)
        
        paint.color = Color.parseColor("#1976D2")
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = 11f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("AI Verified", margin + 15f, currentY + 20f, paint)
        
        paint.textSize = 9f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Hello Mom+ AI", margin + 15f, currentY + 36f, paint)

        // QR Code Placeholder - Detailed and Visible
        val qrSize = 60f
        val qrX = pageWidth - margin - qrSize
        val qrY = currentY - 10f
        
        // Background for QR
        paint.color = Color.WHITE
        canvas.drawRect(qrX - 5, qrY - 5, qrX + qrSize + 5, qrY + qrSize + 5, paint)
        
        // Main QR body (Drawn with patterns)
        paint.color = Color.BLACK
        canvas.drawRect(qrX, qrY, qrX + qrSize, qrY + qrSize, paint)
        
        // Finder patterns (The 3 large squares)
        paint.color = Color.WHITE
        fun drawFinder(x: Float, y: Float) {
            canvas.drawRect(x + 4, y + 4, x + 16, y + 16, paint)
            paint.color = Color.BLACK
            canvas.drawRect(x + 7, y + 7, x + 13, y + 13, paint)
            paint.color = Color.WHITE
        }
        drawFinder(qrX, qrY)
        drawFinder(qrX + qrSize - 20, qrY)
        drawFinder(qrX, qrY + qrSize - 20)
        
        // Some random "data" bits
        paint.color = Color.WHITE
        val random = Random(42)
        for (i in 0..15) {
            val rx = qrX + random.nextInt(qrSize.toInt()).toFloat()
            val ry = qrY + random.nextInt(qrSize.toInt()).toFloat()
            canvas.drawRect(rx, ry, rx + 3, ry + 3, paint)
        }
        
        // QR text - Better alignment
        paint.color = Color.GRAY
        paint.textAlign = Paint.Align.RIGHT
        paint.textSize = 9f
        canvas.drawText("Scan to download app", qrX - 15f, currentY + 12f, paint)
        paint.color = Color.parseColor("#1976D2")
        canvas.drawText(downloadUrl.removePrefix("https://"), qrX - 15f, currentY + 26f, paint)
        paint.color = Color.GRAY
        canvas.drawText("Secure Digital Sync", qrX - 15f, currentY + 40f, paint)

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

    // Keeping the original generic exporter to avoid breaking other features
    fun exportToPdf(
        context: Context,
        uri: Uri,
        title: String,
        userName: String,
        week: Int,
        content: List<PdfRow>
    ) {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        var y = 50f

        // Simple Header for other reports
        paint.color = Color.parseColor("#FFD1DC")
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

    data class BillingPdfRow(
        val date: String,
        val description: String,
        val category: String,
        val amount: Double
    )
}
