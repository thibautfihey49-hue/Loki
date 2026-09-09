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
        const val REQUEST_POS = "!!GET_POS"
        const val RESPONSE_POS = "!!POS:"
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

        tvStatus.text = "🌍 Carte chargée — Prêt à échanger"
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
                    // Rien à faire ici — on envoie seulement quand demandé
                }
            }
        }
    }

    private fun initButtons() {
        findViewById<Button>(R.id.btnSave).setOnClickListener {
            val num = etOtherNumber.text.toString().trim()
            prefs.edit().putString("OTHER_NUM", num).apply()
            Toast.makeText(this, "✅ Numéro sauvegardé", Toast.LENGTH_SHORT).show()
        }

        // 📍 NOUVEAU : Demander la position de l'autre
        findViewById<Button>(R.id.btnRequest).setOnClickListener {
            val num = prefs.getString("OTHER_NUM", "") ?: ""
            if (num.isEmpty()) {
                Toast.makeText(this, "⚠️ Saisis d'abord le numéro de l'autre !", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            sendRequestPosition(num)
        }

        findViewById<Button>(R.id.btnCenter).setOnClickListener {
            myLocationOverlay.myLocation?.let {
                mapView.controller.animateTo(it, 12.0, 500L)
            } ?: Toast.makeText(this, "⏳ Position GPS en attente...", Toast.LENGTH_SHORT).show()
        }

        // 🔴 NOUVEAU : Aller vers la position de l'autre
        findViewById<Button>(R.id.btnGoToOther).setOnClickListener {
            lastOtherPosition?.let { pos ->
                mapView.controller.animateTo(pos, 14.0, 600L)
                Toast.makeText(this, "🔴 Position de l'autre", Toast.LENGTH_SHORT).show()
            } ?: Toast.makeText(this, "⚠️ Pas encore de position reçue", Toast.LENGTH_SHORT).show()
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
        }
    }

    // 📨 Envoyer une demande de position
    private fun sendRequestPosition(num: String) {
        try {
            android.telephony.SmsManager.getDefault().sendDataMessage(
                num, null, PORT.toShort(), REQUEST_POS.toByteArray(Charsets.UTF_8), null, null
            )
            tvStatus.text = "📨 Demande envoyée à $num..."
            Toast.makeText(this, "📨 Demande envoyée !", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            tvStatus.text = "❌ Erreur envoi: ${e.message}"
            Toast.makeText(this, "❌ Erreur: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // 📤 Envoyer MA position en réponse à une demande
    fun sendMyPositionInResponse(toNumber: String) {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            runOnUiThread {
                tvStatus.text = "⚠️ Permission GPS manquante pour répondre"
            }
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
            loc?.let {
                val smsText = "$RESPONSE_POS${it.latitude},${it.longitude}"
                try {
                    android.telephony.SmsManager.getDefault().sendDataMessage(
                        toNumber, null, PORT.toShort(), smsText.toByteArray(Charsets.UTF_8), null, null
                    )
                    runOnUiThread {
                        tvStatus.text = "📤 Réponse envoyée à $toNumber"
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        tvStatus.text = "❌ Erreur réponse: ${e.message}"
                    }
                }
            } ?: runOnUiThread {
                tvStatus.text = "⏳ GPS pas encore prêt pour répondre"
            }
        }
    }

    // 📥 Mettre à jour la position de l'autre sur la carte
    fun updateOtherPosition(lat: Double, lon: Double, from: String) {
        lastOtherPosition = GeoPoint(lat, lon)
        runOnUiThread {
            otherMarker?.position = lastOtherPosition
            otherMarker?.title = "Depuis: $from"
            otherMarkerVisible = true
            mapView.invalidate()
            tvStatus.text = "✅ Position reçue de $from : %.4f, %.4f".format(lat, lon)
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
            Toast.makeText(this, "✅ Toutes permissions accordées", Toast.LENGTH_SHORT).show()
        }
    }
}
