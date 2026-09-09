package com.safi.smstracker

import android.content.Intent
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

class MainActivity : AppCompatActivity() {

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.all { it.value }) {
            Toast.makeText(this, "✅ Toutes permissions accordées !", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "⚠️ Certaines permissions sont manquantes !", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val etNum = findViewById<EditText>(R.id.et_numero)
        val btnSave = findViewById<Button>(R.id.btn_save)
        val btnStart = findViewById<Button>(R.id.btn_start)
        val btnOverlay = findViewById<Button>(R.id.btn_overlay)
        val prefs = getSharedPreferences("SAFI_CONFIG", MODE_PRIVATE)

        etNum.setText(prefs.getString("OTHER_NUM", ""))

        btnSave.setOnClickListener {
            prefs.edit().putString("OTHER_NUM", etNum.text.toString().trim()).apply()
            Toast.makeText(this, "✅ Sauvegardé", Toast.LENGTH_SHORT).show()
        }

        btnOverlay.setOnClickListener {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
            Toast.makeText(this, "👉 Active \"Afficher par-dessus les autres apps\"", Toast.LENGTH_LONG).show()
        }

        btnStart.setOnClickListener {
            when {
                !Settings.canDrawOverlays(this) -> {
                    Toast.makeText(this, "⚠️ D'abord accorde l'affichage par-dessus !", Toast.LENGTH_LONG).show()
                }
                checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED -> {
                    Toast.makeText(this, "⚠️ Accorde d'abord la localisation", Toast.LENGTH_LONG).show()
                }
                else -> {
                    startService(Intent(this, TrackerService::class.java))
                    Toast.makeText(this, "🗺️ Carte démarrée ! Regarde en haut à droite", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }

        requestNeededPermissions()
    }

    private fun requestNeededPermissions() {
        val permissions = mutableListOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.SEND_SMS,
            android.Manifest.permission.RECEIVE_SMS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        requestPermissions.launch(permissions.toTypedArray())
    }
}
