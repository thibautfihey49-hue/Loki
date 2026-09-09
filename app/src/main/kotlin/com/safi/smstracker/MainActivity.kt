package com.safi.smstracker

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var etMyNumber: EditText
    private lateinit var etOtherNumber: EditText
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var btnCamera: Button
    private lateinit var btnGallery: Button

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms.all { it.value }) checkOverlayPermission()
        else Toast.makeText(this, "❌ Permissions requises", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = getSharedPreferences("SAFI_CONFIG", MODE_PRIVATE)
        initViews()
        loadSavedConfig()
        checkPermissions()
    }

    private fun initViews() {
        etMyNumber = findViewById(R.id.etMyNumber)
        etOtherNumber = findViewById(R.id.etOtherNumber)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        btnCamera = findViewById(R.id.btnCamera)
        btnGallery = findViewById(R.id.btnGallery)

        btnStart.setOnClickListener { startTracking() }
        btnStop.setOnClickListener { stopTracking() }
        btnCamera.setOnClickListener { startActivity(Intent(this, HiddenCameraActivity::class.java)) }
        btnGallery.setOnClickListener { startActivity(Intent(this, PrivateGalleryActivity::class.java)) }
    }

    private fun loadSavedConfig() {
        etMyNumber.setText(prefs.getString("MY_NUMBER", ""))
        etOtherNumber.setText(prefs.getString("OTHER_NUMBER", ""))
    }

    private fun saveConfig() {
        prefs.edit()
            .putString("MY_NUMBER", etMyNumber.text.toString().trim())
            .putString("OTHER_NUMBER", etOtherNumber.text.toString().trim())
            .apply()
    }

    private fun checkPermissions() {
        val needed = mutableListOf<String>()
        listOf(Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS,
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.CAMERA).forEach {
            if (ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED) needed.add(it)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.POST_NOTIFICATIONS)

        if (needed.isNotEmpty()) requestPermissions.launch(needed.toTypedArray())
        else checkOverlayPermission()
    }

    private fun checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            Toast.makeText(this, "👉 Accorde l'autorisation d'affichage par-dessus", Toast.LENGTH_LONG).show()
        }
    }

    private fun startTracking() {
        val my = etMyNumber.text.toString().trim()
        val other = etOtherNumber.text.toString().trim()
        if (my.isEmpty() || other.isEmpty()) {
            Toast.makeText(this, "⚠️ Remplis les deux numéros", Toast.LENGTH_SHORT).show()
            return
        }
        saveConfig()
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "⚠️ Accorde d'abord l'autorisation d'affichage", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(this, FloatingMapService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
        else startService(intent)
        Toast.makeText(this, "✅ Tracking DÉMARRÉ", Toast.LENGTH_LONG).show()
        finish()
    }

    private fun stopTracking() {
        stopService(Intent(this, FloatingMapService::class.java))
        Toast.makeText(this, "⏹️ Tracking ARRÊTÉ", Toast.LENGTH_SHORT).show()
    }
}
