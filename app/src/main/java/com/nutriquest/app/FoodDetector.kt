package com.nutriquest.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileNotFoundException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

internal data class FoodGuess(val label: String, val food: Food?)

internal sealed interface FoodScan {
    data object Idle : FoodScan
    data object Running : FoodScan
    data class Ready(val guesses: List<FoodGuess>) : FoodScan
    data class Unavailable(val reason: String) : FoodScan
}

/** CPU detector for an Ultralytics-style float32 YOLO export without embedded NMS. */
internal class FoodDetector(context: Context) {
    private val assets = context.applicationContext.assets

    suspend fun scan(photo: File): FoodScan = withContext(Dispatchers.Default) {
        try {
            FoodScan.Ready(detect(photo))
        } catch (error: CancellationException) {
            throw error
        } catch (_: FileNotFoundException) {
            FoodScan.Unavailable("Food recognition needs a YOLO model and its label file. You can still enter foods below.")
        } catch (error: Exception) {
            if (BuildConfig.DEBUG) Log.w("FoodDetector", "Local scan failed", error)
            FoodScan.Unavailable("The scanner couldn’t read this photo or model. You can still enter foods below.")
        } catch (_: LinkageError) {
            FoodScan.Unavailable("This device couldn’t load the scanner. You can still enter foods below.")
        }
    }

    private fun detect(photo: File): List<FoodGuess> {
        val labels = assets.open("food_labels.txt").bufferedReader().useLines { lines ->
            lines.map(String::trim).toList()
        }
        require(labels.isNotEmpty() && labels.none(String::isEmpty))
        val model = assets.open("food_detector.tflite").use { it.readBytes() }.let { bytes ->
            ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).apply {
                put(bytes)
                rewind()
            }
        }
        return Interpreter(model, Interpreter.Options().setNumThreads(2)).use { interpreter ->
            val inputTensor = interpreter.getInputTensor(0)
            val outputTensor = interpreter.getOutputTensor(0)
            val input = inputTensor.shape()
            val output = outputTensor.shape()
            require(interpreter.inputTensorCount == 1 && interpreter.outputTensorCount == 1)
            require(inputTensor.dataType() == DataType.FLOAT32 && outputTensor.dataType() == DataType.FLOAT32)
            require(input.size == 4 && input[0] == 1 && (input[1] == 3 || input[3] == 3))
            val inputChannelsFirst = input[1] == 3
            val width = if (inputChannelsFirst) input[3] else input[2]
            val height = if (inputChannelsFirst) input[2] else input[1]
            require(width in 32..1024 && height in 32..1024)
            val features = labels.size + 4
            require(output.size == 3 && output[0] == 1 && (output[1] == features || output[2] == features))
            val channelsFirst = output[1] == features
            val count = if (channelsFirst) output[2] else output[1]
            require(count > 0 && count.toLong() * features <= 8_000_000)
            val bitmap = decodePhoto(photo, max(width, height)) ?: error("Photo could not be decoded")
            val image = try {
                letterbox(bitmap, width, height, inputChannelsFirst)
            } finally {
                bitmap.recycle()
            }
            val result = ByteBuffer.allocateDirect(count * features * Float.SIZE_BYTES).order(ByteOrder.nativeOrder())
            interpreter.run(image, result)
            result.rewind()
            val values = result.asFloatBuffer()
            val cocoDemo = labels.size == 80 && labels.first() == "person"
            decodeYolo(count, labels.size) { candidate, feature ->
                values.get(if (channelsFirst) feature * count + candidate else candidate * features + feature)
            }.mapNotNull { hit ->
                val label = labels[hit.label]
                val food = catalogFood(label)
                if (food != null || label.lowercase() in demoFoodLabels || !cocoDemo) {
                    FoodGuess(label.replace('_', ' '), food)
                } else {
                    null
                }
            }.distinctBy { it.label.lowercase() }.take(8)
        }
    }

    private fun letterbox(source: Bitmap, width: Int, height: Int, channelsFirst: Boolean): ByteBuffer {
        val scale = min(width / source.width.toFloat(), height / source.height.toFloat())
        val scaledWidth = source.width * scale
        val scaledHeight = source.height * scale
        val left = (width - scaledWidth) / 2
        val top = (height - scaledHeight) / 2
        val target = createBitmap(width, height)
        val pixels = IntArray(width * height)
        try {
            Canvas(target).apply {
                drawColor(Color.rgb(114, 114, 114))
                drawBitmap(source, null, RectF(left, top, left + scaledWidth, top + scaledHeight), Paint(Paint.FILTER_BITMAP_FLAG))
            }
            target.getPixels(pixels, 0, width, 0, 0, width, height)
        } finally {
            target.recycle()
        }
        return ByteBuffer.allocateDirect(pixels.size * 3 * Float.SIZE_BYTES).order(ByteOrder.nativeOrder()).apply {
            if (channelsFirst) {
                pixels.forEach { putFloat(Color.red(it) / 255f) }
                pixels.forEach { putFloat(Color.green(it) / 255f) }
                pixels.forEach { putFloat(Color.blue(it) / 255f) }
            } else {
                pixels.forEach { pixel ->
                    putFloat(Color.red(pixel) / 255f)
                    putFloat(Color.green(pixel) / 255f)
                    putFloat(Color.blue(pixel) / 255f)
                }
            }
            rewind()
        }
    }
}

private val demoFoodLabels = setOf("banana", "apple", "sandwich", "orange", "broccoli", "carrot", "hot dog", "pizza", "donut", "cake")

internal data class YoloHit(
    val label: Int, val score: Float, val left: Float, val top: Float, val right: Float, val bottom: Float
) {
    fun overlap(other: YoloHit): Float {
        val intersection = max(0f, min(right, other.right) - max(left, other.left)) *
            max(0f, min(bottom, other.bottom) - max(top, other.top))
        val union = (right - left) * (bottom - top) + (other.right - other.left) * (other.bottom - other.top) - intersection
        return if (union > 0f) intersection / union else 0f
    }
}

internal fun decodeYolo(count: Int, classes: Int, value: (Int, Int) -> Float): List<YoloHit> {
    val candidates = (0 until count).mapNotNull { box ->
        var label = -1
        var score = 0.35f
        for (index in 0 until classes) {
            val confidence = value(box, index + 4)
            if (confidence.isFinite() && confidence > score && confidence <= 1f) {
                label = index
                score = confidence
            }
        }
        val x = value(box, 0)
        val y = value(box, 1)
        val w = value(box, 2)
        val h = value(box, 3)
        if (label < 0 || !x.isFinite() || !y.isFinite() || !w.isFinite() || !h.isFinite() || w <= 0 || h <= 0) {
            null
        } else {
            YoloHit(label, score, x - w / 2, y - h / 2, x + w / 2, y + h / 2)
        }
    }.sortedByDescending { it.score }.take(300)
    val kept = mutableListOf<YoloHit>()
    candidates.forEach { hit ->
        if (kept.none { it.label == hit.label && it.overlap(hit) > 0.45f }) kept.add(hit)
    }
    return kept
}
