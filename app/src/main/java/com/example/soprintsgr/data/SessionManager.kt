package com.example.soprintsgr.data

import android.content.Context
import android.content.SharedPreferences
import com.example.soprintsgr.data.api.LoginResponse

class SessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "SoprintSGRSession"
        private const val KEY_TOKEN = "token"
        private const val KEY_USERNAME = "username"
        private const val KEY_NOMBRE = "nombre"
        private const val KEY_APELLIDO = "apellido"
        private const val KEY_ROL = "rol"
        private const val KEY_ID_USUARIO = "idUsuario"
        private const val KEY_EXPIRES_IN = "expiresIn"
        private const val KEY_LOGIN_TIMESTAMP = "loginTimestamp"
        private const val KEY_CORREO = "correo"
        private const val KEY_FOTO_PERFIL = "fotoPerfil"
    }

    fun saveSession(loginResponse: LoginResponse) {
        prefs.edit().apply {
            putString(KEY_TOKEN, loginResponse.token)
            putString(KEY_USERNAME, loginResponse.username)
            putString(KEY_NOMBRE, loginResponse.nombre)
            putString(KEY_APELLIDO, loginResponse.apellido)
            putString(KEY_ROL, loginResponse.rol)
            putInt(KEY_ID_USUARIO, loginResponse.idUsuario)
            putLong(KEY_EXPIRES_IN, loginResponse.expiresIn)
            putLong(KEY_LOGIN_TIMESTAMP, System.currentTimeMillis())
            putString(KEY_CORREO, loginResponse.correo)
            putString(KEY_FOTO_PERFIL, loginResponse.fotoPerfil)
            apply()
        }
    }

    fun getToken(): String? {
        return prefs.getString(KEY_TOKEN, null)
    }

    fun getUsername(): String? {
        return prefs.getString(KEY_USERNAME, null)
    }

    fun getNombre(): String? {
        return prefs.getString(KEY_NOMBRE, null)
    }

    fun getApellido(): String? {
        return prefs.getString(KEY_APELLIDO, null)
    }

    fun getNombreCompleto(): String {
        val nombre = getNombre() ?: ""
        val apellido = getApellido() ?: ""
        return "$nombre $apellido".trim()
    }

    fun getRol(): String? {
        return prefs.getString(KEY_ROL, null)
    }

    fun getIdUsuario(): Int {
        return prefs.getInt(KEY_ID_USUARIO, -1)
    }

    fun getCorreo(): String? {
        return prefs.getString(KEY_CORREO, null)
    }

    fun getFotoPerfil(): String? {
        return prefs.getString(KEY_FOTO_PERFIL, null)
    }

    fun isLoggedIn(): Boolean {
        val token = getToken()
        return !token.isNullOrEmpty() && !isTokenExpired()
    }

    fun isTokenExpired(): Boolean {
        val loginTimestamp = prefs.getLong(KEY_LOGIN_TIMESTAMP, 0)
        val expiresIn = prefs.getLong(KEY_EXPIRES_IN, 0)
        
        if (loginTimestamp == 0L || expiresIn == 0L) {
            return true
        }
        
        val currentTime = System.currentTimeMillis()
        val expirationTime = loginTimestamp + expiresIn
        
        return currentTime >= expirationTime
    }

    fun clearSession() {
        prefs.edit().clear().apply()
    }
}
