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
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.safi.smstracker.databinding.ViewFloatingMapBinding
import com.safi.smstracker.model.Position
import kotlin.math.*

class FloatingMapService : Service() {

    companion object {
        const val CHANNEL_ID = "SAFI_DATA_SMS"
        const val DESTINATION_PORT = 7777
        var myLastPos: Position? = null
        var otherLastPos: Position? = null
    }

    private lateinit var wm: WindowManager
    private lateinit var binding: ViewFloatingMapBinding
    private lateinit var fusedLoc: FusedLocationProviderClient
    private lateinit var locReq: LocationRequest
    private lateinit var locCb: LocationCallback
    private var floatingView: View? = null
    private var mapCanvas: OfflineMapView? = null
    private lateinit var prefs: SharedPreferences

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("SAFI_CONFIG", Context.MODE_PRIVATE)
        createChannel()
        startForeground(1, createNotif(), 8)
        initFloatingWindow()
        initLocation()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "SAFI Data SMS", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun createNotif(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SAFI Data SMS 📡")
            .setContentText("SMS de données — Port 7777")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun initFloatingWindow() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        binding = ViewFloatingMapBinding.inflate(getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater)
        floatingView = binding.root

        val display = wm.defaultDisplay
        val size = Point()
        display.getSize(size)
        val side = (size.x * 0.40).toInt()

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            side, side,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = (size.x * 0.06).toInt()
            y = (size.y * 0.10).toInt()
        }
        wm.addView(floatingView, params)

        val container = binding.root.findViewById<FrameLayout>(R.id.map_container)
        container.removeAllViews()
        mapCanvas = OfflineMapView(this)
        container.addView(mapCanvas)

        binding.btnCloseMap.setOnClickListener { stopSelf() }
    }

    private fun initLocation() {
        fusedLoc = LocationServices.getFusedLocationProviderClient(this)
        locReq = LocationRequest.Builder(5000).setMinUpdateIntervalMillis(3000)
            .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY).build()
        locCb = object : LocationCallback() {
            override fun onLocationResult(r: LocationResult) {
                super.onLocationResult(r)
                r.lastLocation?.let { loc ->
                    val pos = Position(loc.latitude, loc.longitude, true)
                    myLastPos = pos
                    mapCanvas?.updateMyPos(pos)
                    sendPositionDataSms(pos)
                }
            }
        }
        startLocUpdates()
    }

    private fun startLocUpdates() {
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            fusedLoc.requestLocationUpdates(locReq, locCb, mainLooper)
            fusedLoc.lastLocation.addOnSuccessListener { loc ->
                loc?.let {
                    val pos = Position(it.latitude, it.longitude, true)
                    myLastPos = pos
                    mapCanvas?.updateMyPos(pos)
                }
            }
        }
    }

    private fun sendPositionDataSms(p: Position) {
        val otherNum = prefs.getString("OTHER_NUMBER", "") ?: return
        if (otherNum.isEmpty()) return

        try {
            val data = p.toString().toByteArray(Charsets.UTF_8)
            SmsManager.getDefault().sendDataMessage(
                otherNum,
                null,
                DESTINATION_PORT.toShort(),
                data,
                null, null
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun onDataReceived(p: Position, fromNumber: String) {
        otherLastPos = p
        mapCanvas?.updateOtherPos(p)
        android.os.Handler(mainLooper).post {
            Toast.makeText(this, "📍 Donnée reçue de $fromNumber", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "DATA_RECEIVED") {
            val pos = intent.getSerializableExtra("pos") as? Position
            val from = intent.getStringExtra("from") ?: ""
            pos?.let { onDataReceived(it, from) }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLoc.removeLocationUpdates(locCb)
        floatingView?.let { wm.removeView(it) }
    }

    override fun onBind(i: Intent?): IBinder? = null

    inner class OfflineMapView(ctx: Context) : View(ctx) {
        private val paintBg = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL }
        private val paintGrid = Paint().apply { color = Color.parseColor("#222222"); strokeWidth = 1f }
        private val paintMy = Paint().apply { color = Color.GREEN; style = Paint.Style.FILL }
        private val paintOther = Paint().apply { color = Color.RED; style = Paint.Style.FILL }
        private val paintRing = Paint().apply { color = Color.GREEN; style = Paint.Style.STROKE; strokeWidth = 2f }

        private var myPos: Position? = null
        private var otherPos: Position? = null
        private var centerLat = 47.4784
        private var centerLon = -0.5632
        private val scale = 50000f // 1 pixel = ~50 mètres

        fun updateMyPos(p: Position) {
            myPos = p
            centerLat = p.latitude
            centerLon = p.longitude
            invalidate()
        }

        fun updateOtherPos(p: Position) {
            otherPos = p
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w < 10 || h < 10) return

            // Fond noir
            canvas.drawRect(0f, 0f, w, h, paintBg)

            // Grille
            repeat(11) { i -> canvas.drawLine(i*w/10, 0f, i*w/10, h, paintGrid) }
            repeat(11) { i -> canvas.drawLine(0f, i*h/10, w, i*h/10, paintGrid) }

            // 🟢 MOI — Au centre
            canvas.drawCircle(w/2, h/2, 12f, paintMy)
            canvas.drawCircle(w/2, h/2, 20f, paintRing)

            // 🔴 L'AUTRE — Position relative
            otherPos?.let { other ->
                val dx = (other.longitude - centerLon) * 111000.0 * cos(Math.toRadians(centerLat))
                val dy = -(other.latitude - centerLat) * 111000.0
                val px = (w/2 + (dx / scale.toDouble())).toFloat()
                val py = (h/2 + (dy / scale.toDouble())).toFloat()
                if (px > 10f && px < w-10f && py > 10f && py < h-10f) {
                    canvas.drawCircle(px, py, 12f, paintOther)
                }
            }
        }
    }
}
