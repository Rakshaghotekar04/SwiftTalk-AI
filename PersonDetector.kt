package com.example.swifttalkai

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.detector.Detection
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import org.tensorflow.lite.task.vision.detector.ObjectDetector.ObjectDetectorOptions

class PersonDetector(context: Context) {

    private val detector: ObjectDetector

    init {

        val options = ObjectDetectorOptions.builder()
            .setScoreThreshold(0.5f)
            .setMaxResults(3)
            .build()

        detector = ObjectDetector.createFromFileAndOptions(
            context,
            "efficientdet-lite0.tflite",
            options
        )
    }

    fun detect(bitmap: Bitmap): List<Detection> {

        val tensorImage = TensorImage.fromBitmap(bitmap)

        return detector.detect(tensorImage)
    }
}