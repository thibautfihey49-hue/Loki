package com.safi.smstracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import android.widget.Toast
import com.safi.smstracker.model.Position

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        context ?: return
        intent ?: return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        for (msg in messages) {
            val texte = msg.messageBody ?: ""
            val expediteur = msg.originatingAddress ?: ""

            Log.d("SAFI_SMS", "Reçu de $expediteur : $texte")

            if (texte.startsWith("!!POS:")) {
                val position = Position.parse(texte)
                if (position != null) {
                    // ✅ Mettre à jour la carte
                    val serviceIntent = Intent(context, FloatingMapService::class.java).apply {
                        action = FloatingMapService.ACTION_UPDATE_POS
                        putExtra(FloatingMapService.EXTRA_POS, position)
                    }
                    context.startService(serviceIntent)

                    // ⚠️ abortBroadcast() NE SUFFIT PAS sur Android 13+ —
                    // Il faut aussi la permission "SMS par défaut"
                    try {
                        abortBroadcast()
                        Log.d("SAFI_SMS", "✅ Position interceptée !")
                    } catch (e: Exception) {
                        Log.d("SAFI_SMS", "⚠️ Impossible de masquer le SMS — l'app doit être messagerie par défaut")
                    }

                    Toast.makeText(context, "📍 Position reçue !", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
