package com.safi.smstracker

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.Camera
import android.media.MediaScannerConnection
import android.os.Build
import android.os.IBinder
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CameraCaptureService : Service() {

    companion object {
        private const val TAG = "SAFI_CAMERA"
        private const val CHANNEL_ID = "SAFI_CAMERA_CHANNEL"
        private const val NOTIF_ID = 1338
        
        const val FACING_FRONT = 0
        const val FACING_BACK = 1
        
        fun takePhoto(context: Context, useFront: Boolean = true) {
            Log.d(TAG, "📸 takePhoto appelé — useFront=$useFront")
            
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) 
                != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "❌ Permission CAMÉRA manquante !")
                Toast.makeText(context, "❌ Permission Caméra manquante", Toast.LENGTH_LONG).show()
                return
            }
            
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "❌ Permission Stockage manquante !")
                Toast.makeText(context, "❌ Permission Stockage manquante", Toast.LENGTH_LONG).show()
                return
            }
            
            val intent = Intent(context, CameraCaptureService::class.java).apply {
                putExtra("USE_FRONT", useFront)
            }
            
            ContextCompat.startForegroundService(context, intent)
            Toast.makeText(context, "📸 Prise de vue en cours...", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "✅ Service de caméra démarré")
        }
        
        fun capturePhoto(context: Context, facing: Int) {
            takePhoto(context, useFront = (facing == FACING_FRONT))
        }
    }

    private var camera: Camera? = null
    private var photoFile: File? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "🔧 CameraCaptureService.onCreate()")
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "🔧 onStartCommand reçu")
        intent?.getBooleanExtra("USE_FRONT", true)?.let { useFront ->
            capturePhotoInternal(useFront)
        } ?: run {
            Log.e(TAG, "❌ Pas de paramètre USE_FRONT, utilisation de la caméra ARRIÈRE par défaut")
            capturePhotoInternal(useFront = false)
        }
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SAFI — Caméra à distance",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Prise de photos en arrière-plan"
                setShowBadge(true)
                enableVibration(true)
                setSound(null, null)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("📸 SAFI — Prise de photo")
            .setContentText("Capture en cours...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSilent(false)
            .setAutoCancel(true)
            .build()
    }

    private fun capturePhotoInternal(useFront: Boolean) {
        Log.d(TAG, "📸 capturePhotoInternal — caméra ${if (useFront) "AVANT" else "ARRIÈRE"}")
        
        try {
            val cameraId = if (useFront) findFrontCamera() else findBackCamera()
            if (cameraId == -1) {
                Log.e(TAG, "❌ Caméra introuvable")
                Toast.makeText(this, "❌ Caméra introuvable", Toast.LENGTH_LONG).show()
                stopSelf()
                return
            }
            Log.d(TAG, "✅ Caméra trouvée: id=$cameraId")

            // ✅ Ouvrir la caméra
            camera = Camera.open(cameraId)
            Log.d(TAG, "✅ Caméra ouverte")

            // ✅ Configurer les paramètres
            val params = camera!!.parameters
            params.pictureFormat = ImageFormat.JPEG
            params.setJpegQuality(90)
            
            // ✅ Essayer de trouver une résolution supportée
            val sizes = params.supportedPictureSizes
            val targetSize = sizes?.firstOrNull { it.width <= 1280 } ?: sizes?.firstOrNull()
            targetSize?.let {
                params.setPictureSize(it.width, it.height)
                Log.d(TAG, "✅ Résolution: ${it.width}x${it.height}")
            }
            
            camera!!.parameters = params

            // ✅ Démarrer la prévisualisation (OBLIGATOIRE sur Android 11+)
            try {
                camera!!.setPreviewDisplay(null)
            } catch (e: Exception) {
                Log.w(TAG, "Prévisualisation sans surface: ${e.message}")
            }
            camera!!.startPreview()
            Log.d(TAG, "✅ Prévisualisation démarrée")

            // ✅ Prendre la photo
            Thread.sleep(300) // Laisser le temps à la caméra de s'ajuster
            camera!!.takePicture(null, null, { data, cam ->
                Log.d(TAG, "📸 Données reçues: ${data.size} octets")
                savePhoto(data, useFront)
                cam.release()
                camera = null
                stopSelf()
            })

        } catch (e: Exception) {
            Log.e(TAG, "❌ ERREUR CAMÉRA: ${e.message}", e)
            Toast.makeText(this, "❌ Erreur caméra: ${e.message}", Toast.LENGTH_LONG).show()
            try { camera?.release() } catch (ignored: Exception) {}
            camera = null
            stopSelf()
        }
    }

    private fun savePhoto(data: ByteArray, useFront: Boolean) {
        try {
            // ✅ Dossier PUBLIC accessible dans la Galerie
            val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val safiDir = File(publicDir, "SAFI_Photos")
            if (!safiDir.exists()) safiDir.mkdirs()
            Log.d(TAG, "✅ Dossier: ${safiDir.absolutePath}")

            // ✅ Nom unique
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.FRANCE).format(Date())
            val suffix = if (useFront) "AVANT" else "ARRIERE"
            photoFile = File(safiDir, "SAFI_${timeStamp}_${suffix}.jpg")

            // ✅ Écrire le fichier
            val fos = FileOutputStream(photoFile)
            fos.write(data)
            fos.flush()
            fos.close()
            Log.d(TAG, "✅ Photo SAUVEGARDÉE: ${photoFile!!.absolutePath}")

            // ✅ NOTIFIER la galerie Android — la photo apparaît IMMÉDIATEMENT
            MediaScannerConnection.scanFile(
                this,
                arrayOf(photoFile!!.absolutePath),
                arrayOf("image/jpeg")
            ) { _, _ ->
                Log.d(TAG, "✅ Photo indexée dans la galerie !")
            }

            // ✅ Notification à l'utilisateur
            Toast.makeText(this, "✅ Photo SAUVEGARDÉE dans Galerie → SAFI_Photos !", Toast.LENGTH_LONG).show()

            // ✅ Notifier l'UI
            MainMapActivity.instance?.onPhotoReceived(photoFile!!)

            // ✅ Ajouter à la file d'envoi
            PhotoUploadService.addPhotoToQueue(this, photoFile!!)

        } catch (e: Exception) {
            Log.e(TAG, "❌ ERREUR SAUVEGARDE: ${e.message}", e)
            Toast.makeText(this, "❌ Erreur sauvegarde: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun findFrontCamera(): Int {
        for (i in 0 until Camera.getNumberOfCameras()) {
            val info = Camera.CameraInfo()
            Camera.getCameraInfo(i, info)
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) return i
        }
        return findBackCamera()
    }

    private fun findBackCamera(): Int {
        for (i in 0 until Camera.getNumberOfCameras()) {
            val info = Camera.CameraInfo()
            Camera.getCameraInfo(i, info)
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) return i
        }
        return 0
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
