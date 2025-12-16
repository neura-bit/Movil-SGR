package com.example.soprintsgr.data

object AuthService {
    fun login(username: String, password: String): Boolean {
        return username == "admin" && password == "1234"
    }
}
