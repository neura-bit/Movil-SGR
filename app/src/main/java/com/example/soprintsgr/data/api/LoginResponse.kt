package com.example.soprintsgr.data.api

data class LoginResponse(
    val token: String,
    val username: String,
    val nombre: String,
    val apellido: String,
    val rol: String,
    val correo: String?,
    val idUsuario: Int,
    val expiresIn: Long,
    val fotoPerfil: String?
)
