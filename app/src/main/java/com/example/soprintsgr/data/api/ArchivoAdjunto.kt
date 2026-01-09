package com.example.soprintsgr.data.api

data class ArchivoAdjunto(
    val idArchivo: Int,
    val nombreOriginal: String,
    val nombreAlmacenado: String,
    val tipoMime: String,
    val tamanioBytes: Long,
    val fechaSubida: String
)
