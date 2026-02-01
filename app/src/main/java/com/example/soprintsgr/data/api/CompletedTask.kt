package com.example.soprintsgr.data.api




/**
 * Model for completed tasks API response which returns flattened data
 */
data class CompletedTask(
    val idTarea: Int,
    val nombre: String,
    val estadoTarea: String,
    val tipoOperacion: String,
    val categoria: String,
    
    // Cliente fields (flattened)
    val clienteNombre: String,
    val clienteDireccion: String,
    val clienteTelefono: String,
    val clienteCiudad: String,
    val clienteLatitud: Double,
    val clienteLongitud: Double,
    
    // User fields (flattened)
    val asesorCrea: String?,
    val mensajeroAsignado: String?,
    val supervisorAsigna: String?,
    
    // Date fields
    val fechaCreacion: String,
    val fechaLimite: String,
    val fechaFin: String?,
    val fechaInicio: String?,
    
    // Other fields
    val comentario: String?,
    val observacion: String?,
    val codigo: String?,
    val proceso: String?,
    val tiempoTotal: Int?,
    val tiempoEjecucion: Int?,
    val tiempoRespuesta: Int?,
    val entregaATiempo: Boolean?,
    
    // Archivos adjuntos
    val archivosAdjuntos: List<ArchivoAdjunto>? = null
) {
    /**
     * Convert to regular Task model for use with existing adapter
     */
    fun toTask(): Task {
        return Task(
            idTarea = idTarea,
            nombre = nombre,
            tipoOperacion = TipoOperacion(0, tipoOperacion),
            categoria = Categoria(0, categoria),
            cliente = Cliente(
                idCliente = 0,
                nombre = clienteNombre,
                telefono = clienteTelefono,
                rucCi = "",
                direccion = clienteDireccion,
                ciudad = clienteCiudad,
                latitud = clienteLatitud,
                longitud = clienteLongitud
            ),
            estadoTarea = EstadoTarea(0, estadoTarea),
            asesorCrea = Usuario(0, asesorCrea ?: "", "", "", "", true, "", 
                Sucursal(0, "", "", "", ""), 
                RolUsuario(0, "")),
            mensajeroAsignado = Usuario(0, mensajeroAsignado ?: "", "", "", "", true, "", 
                Sucursal(0, "", "", "", ""), 
                RolUsuario(0, "")),
            supervisorAsigna = Usuario(0, supervisorAsigna ?: "", "", "", "", true, "", 
                Sucursal(0, "", "", "", ""), 
                RolUsuario(0, "")),
            fechaCreacion = fechaCreacion,
            fechaLimite = fechaLimite,
            fechaFin = fechaFin,
            tiempoTotal = tiempoTotal?.toString(),
            comentario = comentario,
            observacion = observacion,
            proceso = proceso ?: "",
            fechaInicio = fechaInicio ?: "",
            codigo = codigo ?: "",
            archivosAdjuntos = archivosAdjuntos,
            tiempoEjecucion = tiempoEjecucion?.toString() // Convert Int? to String?
        )
    }
}
