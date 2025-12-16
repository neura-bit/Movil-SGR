package com.example.soprintsgr.data

import android.content.Context
import com.example.soprintsgr.data.api.LoginRequest
import com.example.soprintsgr.data.api.LoginResponse
import com.example.soprintsgr.data.api.RetrofitClient

class AuthService(private val context: Context) {
    private val sessionManager = SessionManager(context)

    suspend fun login(username: String, password: String): Result<LoginResponse> {
        return try {
            val request = LoginRequest(username, password)
            val response = RetrofitClient.api.login(request)
            
            if (response.isSuccessful && response.body() != null) {
                val loginResponse = response.body()!!
                sessionManager.saveSession(loginResponse)
                Result.success(loginResponse)
            } else {
                Result.failure(Exception("Credenciales inválidas"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun logout() {
        sessionManager.clearSession()
    }

    fun isLoggedIn(): Boolean {
        return sessionManager.isLoggedIn()
    }

    fun getSessionManager(): SessionManager {
        return sessionManager
    }
}
