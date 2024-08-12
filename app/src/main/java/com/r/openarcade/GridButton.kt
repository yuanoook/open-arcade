package com.r.openarcade

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import com.r.openarcade.camera.SoundManager

data class GridButton(
    val xIndex: Int,
    val yIndex: Int,
    val soundManager: SoundManager,
    val xTotal: Int = 7, // Default xTotal
    val yTotal: Int = 10, // Default yTotal
    var text: String = "", // Default empty text
    val fillColor: Int = 0x80FFFFFF.toInt(), // Default fill color: half-transparent white
    val borderColor: Int = 0xFFFFFFFF.toInt(), // Default border color: white
    val strokeWidth: Float = 5f,
    val cornerRadius: Float = 20f, // Default corner radius
    val fillAlpha: Int = 128, // Default fill alpha
    val borderAlpha: Int = 255, // Default border alpha
    val marginXPercent: Float = 0.05f, // Default marginX: 10% of button width
    var marginYPercent: Float = 0.05f,  // Default marginY: 10% of button height
    var fontSize: Float = 60f, // Default text size
    val soundKey: Int = 0,
    var lastActivatedAt: Long = 0L,
    var active: Boolean = false,
    var canvas: Canvas? = null
) {
    fun update(newCanvas: Canvas) {
        canvas = newCanvas
    }

    fun draw() {
        // Calculate the width and height of each grid cell
        val cellWidth = canvas!!.width / xTotal.toFloat()
        val cellHeight = canvas!!.height / yTotal.toFloat()

        // Calculate margins based on the percentage of the cell width and height
        val marginX = cellWidth * marginXPercent
        val marginY = cellHeight * marginYPercent * (if (active) -2f else 1f)

        // Calculate the position of the button with margins
        val left = (xIndex - 1) * cellWidth + marginX
        val top = (yIndex - 1) * cellHeight + marginY
        val right = xIndex * cellWidth - marginX
        val bottom = yIndex * cellHeight - marginY

        // Define the rectangle for the button
        val rect = RectF(left, top, right, bottom)

        // Prepare the fill paint
        val fillPaint = Paint().apply {
            color = fillColor
            alpha = fillAlpha
            this.style = Paint.Style.FILL
        }

        // Prepare the border paint
        val borderPaint = Paint().apply {
            color = borderColor
            alpha = borderAlpha
            this.style = Paint.Style.STROKE
            strokeWidth = this@GridButton.strokeWidth
        }

        // Draw the rounded rectangle with fill and border
        canvas!!.drawRoundRect(rect, cornerRadius, cornerRadius, fillPaint)
        canvas!!.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)

        // Draw button text (if provided)
        if (text.isNotEmpty()) {
            val textPaint = Paint().apply {
                color = borderColor
                textSize = if (!active) fontSize else fontSize * 1.5f
                textAlign = Paint.Align.CENTER
            }
            // Calculate text position
            val textX = (left + right) / 2
            val textY = (top + bottom) / 2 - (textPaint.ascent() + textPaint.descent()) / 2
            canvas!!.drawText(text, textX, textY, textPaint)
        }
    }

    fun stroke(oldPoint: PointF, newPoint: PointF): Boolean {
        if (!isStrokeCrossesTop(oldPoint, newPoint)) return false

        soundManager.playSound(soundKey)

        return true
    }

    fun isStrokeCrossesTop(oldPoint: PointF, newPoint: PointF): Boolean {
        // Calculate the width and height of each grid cell
        val cellWidth = canvas!!.width / xTotal.toFloat()
        val cellHeight = canvas!!.height / yTotal.toFloat()

        // Calculate margins based on the percentage of the cell width and height
        val marginX = cellWidth * marginXPercent
        val marginY = cellHeight * marginYPercent

        // Calculate the position of the button with margins
        val left = (xIndex - 1) * cellWidth + marginX
        val top = (yIndex - 1) * cellHeight + marginY
        val right = xIndex * cellWidth - marginX
        val bottom = yIndex * cellHeight - marginY

        // Define the rectangle for the button
        val buttonRect = RectF(left, top, right, bottom)

        // Check if point A is higher than the button (above the top border)
        if (oldPoint.y >= top) {
            return false
        }

        // Use the linesIntersect function to check if the line AB intersects with the top border of the button
        val topLeft = PointF(buttonRect.left, buttonRect.top)
        val topRight = PointF(buttonRect.right, buttonRect.top)

        return linesIntersect(oldPoint, newPoint, topLeft, topRight)
    }

    private fun linesIntersect(p1: PointF, p2: PointF, p3: PointF, p4: PointF): Boolean {
        val denom = (p4.y - p3.y) * (p2.x - p1.x) - (p4.x - p3.x) * (p2.y - p1.y)
        if (denom == 0f) return false // Parallel lines
        val ua = ((p4.x - p3.x) * (p1.y - p3.y) - (p4.y - p3.y) * (p1.x - p3.x)) / denom
        val ub = ((p2.x - p1.x) * (p1.y - p3.y) - (p2.y - p1.y) * (p1.x - p3.x)) / denom
        return ua in 0f..1f && ub in 0f..1f
    }
}
