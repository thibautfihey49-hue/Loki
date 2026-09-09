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
            Log.d("SAFI_SMS", "Reçu sur port $port depuis $expediteur")

            if (port == MainMapActivity.PORT) {
                val text = msg.messageBody ?: ""
                if (text.startsWith("!!POS:")) {
                    val parts = text.removePrefix("!!POS:").split(",")
                    if (parts.size == 2) {
                        try {
                            val lat = parts[0].toDouble()
                            val lon = parts[1].toDouble()
                            
                            if (MainMapActivity.lastOtherPosition == null ||
                                MainMapActivity.lastOtherPosition!!.latitude != lat ||
                                MainMapActivity.lastOtherPosition!!.longitude != lon) {
                                
                                val actCls = Class.forName("com.safi.smstracker.MainMapActivity")
                                val updateMethod = actCls.getMethod("updateOtherPosition", 
                                    Double::class.java, Double::class.java, String::class.java)
                                updateMethod.invoke(context, lat, lon, expediteur)
                            }
                            
                            abortBroadcast()
                            Log.d("SAFI_SMS", "✅ Position mise à jour: $lat, $lon")
                        } catch (e: Exception) {
                            Log.e("SAFI_SMS", "Erreur parsing", e)
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
