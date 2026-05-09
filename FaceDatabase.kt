package com.example.swifttalkai
import android.util.Log
object FaceDatabase {

    val database = mutableListOf<Pair<String, FloatArray>>()

    fun save(name: String, embedding: FloatArray) {
        database.clear()  
        database.add(Pair(name, embedding))
    }
    fun recognize(embedding: FloatArray): String? {

        var bestMatchName: String? = null
        var minDistance = Float.MAX_VALUE

        for (entry in database) {

            val name = entry.first
            val savedEmbedding = entry.second

            val distance = calculateDistance(embedding, savedEmbedding)

            Log.d("Testing", "Distance to ${entry.first}: $distance")

            if (distance < minDistance) {
                minDistance = distance
                bestMatchName = name
            }
        }

        // 🔥 FINAL DECISION
        return if (minDistance < 0.9f) bestMatchName else null
    }
    private fun calculateDistance(a: FloatArray, b: FloatArray): Float {

        var sum = 0f

        for (i in a.indices) {
            val diff = a[i] - b[i]
            sum += diff * diff
        }

        return kotlin.math.sqrt(sum)
    }
}