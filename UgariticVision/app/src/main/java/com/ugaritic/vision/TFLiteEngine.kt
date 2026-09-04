package com.ugaritic.vision

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

class TFLiteEngine(context:Context) : AutoCloseable {
    private val interpreter:Interpreter
    init {
        val bytes=context.assets.open(Constants.MODEL_FILE).use{it.readBytes()}
        interpreter=Interpreter(ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).apply{put(bytes);rewind()})
    }
    data class Output(val data:FloatArray,val shape:IntArray)
    fun run(bitmap:Bitmap):Output {
        val t=interpreter.getInputTensor(0); val shape=t.shape(); val type=t.dataType()
        val h=if(shape.size==4 && shape[1]==Constants.INPUT_SIZE) shape[1] else Constants.INPUT_SIZE
        val w=if(shape.size==4 && shape[1]==Constants.INPUT_SIZE) shape[2] else Constants.INPUT_SIZE
        val resized=Bitmap.createScaledBitmap(bitmap,w,h,true)
        val pixels=IntArray(w*h); resized.getPixels(pixels,0,w,0,0,w,h)
        val input=when(type){
            DataType.FLOAT32 -> FloatArray(w*h*3).also{fillFloat(it,pixels,w,h,shape)}
            DataType.UINT8 -> ByteArray(w*h*3).also{fillByte(it,pixels,w,h,shape,128f,1f)}
            DataType.INT8 -> ByteArray(w*h*3).also{fillByte(it,pixels,w,h,shape,0f,127f)}
            else -> error("Unsupported input type: $type")
        }
        val outT=interpreter.getOutputTensor(0); val outShape=outT.shape(); val n=outShape.fold(1){a,b->a*b}
        val out=FloatArray(n)
        when(outT.dataType()){
            DataType.FLOAT32 -> interpreter.run(input,out)
            DataType.UINT8,DataType.INT8 -> { val raw=ByteArray(n); interpreter.run(input,raw); val q=outT.quantizationParams(); for(i in raw.indices) out[i]=(raw[i].toInt() and 255)*q.scale+q.zeroPoint }
            else -> error("Unsupported output type: ${outT.dataType()}")
        }
        return Output(out,outShape)
    }
    private fun fillFloat(dst:FloatArray,p:IntArray,w:Int,h:Int,s:IntArray){ var k=0; val nchw=s.size==4&&s[1]==3
        if(nchw){for(c in 0..2)for(y in 0 until h)for(x in 0 until w){val v=p[y*w+x];dst[k++]=((if(c==0)v shr 16 and 255 else if(c==1)v shr 8 and 255 else v and 255))/255f}}
        else for(v in p){dst[k++]=(v shr 16 and 255)/255f;dst[k++]=(v shr 8 and 255)/255f;dst[k++]=(v and 255)/255f}
    }
    private fun fillByte(dst:ByteArray,p:IntArray,w:Int,h:Int,s:IntArray,zero:Float,scale:Float){var k=0; val nchw=s.size==4&&s[1]==3
        fun q(v:Int):Byte{val x=((v/255f)*scale+zero).toInt().coerceIn(-128,127);return x.toByte()}
        if(nchw)for(c in 0..2)for(v in p){val z=if(c==0)v shr 16 and 255 else if(c==1)v shr 8 and 255 else v and 255;dst[k++]=q(z)} else for(v in p){dst[k++]=q(v shr 16 and 255);dst[k++]=q(v shr 8 and 255);dst[k++]=q(v and 255)}
    }
    override fun close(){interpreter.close()}
}
