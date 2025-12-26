package com.example.soprintsgr.data.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

data class LocationRequest(
    val idMensajero: Int,
    val idTarea: Int?,  // Nullable para cuando no hay tarea activa
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

data class FinalizarSinCodigoRequest(
    val idEstadoTarea: Int,
    val observacion: String
)

data class FinalizarConCodigoRequest(
    val codigo: String,
    val idEstadoTarea: Int,
    val observacion: String
)

data class IniciarTareaRequest(
    val idEstadoTarea: Int = 2 // EN PROCESO
)

interface LocationApi {
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("api/tracking/posicion")
    suspend fun updateLocation(@Body request: LocationRequest): Response<Void>

    @retrofit2.http.GET("api/tracking/mensajeros-actual")
    suspend fun getActiveMessengers(): Response<List<Messenger>>

    @retrofit2.http.GET("api/tareas/mis-tareas")
    suspend fun getMyTasks(): Response<List<Task>>

    @retrofit2.http.PUT("api/tareas/{idTarea}/iniciar")
    suspend fun iniciarTarea(
        @retrofit2.http.Path("idTarea") idTarea: Int
    ): Response<Task>

    @retrofit2.http.PUT("api/tareas/{idTarea}/finalizar-sin-codigo")
    suspend fun finalizarTareaSinCodigo(
        @retrofit2.http.Path("idTarea") idTarea: Int,
        @Body request: FinalizarSinCodigoRequest
    ): Response<Task>

    @retrofit2.http.PUT("api/tareas/{idTarea}/finalizar")
    suspend fun finalizarTareaConCodigo(
        @retrofit2.http.Path("idTarea") idTarea: Int,
        @Body request: FinalizarConCodigoRequest
    ): Response<Task>

    @retrofit2.http.GET("api/tareas/mis-tareas-completadas")
    suspend fun getCompletedTasks(
        @retrofit2.http.Query("fechaInicio") fechaInicio: String,
        @retrofit2.http.Query("fechaFin") fechaFin: String
    ): Response<List<Task>>
}

