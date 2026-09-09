package com.safi.smstracker.model

import java.io.Serializable

data class Position(
    val latitude: Double,
    val longitude: Double
) : Serializable {
    override fun toString(): String = "!!POS:$latitude,$longitude"

    companion object {
        fun parse(msg: String): Position? {
            if (!msg.startsWith("!!POS:")) return null
            val parts = msg.removePrefix("!!POS:").split(",")
            if (parts.size != 2) return null
            return try { Position(parts[0].toDouble(), parts[1].toDouble()) }
            catch (e: Exception) { null }
        }
    }
}
