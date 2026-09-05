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

    private var showBoxes = true
    private var showConfidence = true
    private var showLabels = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        supportActionBar?.hide()

        mainImageView = findViewById(R.id.mainImageView)
        statusLabel = findViewById(R.id.statusLabel)
        val btnAnalyze = findViewById<Button>(R.id.btnAnalyze)
        val btnLiveCamera = findViewById<Button>(R.id.btnLiveCamera)
        val btnGallery = findViewById<Button>(R.id.btnGallery)
        val chipBoxes = findViewById<CheckBox>(R.id.chipBoxes)
        val chipConf = findViewById<CheckBox>(R.id.chipConf)
        val chipChars = findViewById<CheckBox>(R.id.chipChars)

        cameraExecutor = Executors.newSingleThreadExecutor()

        try {
            tfLiteEngine = TFLiteEngine(this)
        } catch (e: Exception) {
            Toast.makeText(this, "خطأ في تحميل الموديل: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }

        val filterListener = CheckBox.OnCheckedChangeListener { _, _ ->
            showBoxes = chipBoxes.isChecked
            showConfidence = chipConf.isChecked
            showLabels = chipChars.isChecked
            reanalyzeFrozen()
        }

        chipBoxes.setOnCheckedChangeListener(filterListener)
        chipConf.setOnCheckedChangeListener(filterListener)
        chipChars.setOnCheckedChangeListener(filterListener)

        btnAnalyze.setOnClickListener {
            isLiveCamera = false
            currentBitmap?.let { bitmap ->
                frozenBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                frozenBitmap?.let { frozen ->
                    cameraExecutor.execute {
                        processAndDisplayImage(frozen)
                    }
                }
            }
            statusLabel.text = "ANALYZED FRAME"
            statusLabel.setTextColor(Color.parseColor("#FF9919"))
            Toast.makeText(this, "تم تجميد الإطار وتحليله", Toast.LENGTH_SHORT).show()
        }

        btnLiveCamera.setOnClickListener {
            isLiveCamera = true
            statusLabel.text = "LIVE CAMERA"
            statusLabel.setTextColor(Color.parseColor("#00B4D8"))
            startCamera()
        }

        btnGallery.setOnClickListener {
            isLiveCamera = false
            cameraProvider?.unbindAll()
            galleryLauncher.launch("image/*")
        }

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions.launch(arrayOf(Manifest.permission.CAMERA))
        }
    }

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.CAMERA] == true) {
            startCamera()
        } else {
            Toast.makeText(this, "يجب منح صلاحية الكاميرا لتشغيل التطبيق", Toast.LENGTH_LONG).show()
        }
    }

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                isLiveCamera = false
                statusLabel.text = "STATIC IMAGE"
                statusLabel.setTextColor(Color.parseColor("#19CC66"))

                val inputStream = contentResolver.openInputStream(it)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                if (bitmap != null) {
                    frozenBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                    currentBitmap = frozenBitmap
                    frozenBitmap?.let { image ->
                        cameraExecutor.execute {
                            processAndDisplayImage(image)
                        }
                    }
                } else {
                    Toast.makeText(this, "تعذر قراءة الصورة من المعرض", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "خطأ في فتح الصورة: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startCamera() {
        if (!allPermissionsGranted()) return

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()

                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                            if (isLiveCamera) {
                                processImageProxy(imageProxy)
                            } else {
                                imageProxy.close()
                            }
                        }
                    }

                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    imageAnalyzer
                )
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this, "خطأ في تشغيل الكاميرا: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun processImageProxy(imageProxy: ImageProxy) {
        try {
            val plane = imageProxy.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * imageProxy.width
            val paddedWidth = imageProxy.width + rowPadding / pixelStride

            val paddedBitmap = Bitmap.createBitmap(paddedWidth, imageProxy.height, Bitmap.Config.ARGB_8888)
            buffer.rewind()
            paddedBitmap.copyPixelsFromBuffer(buffer)

            val bitmap = Bitmap.createBitmap(paddedBitmap, 0, 0, imageProxy.width, imageProxy.height)
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees

            val finalBitmap = if (rotationDegrees != 0) {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } else {
                bitmap
            }

            processAndDisplayImage(finalBitmap)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            imageProxy.close()
        }
    }

    private fun processAndDisplayImage(bitmap: Bitmap?) {
        if (bitmap == null || tfLiteEngine == null) return
        currentBitmap = bitmap

        try {
            val engineOutput = tfLiteEngine!!.run(bitmap)
            val rawDetections = YoloPostProcessor.decode(
                data = engineOutput.data,
                shape = engineOutput.shape,
                conf = 0.4f,
                iou = 0.5f,
                maxDet = 100
            )

            val finalDetections = rawDetections.map { detection ->
                Letterbox.undo(detection, engineOutput.letterboxResult)
            }

            val extractedText = UgariticTextExtractor.extract(finalDetections)
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
            isAntiAlias = true
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
            frozenBitmap?.let {
                cameraExecutor.execute { processAndDisplayImage(it) }
            }
        }
    }

    private fun allPermissionsGranted(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
        tfLiteEngine?.close()
    }
}
