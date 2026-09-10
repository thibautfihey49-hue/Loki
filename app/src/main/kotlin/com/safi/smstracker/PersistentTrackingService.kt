package com.safi.smstracker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import org.osmdroid.util.GeoPoint

class PersistentTrackingService : Service() {

    companion object {
        private const val TAG = "SAFI_SERVICE"
        private const val CHANNEL_ID = "SAFI_PERSISTENT"
        private const val NOTIF_ID = 1337
        var isRunning = false
        private var handler: Handler? = null
        private var runnable: Runnable? = null
        private val INTERVAL = 60000L // 1 minute
        private var otherNumber: String? = null
        private var prefs: SharedPreferences? = null

        fun start(context: Context) {
            if (!isRunning) {
                val intent = Intent(context, PersistentTrackingService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                Log.d(TAG, "✅ Service démarré en arrière-plan")
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PersistentTrackingService::class.java))
            Log.d(TAG, "🛑 Service arrêté")
        }
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("SAFI_CONFIG", Context.MODE_PRIVATE)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
        startForeground(NOTIF_ID, createNotification())
        isRunning = true
        Log.d(TAG, "🟢 Service persistent CRÉE et en FOREGROUND")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        otherNumber = prefs?.getString("OTHER_NUM", "")
        startRepeatingUpdates()
        return START_STICKY // 🔴 CRUCIAL : Android le redémarre automatiquement si tu le tue
    }

    private fun startRepeatingUpdates() {
        stopRepeatingUpdates()
        
        handler = Handler(Looper.getMainLooper())
        runnable = object : Runnable {
            override fun run() {
                if (DataSmsReceiver.sendingJob != null) {
                    sendMyPosition()
                }
                handler?.postDelayed(this, INTERVAL)
            }
        }
        handler?.postDelayed(runnable!!, 5000)
        Log.d(TAG, "⏰ Mise à jour position démarrée toutes les minutes")
    }

    private fun stopRepeatingUpdates() {
        runnable?.let { handler?.removeCallbacks(it) }
        handler = null
        runnable = null
    }

    private fun sendMyPosition() {
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return
        }
        
        fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
            loc?.let {
                val text = "${Commands.RESPONSE_POS}${it.latitude},${it.longitude}"
                otherNumber = prefs?.getString("OTHER_NUM", "")
                otherNumber?.let { num ->
                    try {
                        val smsManager = android.telephony.SmsManager.getDefault()
                        try {
                            smsManager.sendDataMessage(num, null, Commands.PORT.toShort(), text.toByteArray(Charsets.UTF_8), null, null)
                            Log.d(TAG, "📤 Position envoyée à $num")
                        } catch (e: Exception) {
                            smsManager.sendTextMessage(num, null, text, null, null)
                            Log.d(TAG, "📤 Position envoyée (texte) à $num")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Erreur envoi position", e)
                    }
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SAFI - Suivi en arrière-plan",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Le service de suivi fonctionne en arrière-plan pour envoyer/recevoir les positions"
                setShowBadge(false)
                enableVibration(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainMapActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🟢 SAFI — Suivi actif")
            .setContentText("L'application fonctionne en arrière-plan — Envoi/réception des positions")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopRepeatingUpdates()
        isRunning = false
        Log.d(TAG, "🔴 Service détruit — Redémarrage automatique imminent...")
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // 🔴 Si l'utilisateur ferme l'appli → REDÉMARRER IMMÉDIATEMENT
        Log.d(TAG, "⚠️ Appli fermée par l'utilisateur — Redémarrage du service...")
        val restartIntent = Intent(applicationContext, PersistentTrackingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(restartIntent)
        } else {
            startService(restartIntent)
        }
    }
}
