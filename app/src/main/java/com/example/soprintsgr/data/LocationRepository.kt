package com.example.soprintsgr.data

import android.util.Log
import com.example.soprintsgr.data.api.LocationRequest
import com.example.soprintsgr.data.api.Messenger
import com.example.soprintsgr.data.api.RetrofitClient

object LocationRepository {
    private const val TAG = "LocationRepository"

    suspend fun sendLocation(latitude: Double, longitude: Double) {
        try {
            val request = LocationRequest(
                idMensajero = 8,
                idTarea = 1,
                latitud = latitude,
                longitud = longitude
            )
            val response = RetrofitClient.api.updateLocation(request)
            if (response.isSuccessful) {
                Log.d(TAG, "Location sent successfully: $latitude, $longitude")
            } else {
                Log.e(TAG, "Failed to send location: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending location", e)
        }
    }

    suspend fun getMessengers(): List<Messenger> {
        return try {
            val response = RetrofitClient.api.getActiveMessengers()
            if (response.isSuccessful) {
                response.body() ?: emptyList()
            } else {
                Log.e(TAG, "Failed to fetch messengers: ${response.code()}")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching messengers", e)
            emptyList()
        }
    }
}
