package com.ugaritic.vision

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
    private lateinit var tfLiteEngine: TFLiteEngine // الكلاس الموجود لديك مسبقاً
    
    private lateinit var cameraExecutor: ExecutorService
    private var isLiveCamera = true
    private var frozenBitmap: Bitmap? = null

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

        // التهيئة
        tfLiteEngine = TFLiteEngine(this)
        cameraExecutor = Executors.newSingleThreadExecutor()

        // أحداث الفلاتر
        chipBoxes.setOnCheckedChangeListener { _, isChecked -> showBoxes = isChecked; reanalyzeFrozen() }
        chipConf.setOnCheckedChangeListener { _, isChecked -> showConfidence = isChecked; reanalyzeFrozen() }
        chipChars.setOnCheckedChangeListener { _, isChecked -> showLabels = isChecked; reanalyzeFrozen() }

        // أحداث الأزرار
        btnAnalyze.setOnClickListener {
            isLiveCamera = false
            statusLabel.text = "ANALYZED FRAME"
            statusLabel.setTextColor(android.graphics.Color.parseColor("#FF9919"))
            Toast.makeText(this, "Frame captured & analyzed", Toast.LENGTH_SHORT).show()
        }

        btnLiveCamera.setOnClickListener {
            isLiveCamera = true
            statusLabel.text = "LIVE CAMERA"
            statusLabel.setTextColor(android.graphics.Color.parseColor("#00B4D8"))
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
            statusLabel.setTextColor(android.graphics.Color.parseColor("#19CC66"))
            val inputStream = contentResolver.openInputStream(it)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            frozenBitmap = bitmap
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
                // خطأ في تهيئة الكاميرا
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private fun processImageProxy(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            // تحويل ImageProxy إلى Bitmap (يجب استخدام دالة مساعدة هنا)
            // val bitmap = mediaImage.toBitmap() // افتراض وجود دالة التحويل
            
            // محاكاة للمعالجة للتبسيط في هذا الكود:
            // frozenBitmap = bitmap
            // processAndDisplayImage(bitmap)
        }
        imageProxy.close()
    }

    private fun processAndDisplayImage(bitmap: Bitmap?) {
        if (bitmap == null) return
        
        // هنا يتم استدعاء كلاس الـ Yolo و الـ TextExtractor الخاص بك
        // val annotatedBitmap = tfLiteEngine.detectAndDraw(bitmap, showBoxes, showConfidence, showLabels)
        
        runOnUiThread {
            // mainImageView.setImageBitmap(annotatedBitmap)
            mainImageView.setImageBitmap(bitmap) // مؤقتاً لعرض الصورة
        }
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
    }
}
