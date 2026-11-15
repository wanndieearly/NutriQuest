package com.nutriquest.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import java.io.IOException

@Composable
internal fun CameraScreen(
    onPhotoCaptured: (File) -> Unit,
    onEnterManually: () -> Unit,
    onImportPhoto: (Uri) -> Unit,
    importingPhoto: Boolean,
    photoError: String?
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnPhotoCaptured by rememberUpdatedState(onPhotoCaptured)
    val executor = remember(context) { ContextCompat.getMainExecutor(context) }
    val previewView = remember(context) {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    var hasPermission by remember(context) {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionDenied by remember { mutableStateOf(false) }
    var boundCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var isCapturing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
        permissionDenied = !it
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) permission.launch(Manifest.permission.CAMERA)
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(hasPermission, lifecycleOwner, previewView) {
        var active = true
        var provider: ProcessCameraProvider? = null
        val preview = Preview.Builder().build()
        val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

        if (hasPermission) {
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                if (active) {
                    try {
                        val cameraProvider = future.get()
                        provider = cameraProvider
                        if (cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                            preview.surfaceProvider = previewView.surfaceProvider
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture
                            )
                            boundCapture = capture
                            error = null
                        } else {
                            error = "No rear camera is available on this device."
                        }
                    } catch (_: Exception) {
                        error = "The camera couldn’t start. You can still log this meal by hand."
                    }
                }
            }, executor)
        }

        onDispose {
            active = false
            boundCapture = null
            isCapturing = false
            provider?.unbind(preview, capture)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Snap your plate 📸", style = MaterialTheme.typography.headlineMedium, color = NutriGreen)
        Spacer(Modifier.height(6.dp))
        Text("Take a photo, then build your meal in the Quest Kitchen.")
        Spacer(Modifier.height(14.dp))

        if (hasPermission) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).aspectRatio(1f).clip(RoundedCornerShape(24.dp))
            )
            if (boundCapture == null && error == null) {
                Text("Starting camera…", modifier = Modifier.padding(top = 8.dp))
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Camera access lets you attach a photo to your meal.")
                TextButton(onClick = { permission.launch(Manifest.permission.CAMERA) }) {
                    Text("Grant camera permission")
                }
                if (permissionDenied) {
                    TextButton(onClick = {
                        context.startActivity(Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)
                        ))
                    }) { Text("Open app settings") }
                }
            }
        }

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }

        if (hasPermission) {
            Button(
                onClick = {
                    val capture = boundCapture ?: return@Button
                    if (isCapturing) return@Button
                    val photo = try {
                        File.createTempFile("nutriquest_meal_", ".jpg", context.cacheDir)
                    } catch (_: IOException) {
                        error = "Couldn’t create a photo. Check your available storage."
                        return@Button
                    }
                    isCapturing = true
                    error = null
                    previewView.display?.let { capture.targetRotation = it.rotation }
                    capture.takePicture(
                        ImageCapture.OutputFileOptions.Builder(photo).build(),
                        executor,
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(results: ImageCapture.OutputFileResults) {
                                if (boundCapture !== capture) {
                                    photo.delete()
                                    return
                                }
                                isCapturing = false
                                if (photo.length() == 0L) {
                                    photo.delete()
                                    error = "The photo was empty. Try again."
                                } else {
                                    currentOnPhotoCaptured(photo)
                                }
                            }

                            override fun onError(exception: ImageCaptureException) {
                                photo.delete()
                                if (boundCapture === capture) {
                                    isCapturing = false
                                    error = "Couldn’t take the photo. Try again."
                                }
                            }
                        }
                    )
                },
                enabled = boundCapture != null && !isCapturing && !importingPhoto,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            ) {
                Text(if (isCapturing) "Capturing…" else "Take meal photo")
            }
        }
        AlbumPickerButton(enabled = !importingPhoto && !isCapturing, onPick = onImportPhoto)
        photoError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        TextButton(onClick = onEnterManually) { Text("Enter meal by hand") }
    }
}
