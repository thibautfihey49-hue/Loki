package com.safi.smstracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsMessage
import com.safi.smstracker.model.Position

class DataSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        context ?: return
        intent ?: return

        for (msg in Telephony.Sms.Intents.getMessagesFromIntent(intent)) {
            val port = getPort(msg)
            if (port == 7777) {
                val text = msg.messageBody ?: ""
                val pos = Position.parse(text)
                if (pos != null) {
                    val i = Intent(context, TrackerService::class.java)
                    i.action = "NEW_POS"
                    i.putExtra("pos", pos)
                    i.putExtra("from", msg.originatingAddress ?: "")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        context.startForegroundService(i)
                    else
                        context.startService(i)
                    abortBroadcast()
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
