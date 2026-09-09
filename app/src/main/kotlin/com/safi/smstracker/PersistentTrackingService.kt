package com.safi.smstracker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices

class PersistentTrackingService : Service() {

    companion object {
        private const val TAG = "SAFI_SERVICE"
        private const val CHANNEL_ID = "SAFI_TRACKING"
        private const val NOTIF_ID = 1337
        
        private var isRunning = false
        private var handler: Handler? = null
        private var runnable: Runnable? = null
        private var fusedClient: FusedLocationProviderClient? = null

        fun start(context: Context) {
            if (isRunning) {
                Log.d(TAG, "✅ Service déjà en cours")
                return
            }
            
            val hasFine = ActivityCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            
            val hasCoarse = ActivityCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            
            if (!hasFine && !hasCoarse) {
                Log.e(TAG, "❌ Permission de localisation NON accordée")
                return
            }
            
            val intent = Intent(context, PersistentTrackingService::class.java)
            ContextCompat.startForegroundService(context, intent)
            Log.d(TAG, "✅ Demande de démarrage du service envoyée")
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PersistentTrackingService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "🔧 Service onCreate()")
        createNotificationChannel()
        startForeground(NOTIF_ID, createNotification())
        isRunning = true
        handler = Handler(Looper.getMainLooper())
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        startLocationLoop()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SAFI — Suivi GPS en arrière-plan",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Maintient le suivi actif même en arrière-plan"
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
            this,
            0,
            intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SAFI — Suivi actif")
            .setContentText("L'application fonctionne en arrière-plan")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    private fun startLocationLoop() {
        runnable = object : Runnable {
            override fun run() {
                sendMyPositionIfNeeded()
                handler?.postDelayed(this, 60000)
            }
        }
        handler?.postDelayed(runnable!!, 10000)
        Log.d(TAG, "🔄 Boucle de positionnement démarrée")
    }

    private fun sendMyPositionIfNeeded() {
        val prefs = getSharedPreferences("SAFI_CONFIG", Context.MODE_PRIVATE)
        val otherNum = prefs.getString("OTHER_NUM", "") ?: ""
        val isFollowing = DataSmsReceiver.sendingJob != null
        
        if (otherNum.isEmpty() || !isFollowing) return

        if (ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "❌ Permission GPS perdue")
            return
        }

        fusedClient?.lastLocation?.addOnSuccessListener { loc ->
            loc?.let {
                val msg = "${Commands.RESPONSE_POS}${it.latitude},${it.longitude}"
                try {
                    val smsManager = android.telephony.SmsManager.getDefault()
                    try {
                        smsManager.sendDataMessage(
                            otherNum,
                            null,
                            Commands.PORT.toShort(),
                            msg.toByteArray(Charsets.UTF_8),
                            null,
                            null
                        )
                    } catch (e: Exception) {
                        smsManager.sendTextMessage(otherNum, null, msg, null, null)
                    }
                    Log.d(TAG, "📤 Position envoyée: ${it.latitude}, ${it.longitude}")
                } catch (e: Exception) {
                    Log.e(TAG, "Échec envoi position", e)
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        runnable?.let { handler?.removeCallbacks(it) }
        handler = null
        Log.d(TAG, "🛑 Service détruit")
    }
}
