package com.ugaritic.vision

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast

import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var mainImageView: ImageView
    private lateinit var statusLabel: TextView

    private var tfLiteEngine: TFLiteEngine? = null

    private lateinit var cameraExecutor: ExecutorService

    private var cameraProvider: ProcessCameraProvider? = null

    @Volatile
    private var isLiveCamera = true

    @Volatile
    private var currentBitmap: Bitmap? = null

    private var frozenBitmap: Bitmap? = null

    // إعدادات عرض النتائج
    private var showBoxes = true
    private var showConfidence = true
    private var showLabels = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        supportActionBar?.hide()


        // =========================
        // ربط عناصر الواجهة
        // =========================

        mainImageView = findViewById(R.id.mainImageView)

        statusLabel = findViewById(R.id.statusLabel)

        val btnAnalyze =
            findViewById<Button>(R.id.btnAnalyze)

        val btnLiveCamera =
            findViewById<Button>(R.id.btnLiveCamera)

        val btnGallery =
            findViewById<Button>(R.id.btnGallery)

        val chipBoxes =
            findViewById<CheckBox>(R.id.chipBoxes)

        val chipConf =
            findViewById<CheckBox>(R.id.chipConf)

        val chipChars =
            findViewById<CheckBox>(R.id.chipChars)


        // =========================
        // Thread الكاميرا
        // =========================

        cameraExecutor =
            Executors.newSingleThreadExecutor()


        // =========================
        // تحميل موديل الذكاء الاصطناعي
        // =========================

        try {

            tfLiteEngine =
                TFLiteEngine(this)

        } catch (e: Exception) {

            Toast.makeText(
                this,
                "خطأ في تحميل الموديل: ${e.localizedMessage}",
                Toast.LENGTH_LONG
            ).show()
        }


        // =========================
        // إعداد خيارات عرض النتائج
        // =========================

        val filterListener =
            CheckBox.OnCheckedChangeListener { _, _ ->

                showBoxes =
                    chipBoxes.isChecked

                showConfidence =
                    chipConf.isChecked

                showLabels =
                    chipChars.isChecked


                reanalyzeFrozen()
            }


        chipBoxes.setOnCheckedChangeListener(
            filterListener
        )

        chipConf.setOnCheckedChangeListener(
            filterListener
        )

        chipChars.setOnCheckedChangeListener(
            filterListener
        )


        // =========================
        // زر تجميد وتحليل الصورة
        // =========================

        btnAnalyze.setOnClickListener {

            isLiveCamera = false


            currentBitmap?.let { bitmap ->

                // إنشاء نسخة مستقلة من الفريم
                frozenBitmap =
                    bitmap.copy(
                        Bitmap.Config.ARGB_8888,
                        true
                    )


                frozenBitmap?.let { frozen ->

                    cameraExecutor.execute {

                        processAndDisplayImage(
                            frozen
                        )
                    }
                }
            }


            statusLabel.text =
                "ANALYZED FRAME"

            statusLabel.setTextColor(
                Color.parseColor("#FF9919")
            )


            Toast.makeText(
                this,
                "تم تجميد الإطار وتحليله",
                Toast.LENGTH_SHORT
            ).show()
        }


        // =========================
        // زر تشغيل الكاميرا الحية
        // =========================

        btnLiveCamera.setOnClickListener {

            isLiveCamera = true


            statusLabel.text =
                "LIVE CAMERA"

            statusLabel.setTextColor(
                Color.parseColor("#00B4D8")
            )


            startCamera()
        }


        // =========================
        // زر فتح المعرض
        // =========================

        btnGallery.setOnClickListener {

            isLiveCamera = false


            // تحرير الكاميرا
            cameraProvider?.unbindAll()


            galleryLauncher.launch(
                "image/*"
            )
        }


        // =========================
        // تشغيل الكاميرا أو طلب الصلاحية
        // =========================

        if (allPermissionsGranted()) {

            startCamera()

        } else {

            requestPermissions.launch(
                arrayOf(
                    Manifest.permission.CAMERA
                )
            )
        }
    }


    // =====================================================
    // طلب صلاحية الكاميرا
    // =====================================================

    private val requestPermissions =
        registerForActivityResult(

            ActivityResultContracts
                .RequestMultiplePermissions()

        ) { permissions ->

            if (
                permissions[
                    Manifest.permission.CAMERA
                ] == true
            ) {

                startCamera()

            } else {

                Toast.makeText(
                    this,
                    "يجب منح صلاحية الكاميرا لتشغيل التطبيق",
                    Toast.LENGTH_LONG
                ).show()
            }
        }


    // =====================================================
    // فتح المعرض
    // =====================================================

    private val galleryLauncher =
        registerForActivityResult(

            ActivityResultContracts.GetContent()

        ) { uri ->


            uri?.let {

                try {

                    isLiveCamera = false


                    statusLabel.text =
                        "STATIC IMAGE"

                    statusLabel.setTextColor(
                        Color.parseColor("#19CC66")
                    )


                    val inputStream =
                        contentResolver.openInputStream(
                            it
                        )


                    val bitmap =
                        BitmapFactory.decodeStream(
                            inputStream
                        )


                    inputStream?.close()


                    if (bitmap != null) {

                        // حفظ نسخة مستقلة
                        frozenBitmap =
                            bitmap.copy(
                                Bitmap.Config.ARGB_8888,
                                true
                            )


                        currentBitmap =
                            frozenBitmap


                        frozenBitmap?.let { image ->

                            cameraExecutor.execute {

                                processAndDisplayImage(
                                    image
                                )
                            }
                        }

                    } else {

                        Toast.makeText(
                            this,
                            "تعذر قراءة الصورة من المعرض",
                            Toast.LENGTH_SHORT
                        ).show()
                    }


                } catch (e: Exception) {

                    Toast.makeText(
                        this,
                        "خطأ في فتح الصورة: ${e.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }


    // =====================================================
    // تشغيل الكاميرا
    // =====================================================

    private fun startCamera() {


        if (!allPermissionsGranted()) {
            return
        }


        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(
                this
            )


        cameraProviderFuture.addListener({

            try {

                cameraProvider =
                    cameraProviderFuture.get()


                // =====================================
                // Image Analysis
                // =====================================

                val imageAnalyzer =
                    ImageAnalysis.Builder()

                        // الاحتفاظ بأحدث فريم فقط
                        .setBackpressureStrategy(
                            ImageAnalysis
                                .STRATEGY_KEEP_ONLY_LATEST
                        )

                        // مهم جداً:
                        // CameraX يحول الصورة إلى RGBA مباشرة
                        .setOutputImageFormat(
                            ImageAnalysis
                                .OUTPUT_IMAGE_FORMAT_RGBA_8888
                        )

                        .build()

                        .also { analysis ->

                            analysis.setAnalyzer(
                                cameraExecutor
                            ) { imageProxy ->

                                if (isLiveCamera) {

                                    processImageProxy(
                                        imageProxy
                                    )

                                } else {

                                    imageProxy.close()
                                }
                            }
                        }


                // إزالة أي Camera UseCase سابق
                cameraProvider?.unbindAll()


                // =====================================
                // تشغيل الكاميرا الخلفية
                // =====================================

                cameraProvider?.bindToLifecycle(

                    this,

                    CameraSelector.DEFAULT_BACK_CAMERA,

                    imageAnalyzer
                )


            } catch (e: Exception) {

                e.printStackTrace()


                runOnUiThread {

                    Toast.makeText(
                        this,
                        "خطأ في تشغيل الكاميرا: ${e.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

        }, ContextCompat.getMainExecutor(this))
    }


    // =====================================================
    // معالجة Frame القادم من الكاميرا
    // =====================================================

    private fun processImageProxy(
        imageProxy: ImageProxy
    ) {

        try {

            val plane =
                imageProxy.planes[0]


            val buffer =
                plane.buffer


            val pixelStride =
                plane.pixelStride


            val rowStride =
                plane.rowStride


            // حساب Padding الموجود في بعض الأجهزة
            val rowPadding =
                rowStride -
                        pixelStride *
                        imageProxy.width


            val paddedWidth =
                imageProxy.width +
                        rowPadding / pixelStride


            // إنشاء Bitmap بالحجم الكامل
            val paddedBitmap =
                Bitmap.createBitmap(

                    paddedWidth,

                    imageProxy.height,

                    Bitmap.Config.ARGB_8888
                )


            buffer.rewind()


            paddedBitmap.copyPixelsFromBuffer(
                buffer
            )


            // قص الـ Padding
            val bitmap =
                Bitmap.createBitmap(

                    paddedBitmap,

                    0,
                    0,

                    imageProxy.width,

                    imageProxy.height
                )


            // =====================================
            // تدوير الصورة حسب اتجاه الهاتف
            // =====================================

            val rotationDegrees =
                imageProxy.imageInfo.rotationDegrees


            val finalBitmap =

                if (rotationDegrees != 0) {


                    val matrix =
                        Matrix().apply {

                            postRotate(
                                rotationDegrees.toFloat()
                            )
                        }


                    Bitmap.createBitmap(

                        bitmap,

                        0,
                        0,

                        bitmap.width,

                        bitmap.height,

                        matrix,

                        true
                    )


                } else {

                    bitmap
                }


            // إرسال الصورة للذكاء الاصطناعي
            processAndDisplayImage(
                finalBitmap
            )


        } catch (e: Exception) {

            e.printStackTrace()

        } finally {

            // مهم جداً لمنع تجمد الكاميرا
            imageProxy.close()
        }
    }


    // =====================================================
    // تشغيل الذكاء الاصطناعي
    // =====================================================

    private fun processAndDisplayImage(
        bitmap: Bitmap?
    ) {

        if (
            bitmap == null ||
            tfLiteEngine == null
        ) {
            return
        }


        // حفظ آخر Frame
        currentBitmap =
            bitmap


        try {

            // =====================================
            // تشغيل TFLite
            // =====================================

            val engineOutput =
                tfLiteEngine!!.run(
                    bitmap
                )


            // =====================================
            // فك مخرجات YOLO
            // =====================================

            val rawDetections =
                YoloPostProcessor.decode(

                    data =
                        engineOutput.data,

                    shape =
                        engineOutput.shape,

                    conf = 0.4f,

                    iou = 0.5f,

                    maxDet = 100
                )


            // =====================================
            // إعادة الإحداثيات للحجم الأصلي
            // =====================================

            val finalDetections =
                rawDetections.map {

                    detection ->

                    Letterbox.undo(

                        detection,

                        engineOutput
                            .letterboxResult
                    )
                }


            // =====================================
            // استخراج النص الأوغاريتي
            // =====================================

            val extractedText =
                UgariticTextExtractor.extract(
                    finalDetections
                )


            // =====================================
            // رسم النتائج
            // =====================================

            val annotatedBitmap =
                drawDetectionsOnBitmap(

                    bitmap,

                    finalDetections
                )


            // =====================================
            // تحديث الواجهة
            // =====================================

            runOnUiThread {

                mainImageView.setImageBitmap(
                    annotatedBitmap
                )


                if (
                    extractedText.isNotBlank()
                ) {

                    statusLabel.text =
                        "النص: $extractedText"
                }
            }


        } catch (e: Exception) {

            e.printStackTrace()
        }
    }


    // =====================================================
    // رسم مربعات الكشف
    // =====================================================

    private fun drawDetectionsOnBitmap(

        source: Bitmap,

        detections: List<Detection>

    ): Bitmap {


        val mutableBitmap =
            source.copy(

                Bitmap.Config.ARGB_8888,

                true
            )


        val canvas =
            Canvas(
                mutableBitmap
            )


        // =====================================
        // رسم المربعات
        // =====================================

        val boxPaint =
            Paint().apply {

                color =
                    Color.GREEN

                style =
                    Paint.Style.STROKE

                strokeWidth =
                    4f

                isAntiAlias =
                    true
            }


        // =====================================
        // رسم النص
        // =====================================

        val textPaint =
            Paint().apply {

                color =
                    Color.RED

                textSize =
                    36f

                isAntiAlias =
                    true
            }


        // =====================================
        // رسم كل Detection
        // =====================================

        for (det in detections) {


            if (showBoxes) {

                canvas.drawRect(

                    det.x1,

                    det.y1,

                    det.x2,

                    det.y2,

                    boxPaint
                )
            }


            // =====================================
            // الحصول على الحرف
            // =====================================

            val charStr =

                if (
                    det.classId in
                    Constants
                        .UGARITIC_CHARS
                        .indices
                ) {

                    Constants
                        .UGARITIC_CHARS[
                            det.classId
                        ]

                } else {

                    "?"
                }


            // =====================================
            // بناء النص
            // =====================================

            val displayText =
                buildString {

                    if (showLabels) {

                        append(
                            "$charStr "
                        )
                    }


                    if (showConfidence) {

                        append(

                            String.format(
                                "%.2f",
                                det.confidence
                            )
                        )
                    }
                }


            // =====================================
            // رسم النص
            // =====================================

            if (
                displayText.isNotEmpty()
            ) {

                canvas.drawText(

                    displayText,

                    det.x1,

                    maxOf(
                        det.y1 - 10f,
                        30f
                    ),

                    textPaint
                )
            }
        }


        return mutableBitmap
    }


    // =====================================================
    // إعادة تحليل الصورة المجمدة
    // =====================================================

    private fun reanalyzeFrozen() {

        if (
            !isLiveCamera &&
            frozenBitmap != null
        ) {

            frozenBitmap?.let {

                cameraExecutor.execute {

                    processAndDisplayImage(
                        it
                    )
                }
            }
        }
    }


    // =====================================================
    // فحص صلاحية الكاميرا
    // =====================================================

    private fun allPermissionsGranted(): Boolean {

        return ContextCompat.checkSelfPermission(

            this,

            Manifest.permission.CAMERA

        ) == PackageManager.PERMISSION_GRANTED
    }


    // =====================================================
    // إغلاق الموارد
    // =====================================================

    override fun onDestroy() {

        super.onDestroy()


        // إغلاق الكاميرا
        cameraProvider?.unbindAll()


        // إغلاق Thread
        cameraExecutor.shutdown()


        // إغلاق TFLite
        tfLiteEngine?.close()
    }
}
