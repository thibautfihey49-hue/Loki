package com.safi.smstracker

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.LifecycleCameraController
import java.text.SimpleDateFormat
import java.util.Locale

class HiddenCameraActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.alpha = 0f
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) takePhoto()
        else { Toast.makeText(this, "❌ Permission caméra manquante", Toast.LENGTH_SHORT).show(); finish() }
    }

    private fun takePhoto() {
        val ctrl = LifecycleCameraController(this).apply { bindToLifecycle(this@HiddenCameraActivity) }
        val name = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.FRANCE).format(System.currentTimeMillis())
        val cv = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P)
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SAFI_Private")
        }
        val opts = ImageCapture.OutputFileOptions.Builder(contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv).build()
        ctrl.takePicture(opts, mainExecutor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(r: ImageCapture.OutputFileResults) {
                Toast.makeText(this, "📷 Photo sauvegardée — Galerie privée uniquement", Toast.LENGTH_SHORT).show()
                finish()
            }
            override fun onError(e: ImageCaptureException) {
                Toast.makeText(this, "❌ Échec photo", Toast.LENGTH_SHORT).show(); finish()
            }
        })
    }
}
