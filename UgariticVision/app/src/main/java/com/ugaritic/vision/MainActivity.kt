package com.ugaritic.vision

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity:AppCompatActivity(){
    private lateinit var preview:PreviewView; private lateinit var overlay:AnnotationView; private lateinit var result:TextView; private var engine:TFLiteEngine?=null
    private val executor=Executors.newSingleThreadExecutor(); private val busy=AtomicBoolean(false); private var frozen=false; private var fps=5; private var lastRun=0L
    private val permission=registerForActivityResult(ActivityResultContracts.RequestPermission()){if(it)startCamera()}
    private val gallery=registerForActivityResult(ActivityResultContracts.GetContent()){uri->uri?.let{contentResolver.openInputStream(it)?.use{ins->val b=android.graphics.BitmapFactory.decodeStream(ins);if(b!=null)runImage(b)}}}
    override fun onCreate(b:Bundle?){super.onCreate(b);setContentView(R.layout.activity_main);preview=findViewById(R.id.preview);overlay=findViewById(R.id.overlay);result=findViewById(R.id.result_text)
        findViewById<Button>(R.id.gallery_btn).setOnClickListener{gallery.launch("image/*")};findViewById<Button>(R.id.freeze_btn).setOnClickListener{frozen=!frozen;it as Button;it.text=if(frozen)"استئناف" else "تجميد"};findViewById<Button>(R.id.copy_btn).setOnClickListener{android.content.ClipboardManager::class.java; val cm=getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager;cm.setPrimaryClip(android.content.ClipData.newPlainText("Ugaritic",result.text))}
        try{engine=TFLiteEngine(this);result.text="النموذج جاهز"}catch(e:Exception){result.text="تعذر تحميل النموذج: ${e.message}"}
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)startCamera() else permission.launch(Manifest.permission.CAMERA)
    }
    private fun startCamera(){val f=ProcessCameraProvider.getInstance(this);f.addListener({val p=f.get();val previewUse=Preview.Builder().build().also{it.surfaceProvider=preview.surfaceProvider};val analysis=ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();analysis.setAnalyzer(executor){img->if(!frozen&&System.currentTimeMillis()-lastRun>=1000L/fps) {lastRun=System.currentTimeMillis();analyze(img)} else img.close()};p.unbindAll();p.bindToLifecycle(this,CameraSelector.DEFAULT_BACK_CAMERA,previewUse,analysis)},ContextCompat.getMainExecutor(this))}
    private fun analyze(image:ImageProxy){if(!busy.compareAndSet(false,true)){image.close();return};try{val b=image.toBitmap();runImage(b)}catch(e:Exception){runOnUiThread{result.text="خطأ: ${e.message}"}}finally{busy.set(false);image.close()}}
    private fun runImage(bitmap:Bitmap){Thread{try{val lb=Letterbox.apply(bitmap);val out=engine?.run(lb.bitmap)?:return@Thread;val ds=YoloPostProcessor.decode(out.data,out.shape,.5f,.7f,40).map{Letterbox.undo(it,lb)};val text=UgariticTextExtractor.extract(ds);runOnUiThread{overlay.setDetections(ds,bitmap.width,bitmap.height);result.text=if(text.isBlank())"لم يتم اكتشاف حروف أوغاريتية" else text}}catch(e:Exception){runOnUiThread{result.text="خطأ في الاستدلال: ${e.message}"}}}.start()}
    override fun onDestroy(){super.onDestroy();executor.shutdown();engine?.close()}
}
