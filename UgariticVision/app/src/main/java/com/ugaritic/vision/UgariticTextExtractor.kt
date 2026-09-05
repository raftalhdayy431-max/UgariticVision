package com.ugaritic.vision

object UgariticTextExtractor {

    fun extract(detections: List<Detection>): String {
        if (detections.isEmpty()) return ""

        // 1. ترتيب الاكتشافات: أولاً حسب المحور الرأسي (y1) ثم المحور الأفقي (x1)
        val sortedDetections = detections.sortedWith(
            compareBy<Detection> { it.y1 }.thenBy { it.x1 }
        )

        // 2. تجميع الحروف أو فاصل الكلمات
        return buildString {
            for (d in sortedDetections) {
                when {
                    // إذا كان الفهرس يمثل فاصل الكلمات (Word Divider 𐎟)
                    d.classId == Constants.WORD_DIVIDER_CLASS -> {
                        append(" ")
                    }
                    // إذا كان الحرف ضمن مصفوفة الحروف الأغاريتية المعتمدة
                    d.classId in Constants.UGARITIC_CHARS.indices -> {
                        append(Constants.UGARITIC_CHARS[d.classId])
                    }
                    else -> {
                        // تجاهل أي فهرس خارج النطاق لتجنب الأخطاء
                    }
                }
            }
        }
    }
}
