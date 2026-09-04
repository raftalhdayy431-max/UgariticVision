package com.ugaritic.vision

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

class AnnotationView(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var detections = emptyList<Detection>()
    private var sourceW = 1
    private var sourceH = 1

    var boxThickness = 3f

    fun setDetections(d: List<Detection>, w: Int, h: Int) {
        detections = d
        sourceW = w
        sourceH = h
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)

        val sx = width.toFloat() / sourceW
        val sy = height.toFloat() / sourceH

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = boxThickness
        paint.color = Color.WHITE

        for (d in detections) {

            c.drawRect(
                d.x1 * sx,
                d.y1 * sy,
                d.x2 * sx,
                d.y2 * sy,
                paint
            )

            paint.style = Paint.Style.FILL
            paint.textSize = 28f

            val label =
                Constants.UGARITIC_CHARS.getOrElse(d.classId) { "?" } +
                " ${(d.confidence * 100).toInt()}%"

            c.drawText(
                label,
                d.x1 * sx,
                d.y1 * sy - 6,
                paint
            )

            paint.style = Paint.Style.STROKE
        }
    }
}
