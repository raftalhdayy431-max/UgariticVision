package com.ugaritic.vision
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix

data class LetterboxResult(val bitmap:Bitmap,val scale:Float,val padX:Float,val padY:Float, val originalWidth:Int,val originalHeight:Int)
object Letterbox {
    fun apply(src:Bitmap,size:Int=Constants.INPUT_SIZE):LetterboxResult {
        val scale=minOf(size.toFloat()/src.width,size.toFloat()/src.height)
        val nw=(src.width*scale).toInt().coerceAtLeast(1); val nh=(src.height*scale).toInt().coerceAtLeast(1)
        val out=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888); val c=Canvas(out); c.drawColor(Color.rgb(114,114,114))
        val dx=(size-nw)/2f; val dy=(size-nh)/2f
        c.drawBitmap(src,null,android.graphics.RectF(dx,dy,dx+nw,dy+nh),null)
        return LetterboxResult(out,scale,dx,dy,src.width,src.height)
    }
    fun undo(d:Detection,r:LetterboxResult):Detection {
        val x1=((d.x1-r.padX)/r.scale).coerceIn(0f,r.originalWidth.toFloat())
        val y1=((d.y1-r.padY)/r.scale).coerceIn(0f,r.originalHeight.toFloat())
        val x2=((d.x2-r.padX)/r.scale).coerceIn(0f,r.originalWidth.toFloat())
        val y2=((d.y2-r.padY)/r.scale).coerceIn(0f,r.originalHeight.toFloat())
        return d.copy(x1=x1,y1=y1,x2=x2,y2=y2)
    }
}
