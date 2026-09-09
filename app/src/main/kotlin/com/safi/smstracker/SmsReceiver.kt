package com.safi.smstracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import com.safi.smstracker.model.Position

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        context ?: return
        intent ?: return

        // ✅ Récupérer TOUS les SMS reçus
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        for (msg in messages) {
            val texte = msg.messageBody ?: ""
            val expediteur = msg.originatingAddress ?: ""

            Log.d("SAFI_SMS", "Reçu de $expediteur : $texte")

            // ✅ VÉRIFIER SI C'EST UNE POSITION
            if (texte.startsWith("!!POS:")) {
                // ✅ C'EST UNE POSITION → TRAITER DANS L'APP
                val position = Position.parse(texte)
                if (position != null) {
                    // ✅ ENVOYER AU SERVICE DE CARTE FLOTTANTE
                    val serviceIntent = Intent(context, FloatingMapService::class.java).apply {
                        action = FloatingMapService.ACTION_UPDATE_POS
                        putExtra(FloatingMapService.EXTRA_POS, position)
                    }
                    context.startService(serviceIntent)

                    // ✅ SUPPRIMER DES NOTIFICATIONS ET DE LA MESSAGERIE SYSTÈME
                    abortBroadcast()
                    Log.d("SAFI_SMS", "✅ Position interceptée — pas dans la messagerie")
                }
            }
            // ✅ Sinon : SMS normal → il va dans la messagerie normalement
        }
    }
}
