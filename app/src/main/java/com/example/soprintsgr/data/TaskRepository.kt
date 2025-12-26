package com.example.soprintsgr.data

import android.content.Context
import android.util.Log
import com.example.soprintsgr.data.api.RetrofitClient
import com.example.soprintsgr.data.api.Task

class TaskRepository(context: Context) {
    private val sessionManager = SessionManager(context)
    
    companion object {
        private const val TAG = "TaskRepository"
    }

    suspend fun getMyTasks(): List<Task> {
        return try {
            val response = RetrofitClient.api.getMyTasks()
            if (response.isSuccessful) {
                val tasks = response.body() ?: emptyList()
                Log.d(TAG, "Fetched ${tasks.size} tasks successfully")
                tasks
            } else {
                Log.e(TAG, "Failed to fetch tasks: ${response.code()}")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching tasks", e)
            emptyList()
        }
    }

    suspend fun iniciarTarea(idTarea: Int): Result<Task> {
        return try {
            val response = RetrofitClient.api.iniciarTarea(idTarea)
            if (response.isSuccessful && response.body() != null) {
                Log.d(TAG, "Tarea $idTarea iniciada exitosamente")
                Result.success(response.body()!!)
            } else {
                val errorMsg = "Error al iniciar tarea: ${response.code()}"
                Log.e(TAG, errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error iniciando tarea", e)
            Result.failure(e)
        }
    }

    fun hasActiveTask(tasks: List<Task>): Task? {
        return tasks.find { it.estadoTarea.idEstadoTarea == 2 } // 2 = EN PROCESO
    }

    suspend fun finalizarTareaSinCodigo(idTarea: Int, observacion: String): Result<Task> {
        return try {
            val request = com.example.soprintsgr.data.api.FinalizarSinCodigoRequest(
                idEstadoTarea = 3,
                observacion = observacion
            )
            val response = RetrofitClient.api.finalizarTareaSinCodigo(idTarea, request)
            if (response.isSuccessful && response.body() != null) {
                Log.d(TAG, "Tarea $idTarea finalizada sin código exitosamente")
                Result.success(response.body()!!)
            } else {
                val errorMsg = "Error al finalizar tarea: ${response.code()}"
                Log.e(TAG, errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finalizando tarea sin código", e)
            Result.failure(e)
        }
    }

    suspend fun finalizarTareaConCodigo(idTarea: Int, codigo: String, observacion: String): Result<Task> {
        return try {
            val request = com.example.soprintsgr.data.api.FinalizarConCodigoRequest(
                codigo = codigo,
                idEstadoTarea = 3,
                observacion = observacion
            )
            val response = RetrofitClient.api.finalizarTareaConCodigo(idTarea, request)
            if (response.isSuccessful && response.body() != null) {
                Log.d(TAG, "Tarea $idTarea finalizada con código exitosamente")
                Result.success(response.body()!!)
            } else {
                val errorMsg = when (response.code()) {
                    400 -> "Código incorrecto"
                    else -> "Error al finalizar tarea: ${response.code()}"
                }
                Log.e(TAG, errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finalizando tarea con código", e)
            Result.failure(e)
        }
    }

    suspend fun getCompletedTasks(fechaInicio: String, fechaFin: String): List<Task> {
        return try {
            val response = RetrofitClient.api.getCompletedTasks(fechaInicio, fechaFin)
            if (response.isSuccessful) {
                val tasks = response.body() ?: emptyList()
                Log.d(TAG, "Fetched ${tasks.size} completed tasks successfully")
                tasks
            } else {
                Log.e(TAG, "Failed to fetch completed tasks: ${response.code()}")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching completed tasks", e)
            emptyList()
        }
    }
}
