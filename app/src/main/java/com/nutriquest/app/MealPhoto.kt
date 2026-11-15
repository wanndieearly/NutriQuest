package com.nutriquest.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

@Composable
internal fun MealPhoto(file: File, modifier: Modifier, maxSize: Int = 960) {
    val bitmap by produceState<Bitmap?>(null, file, maxSize) {
        value = withContext(Dispatchers.IO) { decodePhoto(file, maxSize) }
    }
    Box(
        modifier.clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center
    ) {
        val photo = bitmap
        if (photo != null) {
            Image(
                photo.asImageBitmap(),
                contentDescription = "Meal photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text("📷", style = MaterialTheme.typography.headlineSmall)
        }
    }
}

internal fun decodePhoto(file: File, maxSize: Int): Bitmap? = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val sampleSize = maxOf(info.size.width, info.size.height) / maxSize
            decoder.setTargetSampleSize(sampleSize.coerceAtLeast(1))
        }
    } else {
        decodeLegacyPhoto(file, maxSize)
    }
} catch (_: IOException) {
    null
}

private fun decodeLegacyPhoto(file: File, maxSize: Int): Bitmap? {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, options)
    options.inJustDecodeBounds = false
    options.inSampleSize = (maxOf(options.outWidth, options.outHeight) / maxSize).coerceAtLeast(1)
    val bitmap = BitmapFactory.decodeFile(file.path, options) ?: return null
    val exif = ExifInterface(file.path)
    val transform = Matrix().apply {
        if (exif.isFlipped) setScale(-1f, 1f)
        postRotate(exif.rotationDegrees.toFloat())
    }
    if (transform.isIdentity) return bitmap
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, transform, true)
}
