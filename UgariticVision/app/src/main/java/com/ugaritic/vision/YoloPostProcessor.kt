package com.ugaritic.vision
import kotlin.math.max
import kotlin.math.min

object YoloPostProcessor {
    fun decode(data:FloatArray,shape:IntArray,conf:Float,iou:Float,maxDet:Int):List<Detection>{
        if(shape.isEmpty()) return emptyList()
        val channels=Constants.NUM_CLASSES+4
        val rows=when {shape.lastOrNull()==channels->shape.last(); shape.getOrNull(shape.size-2)==channels->shape[shape.size-2]; else->return emptyList()}
        val candidates=ArrayList<Detection>()
        fun at(r:Int,c:Int):Float=if(shape.lastOrNull()==channels)data[r*channels+c] else data[c*rows+r]
        for(r in 0 until rows){var best=-1;var bs=0f;for(c in 0 until Constants.NUM_CLASSES){val s=at(r,4+c);if(s>bs){bs=s;best=c}};if(best>=0&&bs>=conf){val cx=at(r,0);val cy=at(r,1);val w=at(r,2);val h=at(r,3);candidates.add(Detection(best,bs,cx-w/2,cy-h/2,cx+w/2,cy+h/2))}}
        candidates.sortByDescending{it.confidence};val keep=ArrayList<Detection>();for(d in candidates){if(keep.size>=maxDet)break;if(keep.none{it.classId==d.classId&&iou(it,d)>iou})keep.add(d)};return keep
    }
    private fun iou(a:Detection,b:Detection):Float{val x1=max(a.x1,b.x1);val y1=max(a.y1,b.y1);val x2=min(a.x2,b.x2);val y2=min(a.y2,b.y2);val inter=max(0f,x2-x1)*max(0f,y2-y1);val ua=(a.x2-a.x1)*(a.y2-a.y1);val ub=(b.x2-b.x1)*(b.y2-b.y1);return inter/(ua+ub-inter+1e-6f)}
}
