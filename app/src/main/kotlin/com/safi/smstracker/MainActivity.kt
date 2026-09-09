package com.safi.smstracker

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val etNum = findViewById<EditText>(R.id.et_numero)
        val btnSave = findViewById<Button>(R.id.btn_save)
        val btnStart = findViewById<Button>(R.id.btn_start)

        val prefs = getSharedPreferences("SAFI_CONFIG", MODE_PRIVATE)
        etNum.setText(prefs.getString("OTHER_NUMBER", ""))

        btnSave.setOnClickListener {
            prefs.edit().putString("OTHER_NUMBER", etNum.text.toString().trim()).apply()
            Toast.makeText(this, "✅ Numéro sauvegardé", Toast.LENGTH_SHORT).show()
        }

        btnStart.setOnClickListener {
            startService(Intent(this, FloatingMapService::class.java))
            Toast.makeText(this, "📡 SMS de données démarré — Port 7777", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
