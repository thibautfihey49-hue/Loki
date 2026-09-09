package com.safi.smstracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        context ?: return
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("SAFI_BOOT", "✅ Téléphone démarré — Redémarrage du service...")
            PersistentTrackingService.start(context)
        }
    }
}
