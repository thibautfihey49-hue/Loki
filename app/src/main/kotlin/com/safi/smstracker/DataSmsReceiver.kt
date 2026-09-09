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

        for (msg in Telephony.Sms.Intents.getMessagesFromIntent(intent)) {
            val port = getPort(msg)
            val expediteur = msg.originatingAddress ?: "??"
            val messageTexte = msg.messageBody ?: ""
            Log.d("SAFI_SMS", "Reçu sur port $port depuis $expediteur : $messageTexte")

            if (port == MainMapActivity.PORT) {
                when {
                    // 📥 DEMANDE DE POSITION → Répondre automatiquement
                    messageTexte == MainMapActivity.REQUEST_POS -> {
                        Log.d("SAFI_SMS", "📨 Demande de position reçue de $expediteur — Envoi réponse...")
                        MainMapActivity.instance?.sendMyPositionInResponse(expediteur)
                        abortBroadcast()
                    }

                    // 📥 RÉPONSE AVEC POSITION → Afficher sur la carte
                    messageTexte.startsWith(MainMapActivity.RESPONSE_POS) -> {
                        val parts = messageTexte.removePrefix(MainMapActivity.RESPONSE_POS).split(",")
                        if (parts.size == 2) {
                            try {
                                val lat = parts[0].toDouble()
                                val lon = parts[1].toDouble()
                                MainMapActivity.instance?.updateOtherPosition(lat, lon, expediteur)
                                abortBroadcast()
                                Log.d("SAFI_SMS", "✅ Position mise à jour: $lat, $lon")
                            } catch (e: Exception) {
                                Log.e("SAFI_SMS", "Erreur parsing position", e)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun getPort(sms: SmsMessage): Int {
        return try {
            val pdu = sms.pdu
            if (pdu.size > 142) ((pdu[141].toInt() and 0xFF) shl 8) or (pdu[142].toInt() and 0xFF)
            else 0
        } catch (e: Exception) { 0 }
    }
}
