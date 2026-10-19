package com.tdater.blackbox.system

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.tdater.blackbox.core.report.Report
import java.io.File
import java.io.OutputStream

/**
 * Draws a [Report] onto A4 pages with Android's built-in PdfDocument. No libraries, no network.
 * Photos are scaled down before drawing to keep memory and file size sane.
 */
object PdfReport {
    private const val W = 595 // A4 at 72 dpi
    private const val H = 842
    private const val M = 48f
    private const val ACCENT = 0xFFC2410C.toInt()

    fun write(report: Report, out: OutputStream, imageFile: (String) -> File?) {
        val doc = PdfDocument()
        var pageNo = 0
        lateinit var page: PdfDocument.Page
        lateinit var c: Canvas
        var y = M

        val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 22f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = Color.BLACK }
        val sub = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9.5f; color = 0xFF555555.toInt() }
        val head = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 13f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = ACCENT }
        val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10.5f; color = 0xFF1F1A17.toInt() }
        val rule = Paint().apply { color = ACCENT; strokeWidth = 1.2f }
        val foot = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8f; color = 0xFF888888.toInt() }
        val width = (W - 2 * M).toInt()

        fun newPage() {
            if (pageNo > 0) doc.finishPage(page)
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
            c = page.canvas
            c.drawText("BLACKBOX · ${report.title.take(70)} · page $pageNo", M, H - 24f, foot)
            y = M
        }

        fun layout(text: String, p: TextPaint, indent: Float = 0f) =
            StaticLayout.Builder.obtain(text, 0, text.length, p, (width - indent).toInt()).setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(1.5f, 1f).build()

        fun draw(text: String, p: TextPaint, indent: Float = 0f, after: Float = 4f) {
            val l = layout(text, p, indent)
            // Long blocks may span pages: draw line by line.
            for (i in 0 until l.lineCount) {
                val lh = (l.getLineBottom(i) - l.getLineTop(i)).toFloat()
                if (y + lh > H - 48f) newPage()
                c.save()
                c.translate(M + indent, y - l.getLineTop(i))
                c.clipRect(0f, l.getLineTop(i).toFloat(), width.toFloat(), l.getLineBottom(i).toFloat())
                l.draw(c)
                c.restore()
                y += lh
            }
            y += after
        }

        fun image(file: File) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            if (bounds.outWidth <= 0) return
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1200) sample *= 2
            val bmp: Bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return
            val maxW = width.toFloat()
            val maxH = 300f
            val scale = minOf(maxW / bmp.width, maxH / bmp.height)
            val w = bmp.width * scale
            val h = bmp.height * scale
            if (y + h > H - 48f) newPage()
            c.drawBitmap(bmp, null, RectF(M, y, M + w, y + h), Paint(Paint.FILTER_BITMAP_FLAG))
            bmp.recycle()
            y += h + 8f
        }

        newPage()
        draw(report.title, title, after = 2f)
        draw(report.subtitle, sub, after = 10f)
        for (s in report.sections) {
            if (y + 40f > H - 48f) newPage()
            draw(s.heading, head, after = 2f)
            c.drawLine(M, y, W - M, y, rule)
            y += 6f
            s.paragraphs.forEach { draw(it, body) }
            s.bullets.forEach { draw("•  $it", body, indent = 6f, after = 2f) }
            s.imageEvidenceIds.forEach { id -> imageFile(id)?.let { runCatching { image(it) } } }
            y += 8f
        }
        doc.finishPage(page)
        try { doc.writeTo(out) } finally { doc.close() }
    }
}
