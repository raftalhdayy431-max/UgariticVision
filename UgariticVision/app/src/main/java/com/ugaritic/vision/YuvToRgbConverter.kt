package com.ugaritic.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.media.Image
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicYuvToRGB
import androidx.camera.core.ImageProxy

@Suppress("DEPRECATION")
class YuvToRgbConverter(context: Context) {

    private val rs: RenderScript =
        RenderScript.create(context)

    private val scriptYuvToRgb:
            ScriptIntrinsicYuvToRGB =
        ScriptIntrinsicYuvToRGB.create(
            rs,
            Element.U8_4(rs)
        )

    private var yuvByteArray =
        ByteArray(0)

    private var inputAllocation: Allocation? =
        null

    private var outputAllocation: Allocation? =
        null


    @Synchronized
    fun yuvToRgb(
        image: ImageProxy,
        outputBitmap: Bitmap
    ) {

        val imageSize =
            image.width * image.height


        if (
            yuvByteArray.size !=
            imageSize * ImageFormat.getBitsPerPixel(
                ImageFormat.YUV_420_888
            ) / 8
        ) {

            yuvByteArray =
                ByteArray(
                    imageSize *
                    ImageFormat.getBitsPerPixel(
                        ImageFormat.YUV_420_888
                    ) / 8
                )
        }


        image.toByteArray(
            yuvByteArray
        )


        if (
            inputAllocation == null
        ) {

            val yuvType =
                android.renderscript.Type.Builder(
                    rs,
                    Element.U8(rs)
                )
                    .setX(
                        yuvByteArray.size
                    )


            inputAllocation =
                Allocation.createTyped(
                    rs,
                    yuvType.create(),
                    Allocation.USAGE_SCRIPT
                )


            outputAllocation =
                Allocation.createFromBitmap(
                    rs,
                    outputBitmap
                )
        }


        inputAllocation?.copyFrom(
            yuvByteArray
        )


        scriptYuvToRgb.setInput(
            inputAllocation
        )


        scriptYuvToRgb.forEach(
            outputAllocation
        )


        outputAllocation?.copyTo(
            outputBitmap
        )
    }


    private fun ImageProxy.toByteArray(
        output: ByteArray
    ) {

        assert(
            planes.size == 3
        )


        val image = this.image
            ?: return


        image.planes.forEachIndexed {

            planeIndex,

            plane ->

            val buffer =
                plane.buffer

            val rowStride =
                plane.rowStride

            val pixelStride =
                plane.pixelStride


            val width =
                if (planeIndex == 0)
                    image.width
                else
                    image.width / 2


            val height =
                if (planeIndex == 0)
                    image.height
                else
                    image.height / 2


            var outputOffset =

                when (planeIndex) {

                    0 -> 0

                    1 ->
                        image.width *
                        image.height

                    else ->
                        image.width *
                        image.height +
                        image.width *
                        image.height / 4
                }


            for (row in 0 until height) {

                var inputOffset =
                    row * rowStride


                for (col in 0 until width) {

                    output[outputOffset++] =
                        buffer.get(inputOffset)

                    inputOffset += pixelStride
                }
            }
        }
    }


    fun close() {

        inputAllocation?.destroy()

        outputAllocation?.destroy()

        scriptYuvToRgb.destroy()

        rs.destroy()
    }
}
