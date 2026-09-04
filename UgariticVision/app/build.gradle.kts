plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

android {
    namespace = "com.ugaritic.vision"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.ugaritic.vision"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }
    buildTypes {
        release { isMinifyEnabled = false; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    androidResources { noCompress += "tflite" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.activity:activity-ktx:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.3")
    val cameraX = "1.6.2"
    implementation("androidx.camera:camera-core:$cameraX")
    implementation("androidx.camera:camera-camera2:$cameraX")
    implementation("androidx.camera:camera-lifecycle:$cameraX")
    implementation("androidx.camera:camera-view:$cameraX")
    implementation("org.tensorflow:tensorflow-lite:2.17.0")
    // مكتبة الواجهات (مهمة جداً لملف التصميم)
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    
    // مكتبة مساعدة لـ TFLite (اختيارية لكنها تفيد جداً في معالجة الصور)
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
}
