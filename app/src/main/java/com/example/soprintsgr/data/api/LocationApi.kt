package com.example.soprintsgr.data.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

data class LocationRequest(
    val idMensajero: Int,
    val idTarea: Int,
    val latitud: Double,
    val longitud: Double
)

data class Messenger(
    val idMensajero: Int,
    val nombreCompleto: String,
    val idTareaActual: Int,
    val nombreTareaActual: String,
    val latitud: Double,
    val longitud: Double,
    val fechaUltimaActualizacion: String
)

interface LocationApi {
    @POST("api/tracking/posicion")
    suspend fun updateLocation(@Body request: LocationRequest): Response<Void>

    @retrofit2.http.GET("api/tracking/mensajeros-actual")
    suspend fun getActiveMessengers(): Response<List<Messenger>>
}
