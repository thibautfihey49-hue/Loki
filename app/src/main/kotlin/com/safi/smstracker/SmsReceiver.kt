package com.safi.smstracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SmsMessage
import com.safi.smstracker.model.Position

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val bundle = intent.extras ?: return
        val pdus = bundle.get("pdus") as? Array<*> ?: return
        for (pdu in pdus) {
            val sms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                SmsMessage.createFromPdu(pdu as ByteArray, bundle.getString("format"))
            else @Suppress("DEPRECATION") SmsMessage.createFromPdu(pdu as ByteArray)
            val body = sms.messageBody
            if (body.startsWith("!!POS:")) {
                Position.parse(body)?.let {
                    it.isMyPosition = false
                    ctx.startService(Intent(ctx, FloatingMapService::class.java).apply {
                        action = FloatingMapService.ACTION_UPDATE_POS
                        putExtra(FloatingMapService.EXTRA_POS, it)
                    })
                }
            }
        }
    }
}
