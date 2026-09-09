package com.safi.smstracker

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class MainMapActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var prefs: SharedPreferences
    private lateinit var etOtherNumber: EditText
    private lateinit var tvStatus: TextView
    private lateinit var myLocationOverlay: MyLocationNewOverlay
    private var otherMarker: Marker? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private var otherMarkerVisible = false

    companion object {
        const val PORT = 7777
        var lastOtherPosition: GeoPoint? = null
        var instance: MainMapActivity? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().load(this, getSharedPreferences("OSM", MODE_PRIVATE))
        setContentView(R.layout.activity_main_map)
        instance = this

        prefs = getSharedPreferences("SAFI_CONFIG", MODE_PRIVATE)
        mapView = findViewById(R.id.mapView)
        etOtherNumber = findViewById(R.id.etOtherNumber)
        tvStatus = findViewById(R.id.tvStatus)

        etOtherNumber.setText(prefs.getString("OTHER_NUM", ""))

        initMap()
        initGPS()
        initButtons()
        checkPermissions()
    }

    private fun initMap() {
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(12.0)
        mapView.controller.setCenter(GeoPoint(47.4784, -0.5632))

        myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), mapView)
        myLocationOverlay.enableMyLocation()
        myLocationOverlay.enableFollowLocation()
        mapView.overlays.add(myLocationOverlay)

        otherMarker = Marker(mapView).apply {
            icon = ContextCompat.getDrawable(this@MainMapActivity, android.R.drawable.ic_menu_mylocation)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "Autre personne"
            position = GeoPoint(0.0, 0.0)
        }
        mapView.overlays.add(otherMarker)
        otherMarkerVisible = false

        tvStatus.text = "🌍 Carte chargée — Tuiles OSM en ligne"
    }

    private fun initGPS() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationRequest = LocationRequest.Builder(5000)
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMinUpdateIntervalMillis(3000)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                super.onLocationResult(result)
                result.lastLocation?.let { loc ->
                    val myPos = GeoPoint(loc.latitude, loc.longitude)
                    sendPositionToOther(myPos)
                }
            }
        }
    }

    private fun initButtons() {
        findViewById<Button>(R.id.btnSave).setOnClickListener {
            val num = etOtherNumber.text.toString().trim()
            prefs.edit().putString("OTHER_NUM", num).apply()
            Toast.makeText(this, "✅ Numéro sauvegardé", Toast.LENGTH_SHORT).show()
            startLocationUpdates()
        }

        findViewById<Button>(R.id.btnCenter).setOnClickListener {
            myLocationOverlay.myLocation?.let {
                mapView.controller.animateTo(it, 12.0, 500L)
            } ?: Toast.makeText(this, "⏳ Position GPS en attente...", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnClear).setOnClickListener {
            lastOtherPosition = null
            otherMarkerVisible = false
            otherMarker?.position = GeoPoint(0.0, 0.0)
            mapView.invalidate()
            tvStatus.text = "⏹️ Réinitialisé"
            Toast.makeText(this, "⏹️ Réinitialisé", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkPermissions() {
        val needed = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.INTERNET
        )
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 100)
        } else {
            startLocationUpdates()
        }
    }

    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, mainLooper)
            tvStatus.text = "✅ GPS actif — Envoi position toutes les 5s"
        }
    }

    private fun sendPositionToOther(pos: GeoPoint) {
        val num = prefs.getString("OTHER_NUM", "") ?: return
        if (num.isEmpty()) return

        try {
            val smsText = "!!POS:${pos.latitude},${pos.longitude}"
            android.telephony.SmsManager.getDefault().sendDataMessage(
                num, null, PORT.toShort(), smsText.toByteArray(Charsets.UTF_8), null, null
            )
        } catch (e: Exception) {
            tvStatus.text = "❌ Erreur SMS: ${e.message}"
        }
    }

    fun updateOtherPosition(lat: Double, lon: Double, from: String) {
        lastOtherPosition = GeoPoint(lat, lon)
        runOnUiThread {
            otherMarker?.position = lastOtherPosition
            otherMarker?.title = "Depuis: $from"
            otherMarkerVisible = true
            mapView.invalidate()
            tvStatus.text = "📍 Autre: %.4f, %.4f".format(lat, lon)
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        instance = this
        lastOtherPosition?.let { pos ->
            otherMarker?.position = pos
            otherMarkerVisible = true
        }
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
        instance = null
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            startLocationUpdates()
        }
    }
}
