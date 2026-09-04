package com.ugaritic.vision

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

object YoloPostProcessor {

    private fun sigmoid(x: Float): Float {
        val clipped = max(-50.0f, min(50.0f, x))
        return 1.0f / (1.0f + exp(-clipped))
    }

    fun decode(data: FloatArray, shape: IntArray, conf: Float, iou: Float, maxDet: Int, imgsz: Int = 256): List<Detection> {
        if (shape.isEmpty()) return emptyList()
        val channels = Constants.NUM_CLASSES + 4
        
        // استخراج عدد الروابط (Rows) بدقة حسب شكل الـ TFLite Output
        val rows = when {
            shape.lastOrNull() == channels -> shape[0] // أو الصفوف حسب الـ shape
            shape.getOrNull(shape.size - 2) == channels -> shape[shape.size - 2]
            else -> {
                // إذا كان الشكل [1, channels, rows] أو [1, rows, channels]
                if (shape.size >= 3) shape[2] else shape.lastOrNull() ?: return emptyList()
            }
        }

        val candidates = ArrayList<Detection>()
        
        // دالة مساعدة لقراءة البيانات بناءً على ترتيب الأبعاد (Layout)
        fun at(r: Int, c: Int): Float {
            return if (shape.size >= 3 && shape[1] == channels) {
                // شكل [1, channels, rows]
                data[c * rows + r]
            } else if (shape.size >= 3 && shape[2] == channels) {
                // شكل [1, rows, channels]
                data[r * channels + c]
            } else {
                if (shape.lastOrNull() == channels) data[r * channels + c] else data[c * rows + r]
            }
        }

        for (r in 0 until rows) {
            var best = -1
            var bs = 0f
            
            for (c in 0 until Constants.NUM_CLASSES) {
                val rawScore = at(r, 4 + c)
                // تطبيق دالة Sigmoid تماماً مثل كود Kivy لضمان صحة الاحتمالية بين 0 و 1
                val s = if (rawScore < 0f || rawScore > 1f) sigmoid(rawScore) else rawScore
                
                if (s > bs) {
                    bs = s
                    best = c
                }
            }

            if (best >= 0 && bs >= conf) {
                var cx = at(r, 0)
                var cy = at(r, 1)
                var w = at(r, 2)
                var h = at(r, 3)

                // مطابقة كود Kivy: إذا كانت الإحداثيات نسبية وضمن مدى <= 2.0 يتم تحجيمها لـ imgsz
                if (max(max(cx, cy), max(w, h)) <= 2.0f) {
                    cx *= imgsz.toFloat()
                    cy *= imgsz.toFloat()
                    w *= imgsz.toFloat()
                    h *= imgsz.toFloat()
                }

                val x1 = cx - w / 2.0f
                val y1 = cy - h / 2.0f
                val x2 = cx + w / 2.0f
                val y2 = cy + h / 2.0f

                candidates.add(Detection(best, bs, x1, y1, x2, y2))
            }
        }

        // تطبيق الفلترة Class-wise NMS المطابقة لمنطق Kivy
        candidates.sortByDescending { it.confidence }
        val keep = ArrayList<Detection>()
        
        for (d in candidates) {
            if (keep.size >= maxDet) break
            // فحص التداخل الفئوي (Class-wise NMS)
            if (keep.none { it.classId == d.classId && iou(it, d) > iou }) {
                keep.add(d)
            }
        }
        
        return keep
    }

    private fun iou(a: Detection, b: Detection): Float {
        val x1 = max(a.x1, b.x1)
        val y1 = max(a.y1, b.y1)
        val x2 = min(a.x2, b.x2)
        val y2 = min(a.y2, b.y2)
        val inter = max(0f, x2 - x1) * max(0f, y2 - y1)
        val ua = (a.x2 - a.x1) * (a.y2 - a.y1)
        val ub = (b.x2 - b.x1) * (b.y2 - b.y1)
        return inter / (ua + ub - inter + 1e-6f)
    }
}
