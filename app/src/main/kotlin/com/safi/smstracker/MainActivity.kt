package com.safi.smstracker

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.all { it.value }
        if (allGranted) {
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
        val prefs = getSharedPreferences("SAFI_CONFIG", MODE_PRIVATE)

        etNum.setText(prefs.getString("OTHER_NUM", ""))

        btnSave.setOnClickListener {
            prefs.edit().putString("OTHER_NUM", etNum.text.toString().trim()).apply()
            Toast.makeText(this, "✅ Sauvegardé", Toast.LENGTH_SHORT).show()
        }

        btnStart.setOnClickListener {
            if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                startService(Intent(this, TrackerService::class.java))
                Toast.makeText(this, "🗺️ Démarré !", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this, "⚠️ Accorde d'abord la permission de localisation", Toast.LENGTH_LONG).show()
                requestNeededPermissions()
            }
        }

        // Demander les permissions au démarrage
        requestNeededPermissions()
    }

    private fun requestNeededPermissions() {
        val permissions = mutableListOf<String>()
        permissions.add(android.Manifest.permission.ACCESS_FINE_LOCATION)
        permissions.add(android.Manifest.permission.SEND_SMS)
        permissions.add(android.Manifest.permission.RECEIVE_SMS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        requestPermissions.launch(permissions.toTypedArray())
    }
}
