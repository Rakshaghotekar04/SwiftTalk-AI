package com.example.swifttalkai
import android.util.Log
import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class FaceNetModel(context: Context) {

    private val interpreter: Interpreter

    init {
        interpreter = Interpreter(loadModelFile(context))
        val inputTensor = interpreter.getInputTensor(0)

        val shape = inputTensor.shape()
        val dataType = inputTensor.dataType()

        Log.d("MODEL_DEBUG", "Shape: ${shape.contentToString()}")
        Log.d("MODEL_DEBUG", "DataType: $dataType")
    }

    private fun loadModelFile(context: Context): MappedByteBuffer {

        val fileDescriptor = context.assets.openFd("facenet.tflite")
        val inputStream = fileDescriptor.createInputStream()
        val fileChannel = inputStream.channel

        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength

        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            startOffset,
            declaredLength
        )
    }

    fun getEmbedding(bitmap: Bitmap): FloatArray {

        val inputSize = 112
        val batchSize = 2  // ✅ Model truly requires batch=2

        val inputBuffer = ByteBuffer.allocateDirect(
            batchSize * inputSize * inputSize * 3 * 4
        )
        inputBuffer.order(ByteOrder.nativeOrder())

        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val intValues = IntArray(inputSize * inputSize)
        scaledBitmap.getPixels(intValues, 0, inputSize, 0, 0, inputSize, inputSize)

        inputBuffer.rewind()

        // ✅ Fill BOTH batch slots with the same image — model requires it
        for (b in 0 until batchSize) {
            for (i in intValues.indices) {
                val pixel = intValues[i]

                // ✅ Correct FaceNet normalization
                val r = (((pixel shr 16) and 0xFF) - 127.5f) / 128.0f
                val g = (((pixel shr 8) and 0xFF) - 127.5f) / 128.0f
                val bVal = ((pixel and 0xFF) - 127.5f) / 128.0f

                inputBuffer.putFloat(r)
                inputBuffer.putFloat(g)
                inputBuffer.putFloat(bVal)
            }
        }

        val embedding = Array(batchSize) { FloatArray(192) }
        interpreter.run(inputBuffer, embedding)

        return l2Normalize(embedding[0])
    }

    private fun l2Normalize(embedding: FloatArray): FloatArray {
        val norm = kotlin.math.sqrt(embedding.map { it * it }.sum())
        return if (norm > 0f) FloatArray(embedding.size) { embedding[it] / norm } else embedding
    }
}