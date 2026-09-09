package com.safi.smstracker.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.io.Serializable

@Parcelize
data class Position(
    val latitude: Double,
    val longitude: Double,
    var isMyPosition: Boolean,
    val timestamp: Long = System.currentTimeMillis()
) : Parcelable, Serializable {
    override fun toString(): String = "!!POS:$latitude,$longitude"

    companion object {
        fun parse(message: String): Position? {
            if (!message.startsWith("!!POS:")) return null
            val coords = message.removePrefix("!!POS:").split(",")
            if (coords.size != 2) return null
            return try { 
                Position(coords[0].toDouble(), coords[1].toDouble(), isMyPosition = false) 
            } catch (e: Exception) { 
                null 
            }
        }
    }
}
