package com.nutriquest.app

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

internal class MealPhotoImporter(private val context: Context) {
    suspend fun copy(uri: Uri): File = withContext(Dispatchers.IO) {
        val original = File.createTempFile("nutriquest-import-", ".image", context.cacheDir)
        var photo: File? = null
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IOException("That photo couldn’t be opened.")
            input.use { source ->
                original.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var bytes = 0L
                    while (true) {
                        ensureActive()
                        val count = source.read(buffer)
                        if (count < 0) break
                        bytes += count
                        if (bytes > 20 * 1024 * 1024) throw IOException("Choose a photo smaller than 20 MB.")
                        output.write(buffer, 0, count)
                    }
                }
            }
            val bitmap = decodePhoto(original, 2048)
                ?: throw IOException("That photo format couldn’t be read. Try a JPG or PNG.")
            try {
                val normalized = File.createTempFile("nutriquest_meal_", ".jpg", context.cacheDir)
                photo = normalized
                normalized.outputStream().use {
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)) {
                        throw IOException("The photo couldn’t be imported.")
                    }
                }
                ensureActive()
                normalized
            } finally {
                bitmap.recycle()
            }
        } catch (error: Exception) {
            photo?.delete()
            throw error
        } finally {
            original.delete()
        }
    }
}

@Composable
internal fun AlbumPickerButton(enabled: Boolean, onPick: (Uri) -> Unit) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(onPick)
    }
    OutlinedButton(
        enabled = enabled,
        onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    ) { Text(if (enabled) "Choose from album" else "Importing photo…") }
}
