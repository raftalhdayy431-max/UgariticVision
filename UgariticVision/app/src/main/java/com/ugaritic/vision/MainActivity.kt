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
    private var isLiveCamera = true
    private var frozenBitmap: Bitmap? = null
    private var currentBitmap: Bitmap? = null

    // متغيرات الفلاتر
    private var showBoxes = true
    private var showConfidence = true
    private var showLabels = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        supportActionBar?.hide()

        // ربط العناصر
        mainImageView = findViewById(R.id.mainImageView)
        statusLabel = findViewById(R.id.statusLabel)
        val btnAnalyze = findViewById<Button>(R.id.btnAnalyze)
        val btnLiveCamera = findViewById<Button>(R.id.btnLiveCamera)
        val btnGallery = findViewById<Button>(R.id.btnGallery)
        
        val chipBoxes = findViewById<CheckBox>(R.id.chipBoxes)
        val chipConf = findViewById<CheckBox>(R.id.chipConf)
        val chipChars = findViewById<CheckBox>(R.id.chipChars)

        cameraExecutor = Executors.newSingleThreadExecutor()

        // التهيئة الآمنة للموديل لمنع الخروج المفاجئ وإظهار السبب الحقيقي إن وجد
        try {
            tfLiteEngine = TFLiteEngine(this)
        } catch (e: Exception) {
            Toast.makeText(this, "خطأ في تهيئة الموديل: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            statusLabel.text = "فشل تحميل الموديل!"
            statusLabel.setTextColor(Color.RED)
        }

        // أحداث الفلاتر
        chipBoxes.setOnCheckedChangeListener { _, isChecked -> showBoxes = isChecked; reanalyzeFrozen() }
        chipConf.setOnCheckedChangeListener { _, isChecked -> showConfidence = isChecked; reanalyzeFrozen() }
        chipChars.setOnCheckedChangeListener { _, isChecked -> showLabels = isChecked; reanalyzeFrozen() }

        // أحداث الأزرار
        btnAnalyze.setOnClickListener {
            isLiveCamera = false
            frozenBitmap = currentBitmap
            statusLabel.text = "ANALYZED FRAME"
            statusLabel.setTextColor(Color.parseColor("#FF9919"))
            Toast.makeText(this, "Frame captured & analyzed", Toast.LENGTH_SHORT).show()
        }

        btnLiveCamera.setOnClickListener {
            isLiveCamera = true
            statusLabel.text = "LIVE CAMERA"
            statusLabel.setTextColor(Color.parseColor("#00B4D8"))
            startCamera()
        }

        btnGallery.setOnClickListener {
            galleryLauncher.launch("image/*")
        }

        // طلب صلاحيات الكاميرا
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions.launch(arrayOf(Manifest.permission.CAMERA))
        }
    }

    private val requestPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions[Manifest.permission.CAMERA] == true) {
            startCamera()
        } else {
            Toast.makeText(this, "Permission not granted.", Toast.LENGTH_SHORT).show()
        }
    }

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            isLiveCamera = false
            statusLabel.text = "STATIC IMAGE"
            statusLabel.setTextColor(Color.parseColor("#19CC66"))
            val inputStream = contentResolver.openInputStream(it)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            frozenBitmap = bitmap
            currentBitmap = bitmap
            processAndDisplayImage(bitmap)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        if (isLiveCamera) {
                            processImageProxy(imageProxy)
                        } else {
                            imageProxy.close()
                        }
                    }
                }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, imageAnalyzer)
            } catch (exc: Exception) {
                // خطأ كاميرا صامت
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun processImageProxy(imageProxy: ImageProxy) {
        val bitmap = imageProxy.toBitmap()
        if (bitmap != null) {
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val rotatedBitmap = if (rotationDegrees != 0) {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } else {
                bitmap
            }
            processAndDisplayImage(rotatedBitmap)
        }
        imageProxy.close()
    }

    private fun ImageProxy.toBitmap(): Bitmap? {
        val yBuffer = planes[0].buffer
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = android.graphics.YuvImage(nv21, android.graphics.ImageFormat.NV21, width, height, null)
        val out = java.io.ByteArrayOutputStream()
        yuvImage.compressToJpeg(android.graphics.Rect(0, 0, width, height), 90, out)
        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    private fun processAndDisplayImage(bitmap: Bitmap?) {
        if (bitmap == null || tfLiteEngine == null) return
        
        currentBitmap = bitmap

        try {
            // 1. تشغيل نموذج TFLite والحصول على النتائج
            val engineOutput = tfLiteEngine!!.run(bitmap)

            // 2. فك التشفير واستخراج المربعات والحروف
            val rawDetections = YoloPostProcessor.decode(
                data = engineOutput.data,
                shape = engineOutput.shape,
                conf = 0.4f,
                iou = 0.5f,
                maxDet = 100
            )

            // 3. إعادة الإحداثيات لمقاس الصورة الأصلي
            val finalDetections = rawDetections.map { detection ->
                Letterbox.undo(detection, engineOutput.letterboxResult)
            }

            // 4. استخراج النص الأغاريتي
            val extractedText = UgariticTextExtractor.extract(finalDetections)

            // 5. رسم المربعات والرموز
            val annotatedBitmap = drawDetectionsOnBitmap(bitmap, finalDetections)

            runOnUiThread {
                mainImageView.setImageBitmap(annotatedBitmap)
                if (extractedText.isNotBlank()) {
                    statusLabel.text = "النص: $extractedText"
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun drawDetectionsOnBitmap(source: Bitmap, detections: List<Detection>): Bitmap {
        val mutableBitmap = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)
        
        val boxPaint = Paint().apply {
            color = Color.GREEN
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }

        val textPaint = Paint().apply {
            color = Color.RED
            textSize = 36f
            isAntiAlias = true
        }

        for (det in detections) {
            if (showBoxes) {
                canvas.drawRect(det.x1, det.y1, det.x2, det.y2, boxPaint)
            }

            val charStr = if (det.classId in Constants.UGARITIC_CHARS.indices) {
                Constants.UGARITIC_CHARS[det.classId]
            } else {
                "?"
            }

            val displayText = buildString {
                if (showLabels) append("$charStr ")
                if (showConfidence) append(String.format("%.2f", det.confidence))
            }

            if (displayText.isNotEmpty()) {
                canvas.drawText(displayText, det.x1, maxOf(det.y1 - 10f, 30f), textPaint)
            }
        }

        return mutableBitmap
    }

    private fun reanalyzeFrozen() {
        if (!isLiveCamera && frozenBitmap != null) {
            processAndDisplayImage(frozenBitmap)
        }
    }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        tfLiteEngine?.close()
    }
}
