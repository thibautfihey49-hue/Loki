package com.safi.smstracker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
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
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.safi.smstracker.databinding.ViewFloatingMapBinding
import com.safi.smstracker.model.Position
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker

class FloatingMapService : Service() {

    companion object {
        const val CHANNEL_ID = "SAFI_SMS_TRACKER"
        const val ACTION_UPDATE_POS = "com.safi.UPDATE_POSITION"
        const val EXTRA_POS = "position"
        var myLastPos: Position? = null
        var otherLastPos: Position? = null
    }

    private lateinit var wm: WindowManager
    private lateinit var binding: ViewFloatingMapBinding
    private lateinit var fusedLoc: FusedLocationProviderClient
    private lateinit var locReq: LocationRequest
    private lateinit var locCb: LocationCallback
    private var floatingView: View? = null
    private var myMarker: Marker? = null
    private var otherMarker: Marker? = null
    private lateinit var prefs: SharedPreferences

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("SAFI_CONFIG", Context.MODE_PRIVATE)
        createChannel()
        startForeground(1, createNotif(), 8)
        initFloatingWindow()
        initMap()
        initLocation()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "SAFI SMS Tracker", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun createNotif(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SAFI SMS Tracker 📡")
            .setContentText("Échange de positions actif")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun initFloatingWindow() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        binding = ViewFloatingMapBinding.inflate(getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater)
        floatingView = binding.root

        // ✅ TAILLE MOYENNE — 80% de l'écran, PAS plein écran !
        val display = wm.defaultDisplay
        val size = Point()
        display.getSize(size)
        val screenW = size.x
        val screenH = size.y
        val w = (screenW * 0.85).toInt()
        val h = (screenH * 0.65).toInt()

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            w, h,  // ✅ TAILLE FIXE — PLUS PETITE !
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER  // ✅ Centrée sur l'écran
        }
        wm.addView(floatingView, params)
        binding.btnCloseMap.setOnClickListener { stopSelf() }
    }

    private fun initMap() {
        Configuration.getInstance().load(this, prefs)
        Configuration.getInstance().userAgentValue = packageName
        binding.mapView.setTileSource(TileSourceFactory.MAPNIK)
        binding.mapView.setMultiTouchControls(true)
        binding.mapView.controller?.setZoom(15.0)
        val defaultPos = GeoPoint(47.4784, -0.5632)
        binding.mapView.controller?.setCenter(defaultPos)
        myMarker = Marker(binding.mapView).apply {
            icon = resources.getDrawable(android.R.drawable.presence_online, null)
            title = "📍 MOI"
            position = defaultPos
        }
        otherMarker = Marker(binding.mapView).apply {
            icon = resources.getDrawable(android.R.drawable.presence_busy, null)
            title = "👤 L'AUTRE"
            position = defaultPos
        }
        binding.mapView.overlays.addAll(listOf(myMarker!!, otherMarker!!))
        binding.mapView.invalidate()
    }

    private fun initLocation() {
        fusedLoc = LocationServices.getFusedLocationProviderClient(this)
        locReq = LocationRequest.Builder(10000).setMinUpdateIntervalMillis(8000)
            .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY).build()
        locCb = object : LocationCallback() {
            override fun onLocationResult(r: LocationResult) {
                super.onLocationResult(r)
                r.lastLocation?.let { loc ->
                    val pos = Position(loc.latitude, loc.longitude, true)
                    myLastPos = pos
                    updateMyMarker(pos)
                    sendBySms(pos)
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
                    updateMyMarker(pos)
                }
            }
        }
    }

    private fun updateMyMarker(p: Position) {
        val gp = GeoPoint(p.latitude, p.longitude)
        myMarker?.position = gp
        binding.mapView.controller?.animateTo(gp)
        binding.mapView.invalidate()
    }

    fun updateOtherPos(p: Position) {
        otherLastPos = p
        otherMarker?.position = GeoPoint(p.latitude, p.longitude)
        binding.mapView.invalidate()
    }

    private fun sendBySms(p: Position) {
        val other = prefs.getString("OTHER_NUMBER", null) ?: return
        try {
            SmsManager.getDefault().sendTextMessage(other, null, p.toString(), null, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_UPDATE_POS) {
            intent.getParcelableExtra<Position>(EXTRA_POS)?.let { updateOtherPos(it) }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLoc.removeLocationUpdates(locCb)
        floatingView?.let { wm.removeView(it) }
    }

    override fun onBind(i: Intent?): IBinder? = null
}
