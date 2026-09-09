package com.safi.smstracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log

class DataSmsReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "SAFI_SMS"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        context ?: return
        intent ?: return

        Log.d(TAG, "📨 Broadcast reçu ! Action: ${intent.action}")

        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            Log.d(TAG, "❌ Ce n'est pas un SMS_RECEIVED_ACTION")
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        Log.d(TAG, "📬 Nombre de messages: ${messages.size}")

        for ((index, msg) in messages.withIndex()) {
            val expediteur = msg.originatingAddress ?: "??"
            val messageTexte = msg.messageBody ?: ""
            val port = getDestPort(msg)
            
            Log.d(TAG, "📩 Message $index — De: $expediteur, Port: $port, Texte: '$messageTexte'")

            // ✅ Traitement par port OU par texte (fallback si port ne fonctionne pas)
            val isRequest = messageTexte == MainMapActivity.REQUEST_POS || messageTexte == "!!GET_POS"
            val isResponse = messageTexte.startsWith(MainMapActivity.RESPONSE_POS) || messageTexte.startsWith("!!POS:")

            when {
                isRequest -> {
                    Log.d(TAG, "👉 DEMANDE DE POSITION détectée depuis $expediteur")
                    MainMapActivity.instance?.sendMyPositionInResponse(expediteur)
                    abortBroadcast()
                    Log.d(TAG, "✅ Réponse déclenchée")
                }

                isResponse -> {
                    val cleanText = when {
                        messageTexte.startsWith("!!POS:") -> messageTexte.removePrefix("!!POS:")
                        else -> messageTexte.removePrefix(MainMapActivity.RESPONSE_POS)
                    }
                    val parts = cleanText.split(",")
                    if (parts.size == 2) {
                        try {
                            val lat = parts[0].toDouble()
                            val lon = parts[1].toDouble()
                            Log.d(TAG, "👉 POSITION REÇUE depuis $expediteur: $lat, $lon")
                            MainMapActivity.instance?.updateOtherPosition(lat, lon, expediteur)
                            abortBroadcast()
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Erreur parsing", e)
                        }
                    }
                }
                
                port == MainMapActivity.PORT -> {
                    Log.d(TAG, "📥 Message sur port 7777 mais texte inconnu")
                }
            }
        }
    }

    private fun getDestPort(sms: SmsMessage): Int {
        return try {
            val pdu = sms.pdu ?: return 0
            if (pdu.size > 142) {
                val high = pdu[141].toInt() and 0xFF
                val low = pdu[142].toInt() and 0xFF
                (high shl 8) or low
            } else 0
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lecture port", e)
            0
        }
    }
}
