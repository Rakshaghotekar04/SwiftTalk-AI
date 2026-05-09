
package com.example.swifttalkai
import java.util.concurrent.Executors
import android.annotation.SuppressLint
import android.content.Intent
import android.speech.RecognizerIntent
import org.tensorflow.lite.task.vision.detector.Detection
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.io.FileOutputStream
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import android.speech.tts.UtteranceProgressListener

var latestBitmap: Bitmap? = null
var lastSpokenName: String? = null
var lastSpeakTime = 0L

val detector = FaceDetection.getClient()

lateinit var personDetector: PersonDetector
lateinit var faceModel: FaceNetModel
private var tts: TextToSpeech? = null
private var lastDetectionTime: Long = 0

@SuppressLint("UnsafeOptInUsageError")
@Composable
fun CameraScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        personDetector = PersonDetector(context)
        faceModel = FaceNetModel(context)
    }

    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }


    LaunchedEffect(Unit) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = java.util.Locale.US
                tts?.setSpeechRate(0.9f)
                tts?.setPitch(1.0f)

                Log.d("TTS", "Initialized SUCCESS")
            } else {
                Log.e("TTS", "Initialization FAILED")
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            tts?.shutdown()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->

                val view = PreviewView(ctx)
                previewView = view

                val cameraProviderFuture =
                    ProcessCameraProvider.getInstance(ctx)

                cameraProviderFuture.addListener({

                    cameraProvider = cameraProviderFuture.get()

                    bindCamera(
                        ctx,
                        view,
                        cameraProvider!!,
                        lensFacing,
                        lifecycleOwner
                    )

                }, ContextCompat.getMainExecutor(ctx))

                view
            }
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {

            // Flip camera
            IconButton(onClick = {

                lensFacing =
                    if (lensFacing == CameraSelector.LENS_FACING_BACK)
                        CameraSelector.LENS_FACING_FRONT
                    else
                        CameraSelector.LENS_FACING_BACK

                cameraProvider?.let {
                    previewView?.let { view ->
                        bindCamera(context, view, it, lensFacing,lifecycleOwner)
                    }
                }

            }) {
                Icon(Icons.Default.Cameraswitch, contentDescription = "Flip")
            }

            // Add face
            IconButton(onClick = {
                addFace()
            }) {
                Icon(Icons.Default.PersonAdd, contentDescription = "Add Face")
            }

            // Mic
            IconButton(onClick = {
                startVoiceRecognition(context)
            }) {
                Icon(Icons.Default.Mic, contentDescription = "Mic")
            }
            IconButton(onClick = {
                speakPersonName("Hello Raksha")
            }) {
                Icon(Icons.Default.Mic, contentDescription = "Test Voice")
            }
            Log.d("TEST", "Button clicked")

        }
    }
}
fun bindCamera(
    context: Context,
    previewView: PreviewView,
    cameraProvider: ProcessCameraProvider,
    lensFacing: Int,
    lifecycleOwner: LifecycleOwner
) {

    val preview = Preview.Builder().build()
    preview.setSurfaceProvider(previewView.surfaceProvider)

    val cameraSelector = CameraSelector.Builder()
        .requireLensFacing(lensFacing)
        .build()

    val imageAnalysis = ImageAnalysis.Builder()
        .setBackpressureStrategy(
            ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
        )
        .build()

    val executor = Executors.newSingleThreadExecutor()

    imageAnalysis.setAnalyzer(executor) { imageProxy ->
        processImage(imageProxy, context)
    }

    cameraProvider.unbindAll()

    cameraProvider.bindToLifecycle(
        lifecycleOwner,
        cameraSelector,
        preview,
        imageAnalysis
    )
}


fun startVoiceRecognition(context: Context) {

    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)

    intent.putExtra(
        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
    )

    intent.putExtra(
        RecognizerIntent.EXTRA_PROMPT,
        "Speak Now"
    )
    context.startActivity(intent)
}

fun processImage(imageProxy: ImageProxy, context: Context) {

    val bitmap = imageProxy.toBitmap()
    latestBitmap = bitmap

    val image = InputImage.fromBitmap(bitmap, 0)

    val detector = FaceDetection.getClient()

    detector.process(image)
        .addOnSuccessListener { faces ->

            if (faces.isEmpty()) return@addOnSuccessListener   // 🔥 IMPORTANT

            val face = faces[0]

            val box = face.boundingBox

            val croppedBitmap = Bitmap.createBitmap(
                bitmap,
                box.left.coerceAtLeast(0),
                box.top.coerceAtLeast(0),
                box.width().coerceAtMost(bitmap.width - box.left),
                box.height().coerceAtMost(bitmap.height - box.top)
            )

            val resizedBitmap = Bitmap.createScaledBitmap(croppedBitmap, 160, 160, true)

            val embedding = faceModel.getEmbedding(resizedBitmap)
            val name = FaceDatabase.recognize(embedding)

            Log.d("Testing", "Best match: $name")

            val currentTime = System.currentTimeMillis()

            if (currentTime - lastDetectionTime > 3000) {
                lastDetectionTime = currentTime

                if (name != null) {
                    speakPersonName("Hello $name")
                } else {
                    speakPersonName("Unknown person detected")
                }
            }
        }

    imageProxy.close()
}


    fun ImageProxy.toBitmap(): Bitmap {
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

        val yuvImage = android.graphics.YuvImage(
            nv21,
            android.graphics.ImageFormat.NV21,
            width,
            height,
            null
        )

        val out = java.io.ByteArrayOutputStream()
        yuvImage.compressToJpeg(
            android.graphics.Rect(0, 0, width, height),
            100,
            out
        )

        val yuv = out.toByteArray()
        return BitmapFactory.decodeByteArray(yuv, 0, yuv.size)
    }

    fun saveBitmap(bitmap: Bitmap, context: Context): String {

        val folder = File(context.getExternalFilesDir(null), "SwiftTalkAI")

        if (!folder.exists()) folder.mkdirs()

        val file = File(folder, "person_${System.currentTimeMillis()}.jpg")

        val stream = FileOutputStream(file)

        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)

        stream.flush()
        stream.close()

        return file.absolutePath

    }
fun speakPersonName(text: String) {
    val currentTime = System.currentTimeMillis()

    if (currentTime - lastSpeakTime < 3000) return

    lastSpeakTime = currentTime

    tts?.stop()
    tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)

    Log.d("TTS", "Speaking: $text")
}
fun addFace() {

    val bitmap = latestBitmap

    if (bitmap == null) {
        tts?.speak("Camera not ready", TextToSpeech.QUEUE_FLUSH, null, null)
        return
    }

    val image = InputImage.fromBitmap(bitmap, 0)

    detector.process(image)
        .addOnSuccessListener { faces ->

            if (faces.isNotEmpty()) {

                val face = faces[0]   // take first face

                val box = face.boundingBox

                val croppedBitmap = Bitmap.createBitmap(
                    bitmap,
                    box.left.coerceAtLeast(0),
                    box.top.coerceAtLeast(0),
                    box.width().coerceAtMost(bitmap.width - box.left),
                    box.height().coerceAtMost(bitmap.height - box.top)
                )
                val resizedBitmap = Bitmap.createScaledBitmap(croppedBitmap, 160, 160, true)

                val embedding = faceModel.getEmbedding(resizedBitmap)

                FaceDatabase.save("Raksha", embedding)

                tts?.speak("Face added", TextToSpeech.QUEUE_FLUSH, null, null)

            } else {
                tts?.speak("No face detected", TextToSpeech.QUEUE_FLUSH, null, null)
            }
        }
}
fun getCroppedFace(bitmap: Bitmap): Bitmap? {

    val results: List<Detection> = personDetector.detect(bitmap)

    for (detection in results) {
        for (category in detection.categories) {

            if (category.label == "person" && category.score > 0.5f) {

                val box = detection.boundingBox

                val left = box.left.toInt().coerceAtLeast(0)
                val top = box.top.toInt().coerceAtLeast(0)
                val right = box.right.toInt().coerceAtMost(bitmap.width)
                val bottom = box.bottom.toInt().coerceAtMost(bitmap.height)

                return Bitmap.createBitmap(
                    bitmap,
                    left,
                    top,
                    right - left,
                    bottom - top
                )
            }
        }
    }

    return null
}
