package com.safi.smstracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log

class DataSmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        context ?: return
        intent ?: return

        // ✅ Récupère TOUS les SMS reçus
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        
        for (msg in messages) {
            // ✅ VÉRIFIE SI C'EST UN SMS DE DONNÉES SUR LE BON PORT
            val port = msg.pduPort // Port de destination du SMS de données
            val expediteur = msg.originatingAddress ?: ""
            
            Log.d("SAFI_DATA", "Reçu sur port $port depuis $expediteur")

            // ✅ SI C'EST LE PORT 7777 → ON TRAITE LES DONNÉES
            if (port == FloatingMapService.DESTINATION_PORT.toShort()) {
                val data = msg.userData // Données binaires brutes
                val texte = String(data, Charsets.UTF_8).trim()
                
                Log.d("SAFI_DATA", "Donnée : $texte")

                val position = Position.parse(texte)
                if (position != null) {
                    // ✅ ENVOYER AU SERVICE DE CARTE
                    val serviceIntent = Intent(context, FloatingMapService::class.java).apply {
                        action = "DATA_RECEIVED"
                        putExtra("pos", position)
                        putExtra("from", expediteur)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }

                    // ✅ ANNULER LA DIFFUSION — N'IRA JAMAIS DANS LA MESSAGERIE
                    abortBroadcast()
                    Log.d("SAFI_DATA", "✅ SMS de données intercepté — port $port")
                }
            }
            // ✅ SMS texte normaux ou autres ports → ILS PASSENT, ON NE TOUCHE À RIEN
        }
    }
}
