package com.safi.smstracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log

class DataSmsReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "SAFI_SMS"
        var sendingJob: Pair<String, Handler>? = null
        var sendingRunnable: Runnable? = null
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        context ?: return
        intent ?: return

        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        for (msg in messages) {
            val from = msg.originatingAddress ?: "??"
            val text = msg.messageBody ?: ""
            Log.d(TAG, "Reçu de $from: $text")

            when {
                // 📥 IL reçoit "Envoie-moi ta position toutes les minutes" → IL démarre l'envoi
                text == Commands.REQUEST_POS_START -> {
                    Log.d(TAG, "👉 DEMANDE SUIVI CONTINU de $from — Je démarre l'envoi !")
                    startSendingPosition(context, from)
                    abortBroadcast()
                }

                // 📥 IL reçoit "Arrête d'envoyer" → IL arrête
                text == Commands.REQUEST_POS_STOP -> {
                    Log.d(TAG, "👉 ARRÊT SUIVI demandé par $from")
                    stopSendingPosition()
                    abortBroadcast()
                }

                // 📥 IL reçoit "Envoie une fois" → IL envoie une fois
                text == Commands.REQUEST_POS_ONCE -> {
                    Log.d(TAG, "👉 DEMANDE POS UNE FOIS de $from")
                    MainMapActivity.instance?.sendMyPositionInResponse(from)
                    abortBroadcast()
                }

                // 📥 TU reçois SA position → TU l'affiches sur la carte
                text.startsWith(Commands.RESPONSE_POS) -> {
                    val data = text.removePrefix(Commands.RESPONSE_POS).split(",")
                    if (data.size == 2) {
                        try {
                            val lat = data[0].toDouble()
                            val lon = data[1].toDouble()
                            Log.d(TAG, "✅ Position de $from: $lat, $lon")
                            MainMapActivity.instance?.updateOtherPosition(lat, lon, from)
                            FloatingWindowService.instance?.updatePosition(lat, lon, from)
                            abortBroadcast()
                        } catch (e: Exception) {
                            Log.e(TAG, "Erreur parsing", e)
                        }
                    }
                }
            }
        }
    }

    // 📤 IL démarre l'envoi de SA position toutes les minutes
    private fun startSendingPosition(context: Context, toNumber: String) {
        stopSendingPosition() // Évite doublon
        
        val handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                MainMapActivity.instance?.sendMyPositionInResponse(toNumber)
                handler.postDelayed(this, 60000) // Toutes les 60 secondes
            }
        }
        
        sendingJob = Pair(toNumber, handler)
        sendingRunnable = runnable
        
        handler.post(runnable) // Envoie immédiatement puis toutes les minutes
        Log.d(TAG, "✅ Envoi position démarré vers $toNumber toutes les minutes")
    }

    // 📤 IL arrête l'envoi
    private fun stopSendingPosition() {
        sendingRunnable?.let { sendingJob?.second?.removeCallbacks(it) }
        sendingJob = null
        sendingRunnable = null
        Log.d(TAG, "🛑 Envoi position arrêté")
    }
}
