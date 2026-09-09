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
import android.graphics.RectF
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.telephony.SmsManager
import android.view.Gravity
import android.view.MotionEvent
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
    private var mapView: InteractiveMapView? = null
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
            .setContentText("🗺️ Carte active — Pincer pour zoomer / Glisser pour se déplacer")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()
    }

    private fun initMap() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val display = wm.defaultDisplay
        val size = Point()
        display.getSize(size)
        val side = (size.x * 0.75).toInt() // PLUS GRANDE : 75% de l'écran

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(side, side, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.CENTER or Gravity.BOTTOM
            x = 0
            y = 40
        }

        mapView = InteractiveMapView(this)
        wm.addView(mapView, params)
    }

    private fun initGPS() {
        fused = LocationServices.getFusedLocationProviderClient(this)
        locReq = LocationRequest.Builder(3000)
            .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY).build()
        
        locCb = object : LocationCallback() {
            override fun onLocationResult(r: LocationResult) {
                r.lastLocation?.let { loc ->
                    myPos = Position(loc.latitude, loc.longitude)
                    mapView?.updateMyPos(myPos!!)
                    sendMyPosition()
                }
            }
        }

        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == 
            android.content.pm.PackageManager.PERMISSION_GRANTED) {
            fused.requestLocationUpdates(locReq, locCb, mainLooper)
            fused.lastLocation.addOnSuccessListener { loc ->
                loc?.let { 
                    myPos = Position(it.latitude, it.longitude)
                    mapView?.updateMyPos(myPos!!)
                }
            }
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
                mapView?.updateOtherPos(pos)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        running = false
        try { fused.removeLocationUpdates(locCb) } catch (e: Exception) {}
        mapView?.let { wm.removeView(it) }
    }

    override fun onBind(i: Intent?): IBinder? = null

    inner class InteractiveMapView(ctx: Context) : View(ctx) {
        // 🎨 Pinceaux
        private val bg = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL }
        private val grid = Paint().apply { color = Color.parseColor("#333333"); strokeWidth = 1f }
        private val dotMe = Paint().apply { color = Color.GREEN; style = Paint.Style.FILL }
        private val dotOther = Paint().apply { color = Color.RED; style = Paint.Style.FILL }
        private val ring = Paint().apply { color = Color.GREEN; style = Paint.Style.STROKE; strokeWidth = 3f }
        private val textPaint = Paint().apply { color = Color.WHITE; textSize = 22f; isFakeBoldText = true }
        private val btnCenter = Paint().apply { color = Color.parseColor("#444444"); style = Paint.Style.FILL }
        private val btnBorder = Paint().apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 2f }

        // 📍 Position de la caméra
        private var centerLat = 47.4784
        private var centerLon = -0.5632
        private var scale = 50000.0 // mètres par pixel
        private val minScale = 5000.0   // Max zoom : 5km
        private val maxScale = 500000.0 // Min zoom : 500km

        // 📍 Positions des points
        private var myPosition: Position? = null
        private var otherPosition: Position? = null

        // 👆 Gestion tactile
        private var lastTouchX = 0f
        private var lastTouchY = 0f
        private var touchStartX = 0f
        private var touchStartY = 0f
        private var isDragging = false
        private var lastFingerSpacing = 0f

        fun updateMyPos(p: Position) {
            myPosition = p
            invalidate()
        }

        fun updateOtherPos(p: Position) {
            otherPosition = p
            invalidate()
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val w = width.toFloat()
            val h = height.toFloat()
            if (w < 10 || h < 10) return true

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastTouchX = event.x
                    lastTouchY = event.y
                    touchStartX = event.x
                    touchStartY = event.y
                    isDragging = false
                }

                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (event.pointerCount == 2) {
                        lastFingerSpacing = getFingerSpacing(event)
                    }
                }

                MotionEvent.ACTION_MOVE -> {
                    if (event.pointerCount == 2) {
                        // 🤏 PINCER = ZOOM
                        val spacing = getFingerSpacing(event)
                        val zoomFactor = lastFingerSpacing / spacing
                        scale *= zoomFactor
                        scale = scale.coerceIn(minScale, maxScale)
                        lastFingerSpacing = spacing
                        invalidate()
                    } else if (event.pointerCount == 1) {
                        // 👆 GLISSER = DÉPLACER
                        val dx = event.x - lastTouchX
                        val dy = event.y - lastTouchY
                        
                        if (abs(event.x - touchStartX) > 10f || abs(event.y - touchStartY) > 10f) {
                            isDragging = true
                        }

                        val latPerPixel = scale / 111000.0
                        val lonPerPixel = scale / (111000.0 * cos(Math.toRadians(centerLat)))
                        
                        centerLon -= dx * lonPerPixel
                        centerLat += dy * latPerPixel
                        
                        lastTouchX = event.x
                        lastTouchY = event.y
                        invalidate()
                    }
                }

                MotionEvent.ACTION_UP -> {
                    // 🖱️ CLIC = recentrer si pas de déplacement
                    if (!isDragging && abs(event.x - touchStartX) < 10f && abs(event.y - touchStartY) < 10f) {
                        // Vérifie si clic sur bouton "Recentrer"
                        val btnRect = RectF(w - 100f, h - 100f, w - 20f, h - 20f)
                        if (btnRect.contains(event.x, event.y)) {
                            myPosition?.let {
                                centerLat = it.latitude
                                centerLon = it.longitude
                                invalidate()
                            }
                        }
                    }
                }
            }
            return true
        }

        private fun getFingerSpacing(event: MotionEvent): Float {
            val x = event.getX(0) - event.getX(1)
            val y = event.getY(0) - event.getY(1)
            return sqrt(x*x + y*y)
        }

        private fun latLonToPixel(lat: Double, lon: Double, w: Float, h: Float): Pair<Float, Float> {
            val dx = (lon - centerLon) * 111000.0 * cos(Math.toRadians(centerLat))
            val dy = -(lat - centerLat) * 111000.0
            val px = w/2 + (dx / scale).toFloat()
            val py = h/2 + (dy / scale).toFloat()
            return Pair(px, py)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w < 10 || h < 10) return

            // 🖼️ Fond
            canvas.drawRect(0f, 0f, w, h, bg)

            // 📐 Grille dynamique
            val gridStep = when {
                scale < 15000 -> 1000    // 1km
                scale < 50000 -> 5000    // 5km
                scale < 150000 -> 20000  // 20km
                else -> 100000           // 100km
            }
            val pixelsPerGrid = gridStep / scale
            val gridCountX = (w / pixelsPerGrid).toInt() + 2
            val gridCountY = (h / pixelsPerGrid).toInt() + 2
            val offsetX = ((-centerLon * 111000.0 * cos(Math.toRadians(centerLat))) / scale % pixelsPerGrid).toFloat()
            val offsetY = ((centerLat * 111000.0) / scale % pixelsPerGrid).toFloat()

            for (i in -gridCountX/2..gridCountX/2) {
                val x = w/2 + offsetX + i * pixelsPerGrid
                canvas.drawLine(x, 0f, x, h, grid)
            }
            for (i in -gridCountY/2..gridCountY/2) {
                val y = h/2 + offsetY + i * pixelsPerGrid
                canvas.drawLine(0f, y, w, y, grid)
            }

            // 🟢 MOI
            myPosition?.let { pos ->
                val (px, py) = latLonToPixel(pos.latitude, pos.longitude, w, h)
                canvas.drawCircle(px, py, 16f, dotMe)
                canvas.drawCircle(px, py, 26f, ring)
            }

            // 🔴 L'AUTRE
            otherPosition?.let { pos ->
                val (px, py) = latLonToPixel(pos.latitude, pos.longitude, w, h)
                canvas.drawCircle(px, py, 16f, dotOther)
                // Ligne entre les deux
                myPosition?.let { my ->
                    val (mx, myy) = latLonToPixel(my.latitude, my.longitude, w, h)
                    canvas.drawLine(mx, myy, px, py, Paint().apply { 
                        color = Color.YELLOW
                        strokeWidth = 1.5f
                        style = Paint.Style.STROKE
                        alpha = 128
                    })
                }
            }

            // 🔘 Bouton Recentrer (en bas à droite)
            val btnX = w - 100f
            val btnY = h - 100f
            canvas.drawCircle(btnX + 40f, btnY + 40f, 35f, btnCenter)
            canvas.drawCircle(btnX + 40f, btnY + 40f, 35f, btnBorder)
            canvas.drawText("⌖", btnX + 25f, btnY + 52f, textPaint)

            // 📏 Échelle
            val scaleText = when {
                scale < 15000 -> "1 carré = 1km"
                scale < 50000 -> "1 carré = 5km"
                scale < 150000 -> "1 carré = 20km"
                else -> "1 carré = 100km"
            }
            canvas.drawText(scaleText, 20f, 40f, textPaint)
        }
    }
}
