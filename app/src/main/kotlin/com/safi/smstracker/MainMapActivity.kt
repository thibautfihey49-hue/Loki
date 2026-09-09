package com.safi.smstracker

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
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
        private const val TAG = "SAFI_SMS"
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

        tvStatus.text = "🌍 Carte chargée — Prêt à tester"
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
                result.lastLocation?.let { }
            }
        }
    }

    private fun initButtons() {
        findViewById<Button>(R.id.btnSave).setOnClickListener {
            val num = etOtherNumber.text.toString().trim()
            prefs.edit().putString("OTHER_NUM", num).apply()
            Toast.makeText(this, "✅ Numéro sauvegardé", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnRequest).setOnClickListener {
            val num = prefs.getString("OTHER_NUM", "") ?: ""
            if (num.isEmpty()) {
                Toast.makeText(this, "⚠️ Saisis d'abord un numéro !", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            sendRequestPosition(num)
        }

        findViewById<Button>(R.id.btnCenter).setOnClickListener {
            myLocationOverlay.myLocation?.let {
                mapView.controller.animateTo(it, 12.0, 500L)
            } ?: Toast.makeText(this, "⏳ Position GPS en attente...", Toast.LENGTH_SHORT).show()
        }

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
            Manifest.permission.READ_SMS,
            Manifest.permission.INTERNET
        )
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 100)
        }
    }

    private fun sendRequestPosition(num: String) {
        try {
            Log.d(TAG, "📨 Envoi demande à: $num")
            val smsManager = android.telephony.SmsManager.getDefault()
            
            // ✅ NOUVEAU : Si c'est MON propre numéro → SIMULER la réponse immédiatement
            val myNumber = getMyPhoneNumber()
            if (myNumber.isNotEmpty() && num == myNumber) {
                Log.d(TAG, "🧪 TEST : Envoi à moi-même détecté — Simulation réponse !")
                tvStatus.text = "🧪 TEST — Envoi à soi-même..."
                Toast.makeText(this, "🧪 Mode TEST — Envoi à moi-même !", Toast.LENGTH_SHORT).show()
                
                // Simuler la réponse après 1 seconde
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    sendMyPositionInResponse(num)
                }, 1000)
                return
            }
            
            // Sinon envoyer par SMS normal
            try {
                smsManager.sendDataMessage(num, null, PORT.toShort(), REQUEST_POS.toByteArray(Charsets.UTF_8), null, null)
                Log.d(TAG, "📨 Demande envoyée en SMS data")
            } catch (e: Exception) {
                Log.d(TAG, "⚠️ SMS data échoué, envoi en SMS texte: ${e.message}")
                smsManager.sendTextMessage(num, null, REQUEST_POS, null, null)
            }
            tvStatus.text = "📨 Demande envoyée à $num..."
            Toast.makeText(this, "📨 Demande envoyée !", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            tvStatus.text = "❌ Erreur envoi: ${e.message}"
            Toast.makeText(this, "❌ Erreur: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // 📱 Récupérer mon propre numéro de téléphone
    private fun getMyPhoneNumber(): String {
        return try {
            val tm = getSystemService(Context.TELEPHONY_SERVICE) as android.telephony.TelephonyManager
            val num = tm.line1Number ?: ""
            Log.d(TAG, "📱 Mon numéro: $num")
            num
        } catch (e: Exception) {
            Log.e(TAG, "Impossible de lire mon numéro", e)
            ""
        }
    }

    fun sendMyPositionInResponse(toNumber: String) {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            runOnUiThread {
                tvStatus.text = "⚠️ Permission GPS manquante pour répondre"
                Toast.makeText(this, "⚠️ Accorde la permission GPS d'abord", Toast.LENGTH_LONG).show()
            }
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
            loc?.let {
                val smsText = "$RESPONSE_POS${it.latitude},${it.longitude}"
                Log.d(TAG, "📤 Réponse: $smsText vers $toNumber")
                
                // ✅ Si c'est à moi-même → Mettre à jour directement
                val myNumber = getMyPhoneNumber()
                if (myNumber.isNotEmpty() && toNumber == myNumber) {
                    Log.d(TAG, "🧪 TEST : Mise à jour directe de la position")
                    runOnUiThread {
                        updateOtherPosition(it.latitude, it.longitude, "MOI-TEST")
                    }
                    return@addOnSuccessListener
                }
                
                try {
                    val smsManager = android.telephony.SmsManager.getDefault()
                    try {
                        smsManager.sendDataMessage(toNumber, null, PORT.toShort(), smsText.toByteArray(Charsets.UTF_8), null, null)
                        Log.d(TAG, "📤 Réponse envoyée en SMS data")
                    } catch (e: Exception) {
                        Log.d(TAG, "⚠️ Réponse en SMS texte: ${e.message}")
                        smsManager.sendTextMessage(toNumber, null, smsText, null, null)
                    }
                    runOnUiThread {
                        tvStatus.text = "📤 Réponse envoyée à $toNumber"
                        Toast.makeText(this, "📤 Réponse envoyée !", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        tvStatus.text = "❌ Erreur réponse: ${e.message}"
                        Toast.makeText(this, "❌ Erreur: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            } ?: runOnUiThread {
                tvStatus.text = "⏳ Position GPS pas encore disponible — Active le GPS !"
                Toast.makeText(this, "⏳ Active le GPS d'abord !", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun updateOtherPosition(lat: Double, lon: Double, from: String) {
        lastOtherPosition = GeoPoint(lat, lon)
        runOnUiThread {
            otherMarker?.position = lastOtherPosition
            otherMarker?.title = "Depuis: $from"
            otherMarkerVisible = true
            mapView.invalidate()
            tvStatus.text = "✅ Position reçue de $from : %.4f, %.4f".format(lat, lon)
            Toast.makeText(this, "✅ Position reçue !", Toast.LENGTH_SHORT).show()
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
        if (requestCode == 100) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                Toast.makeText(this, "✅ Toutes permissions accordées", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "⚠️ Certaines permissions sont refusées !", Toast.LENGTH_LONG).show()
            }
        }
    }
}
