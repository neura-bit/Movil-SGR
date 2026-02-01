package com.example.soprintsgr.data.api

import com.google.gson.annotations.SerializedName

data class Task(
    val idTarea: Int,
    val tipoOperacion: TipoOperacion,
    val categoria: Categoria,
    val cliente: Cliente,
    val estadoTarea: EstadoTarea,
    val asesorCrea: Usuario,
    val mensajeroAsignado: Usuario,
    val supervisorAsigna: Usuario,
    val nombre: String,
    val fechaCreacion: String,
    val fechaLimite: String,
    val fechaFin: String?,
    val tiempoTotal: String?,
    val comentario: String?,
    val observacion: String?,
    val proceso: String,
    val fechaInicio: String,
    val codigo: String,
    val archivosAdjuntos: List<ArchivoAdjunto>? = null,
    val tiempoEjecucion: String? = null
)

data class TipoOperacion(
    val idTipoOperacion: Int,
    val nombre: String
)

data class Categoria(
    val idCategoria: Int,
    val nombre: String
)

data class Cliente(
    val idCliente: Int,
    val nombre: String,
    val telefono: String,
    val rucCi: String,
    val direccion: String,
    val ciudad: String,
    val latitud: Double,
    val longitud: Double
)

data class EstadoTarea(
    val idEstadoTarea: Int,
    val nombre: String
)

data class Usuario(
    val idUsuario: Int,
    val nombre: String,
    val apellido: String,
    val telefono: String,
    val username: String,
    val estado: Boolean,
    val fechaCreacion: String,
    val sucursal: Sucursal,
    val rol: RolUsuario
)

data class Sucursal(
    val idSucursal: Int,
    val nombre: String,
    val direccion: String,
    val ciudad: String,
    val telefono: String
)

data class RolUsuario(
    @SerializedName("id_rol")
    val idRol: Int,
    val nombre: String
)
