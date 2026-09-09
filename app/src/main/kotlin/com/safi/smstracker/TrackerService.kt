package com.safi.smstracker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Point
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.telephony.SmsManager
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.google.android.gms.location.*
import com.safi.smstracker.model.Position
import kotlin.math.*

class TrackerService : Service() {
    companion object {
        const val CHANNEL = "SAFI_TRACKER"
        const val PORT = 7777
        var myPos: Position? = null
        var otherPos: Position? = null
    }

    private lateinit var wm: WindowManager
    private lateinit var prefs: SharedPreferences
    private lateinit var fused: FusedLocationProviderClient
    private lateinit var locReq: LocationRequest
    private lateinit var locCb: LocationCallback
    private var mapView: MapView? = null
    private var running = true

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("SAFI_CONFIG", Context.MODE_PRIVATE)
        createChannel()
        startForeground(1, createNotif())
        initMap()
        initGPS()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(NotificationChannel(CHANNEL, "SAFI", NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun createNotif(): Notification {
        return Notification.Builder(this, CHANNEL)
            .setContentTitle("SAFI 📡")
            .setContentText("Actif — Port 7777")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()
    }

    private fun initMap() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val display = wm.defaultDisplay
        val size = Point()
        display.getSize(size)
        val side = (size.x * 0.4).toInt()

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(side, side, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 20
            y = 100
        }

        mapView = MapView(this)
        wm.addView(mapView, params)
    }

    private fun initGPS() {
        fused = LocationServices.getFusedLocationProviderClient(this)
        locReq = LocationRequest.Builder(5000)
            .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY).build()
        
        locCb = object : LocationCallback() {
            override fun onLocationResult(r: LocationResult) {
                r.lastLocation?.let { loc ->
                    myPos = Position(loc.latitude, loc.longitude)
                    mapView?.invalidate()
                    sendMyPosition()
                }
            }
        }

        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == 
            android.content.pm.PackageManager.PERMISSION_GRANTED) {
            fused.requestLocationUpdates(locReq, locCb, mainLooper)
        }
    }

    private fun sendMyPosition() {
        val num = prefs.getString("OTHER_NUM", "") ?: return
        val pos = myPos ?: return
        try {
            SmsManager.getDefault().sendDataMessage(num, null, PORT.toShort(), 
                pos.toString().toByteArray(Charsets.UTF_8), null, null)
        } catch (e: Exception) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "NEW_POS") {
            val pos = intent.getSerializableExtra("pos") as? Position
            if (pos != null) {
                otherPos = pos
                mapView?.invalidate()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        running = false
        fused.removeLocationUpdates(locCb)
        mapView?.let { wm.removeView(it) }
    }

    override fun onBind(i: Intent?): IBinder? = null

    inner class MapView(ctx: Context) : View(ctx) {
        private val bg = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL }
        private val grid = Paint().apply { color = Color.parseColor("#222222"); strokeWidth = 1f }
        private val dotMe = Paint().apply { color = Color.GREEN; style = Paint.Style.FILL }
        private val dotOther = Paint().apply { color = Color.RED; style = Paint.Style.FILL }
        private val ring = Paint().apply { color = Color.GREEN; style = Paint.Style.STROKE; strokeWidth = 2f }
        private val scale = 50000f

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w < 10 || h < 10) return

            canvas.drawRect(0f, 0f, w, h, bg)
            repeat(11) { i -> canvas.drawLine(i*w/10, 0f, i*w/10, h, grid) }
            repeat(11) { i -> canvas.drawLine(0f, i*h/10, w, i*h/10, grid) }

            canvas.drawCircle(w/2, h/2, 12f, dotMe)
            canvas.drawCircle(w/2, h/2, 20f, ring)

            val my = myPos
            val other = otherPos
            if (my != null && other != null) {
                val dx = (other.longitude - my.longitude) * 111000.0 * cos(Math.toRadians(my.latitude))
                val dy = -(other.latitude - my.latitude) * 111000.0
                val px = (w/2 + (dx / scale)).toFloat()
                val py = (h/2 + (dy / scale)).toFloat()
                if (px > 10f && px < w-10f && py > 10f && py < h-10f) {
                    canvas.drawCircle(px, py, 12f, dotOther)
                }
            }
        }
    }
}
