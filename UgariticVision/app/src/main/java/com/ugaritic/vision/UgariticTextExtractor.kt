package com.ugaritic.vision
object UgariticTextExtractor {
    fun extract(detections:List<Detection>):String = detections.sortedWith(compareBy<Detection>{it.y1}.thenBy{it.x1}).joinToString(""){d->if(d.classId==Constants.WORD_DIVIDER_CLASS)" " else Constants.UGARITIC_CHARS.getOrElse(d.classId){""}}
}
