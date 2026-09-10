package com.safi.smstracker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SimpleCamera {
    private const val TAG = "SAFI_CAMERA"

    fun takePhoto(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        useFront: Boolean,
        onSuccess: (File) -> Unit,
        onError: (String) -> Unit
    ) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) 
            != PackageManager.PERMISSION_GRANTED) {
            onError("Permission CAMÉRA manquante")
            return
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            
            val safiDir = File(context.getExternalFilesDir(null), "SAFI_Photos")
            if (!safiDir.exists()) safiDir.mkdirs()

            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.FRANCE).format(Date())
            val photoFile = File(safiDir, "PHOTO_${timeStamp}.jpg")

            val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
            val imageCapture = ImageCapture.Builder().build()

            val cameraSelector = if (useFront) 
                CameraSelector.DEFAULT_FRONT_CAMERA 
            else 
                CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                val preview = Preview.Builder().build()
                
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )

                imageCapture.takePicture(
                    outputOptions,
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            Log.d(TAG, "✅ Photo sauvegardée: ${photoFile.absolutePath}")
                            Toast.makeText(context, "✅ Photo sauvegardée !", Toast.LENGTH_LONG).show()
                            onSuccess(photoFile)
                            cameraProvider.unbindAll()
                        }

                        override fun onError(exc: ImageCaptureException) {
                            Log.e(TAG, "❌ Erreur prise photo", exc)
                            onError(exc.message ?: "Erreur inconnue")
                            cameraProvider.unbindAll()
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur caméra", e)
                onError(e.message ?: "Erreur caméra")
                cameraProvider.unbindAll()
            }
        }, ContextCompat.getMainExecutor(context))
    }
}
