package com.example.soprintsgr.data

import android.content.Context
import android.util.Log
import com.example.soprintsgr.data.api.LocationRequest
import com.example.soprintsgr.data.api.Messenger
import com.example.soprintsgr.data.api.RetrofitClient

class LocationRepository(private val context: Context) {
    private val sessionManager = SessionManager(context)
    
    companion object {
        private const val TAG = "LocationRepository"
    }

    suspend fun sendLocation(latitude: Double, longitude: Double) {
        try {
            val idMensajero = sessionManager.getIdUsuario()
            if (idMensajero == -1) {
                Log.e(TAG, "No user logged in, cannot send location")
                return
            }
            
            // Get active task ID from ActiveTaskManager
            val activeTaskManager = ActiveTaskManager.getInstance(context)
            val activeTask = activeTaskManager.getActiveTask()
            val idTarea = activeTask?.idTarea
            
            val request = LocationRequest(
                idMensajero = idMensajero,
                idTarea = idTarea,
                latitud = latitude,
                longitud = longitude
            )
            val response = RetrofitClient.api.updateLocation(request)
            if (response.isSuccessful) {
                if (idTarea != null) {
                    Log.d(TAG, "Location sent WITH task: lat=$latitude, lng=$longitude, taskId=$idTarea")
                } else {
                    Log.d(TAG, "Location sent WITHOUT task: lat=$latitude, lng=$longitude")
                }
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
