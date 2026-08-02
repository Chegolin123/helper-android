package com.helper.app.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.tasks.await

/**
 * Фича 2: геолокация через FusedLocationProvider (Play Services).
 * Возвращает строку «широта, долгота» или null если нет разрешения/недоступно.
 */
class LocationProvider(private val context: Context) {

    @SuppressLint("MissingPermission")
    suspend fun getLocationString(): String? {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return null

        return try {
            val client = LocationServices.getFusedLocationProviderClient(context)
            val location = client.lastLocation.await()
            if (location != null) {
                String.format("%.5f, %.5f", location.latitude, location.longitude)
            } else {
                // Fallback: разовый запрос свежей позиции.
                val current = client.getCurrentLocation(
                    com.google.android.gms.location.Priority.PRIORITY_LOW_POWER, null
                ).await()
                if (current != null) {
                    String.format("%.5f, %.5f", current.latitude, current.longitude)
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }
}
