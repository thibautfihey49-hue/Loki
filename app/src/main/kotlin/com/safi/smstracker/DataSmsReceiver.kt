package com.safi.smstracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import com.safi.smstracker.model.Position

class DataSmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        context ?: return
        intent ?: return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        
        for (msg in messages) {
            val expediteur = msg.originatingAddress ?: ""
            val port = getDestinationPort(msg)
            
            Log.d("SAFI_DATA", "Reçu sur port $port depuis $expediteur")

            if (port == 7777) {
                val data = msg.messageBody ?: ""
                val position = Position.parse(data)
                if (position != null) {
                    val serviceIntent = Intent(context, FloatingMapService::class.java).apply {
                        action = "DATA_RECEIVED"
                        putExtra("pos", position as java.io.Serializable)
                        putExtra("from", expediteur)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                    abortBroadcast()
                    Log.d("SAFI_DATA", "✅ SMS de données intercepté")
                }
            }
        }
    }

    private fun getDestinationPort(sms: SmsMessage): Int {
        return try {
            val pdu = sms.pdu
            if (pdu.size > 142) {
                ((pdu[141].toInt() and 0xFF) shl 8) or (pdu[142].toInt() and 0xFF)
            } else 0
        } catch (e: Exception) {
            0
        }
    }
}
