# Ugaritic Vision AI Studio — Android

هذا مشروع Android Native مبني بـ Kotlin + XML + CameraX + TensorFlow Lite، بدون Python/Kivy/Torch.

## مهم قبل البناء
ضع نموذجك الحقيقي هنا:
`app/src/main/assets/bestfinetuneing.tflite`

والخط الاختياري هنا:
`app/src/main/assets/fonts/NotoSansUgaritic-Regular.ttf`

لا يمكن تضمين النموذج أو الخط داخل هذه الحزمة لأن الملفين الأصليين غير مرفوعين هنا.

## البناء بدون Android Studio
ارفع المجلد إلى GitHub، ثم افتح Actions وشغّل `Build Android APK`. ستجد APK في Artifacts.

## ملاحظة النموذج
المعالج يدعم مداخل TFLite الشائعة NHWC/NCHW و FLOAT32/UINT8/INT8، ويطبع الخطأ إذا كان شكل الإخراج غير متوافق. كود YOLO الحالي يفترض إخراجًا decoded بعدد 35 قناة (4 bbox + 31 class). إذا كان نموذجك يخرج raw YOLOv8 DFL، يجب استخدام decoder الخاص بتصدير نموذجك.
