package com.safi.smstracker

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class FloatingWindowService : Service() {

    companion object {
        var instance: FloatingWindowService? = null
        private var floatingView: View? = null
        private var mapView: MapView? = null
        private var marker: Marker? = null
        private var lastPos: GeoPoint? = null
        private var lastFrom: String = ""
        
        fun show(context: Context) {
            context.startService(Intent(context, FloatingWindowService::class.java))
        }
        
        fun hide(context: Context) {
            instance?.close()
            context.stopService(Intent(context, FloatingWindowService::class.java))
        }
    }

    private lateinit var windowManager: WindowManager

    override fun onCreate() {
        super.onCreate()
        instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createFloatingWindow()
    }

    private fun createFloatingWindow() {
        Configuration.getInstance().load(this, getSharedPreferences("OSM", Context.MODE_PRIVATE))
        
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_map, null)
        mapView = floatingView?.findViewById(R.id.floatingMapView)
        val tvClose = floatingView?.findViewById<TextView>(R.id.tvCloseFloating)
        val tvInfo = floatingView?.findViewById<TextView>(R.id.tvFloatingInfo)

        // 🗺️ Config petite carte
        mapView?.setTileSource(TileSourceFactory.MAPNIK)
        mapView?.setMultiTouchControls(false)
        mapView?.isClickable = false
        mapView?.controller?.setZoom(10.0)

        marker = Marker(mapView).apply {
            icon = resources.getDrawable(android.R.drawable.ic_menu_mylocation)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            position = GeoPoint(0.0, 0.0)
            title = "Position..."
        }
        mapView?.overlays?.add(marker)

        // ❌ Fermer la carte flottante
        tvClose?.setOnClickListener { close() }

        // 📐 Dimensions de la fenêtre flottante
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            300, // Largeur
            350, // Hauteur
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.END
        params.x = 20
        params.y = 100

        windowManager.addView(floatingView, params)
        
        lastPos?.let { pos -> updatePosition(pos.latitude, pos.longitude, lastFrom) }
    }

    fun updatePosition(lat: Double, lon: Double, from: String) {
        lastPos = GeoPoint(lat, lon)
        lastFrom = from
        val info = floatingView?.findViewById<TextView>(R.id.tvFloatingInfo)
        info?.text = "📍 $from\n%.4f, %.4f".format(lat, lon)
        marker?.position = lastPos
        marker?.title = "Depuis: $from"
        mapView?.controller?.animateTo(lastPos, 12.0, 300L)
        mapView?.invalidate()
    }

    fun close() {
        try { windowManager.removeView(floatingView) } catch (e: Exception) {}
        floatingView = null
        mapView = null
        instance = null
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { close(); super.onDestroy() }
}
