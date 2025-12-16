package com.example.soprintsgr.ui.login

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.soprintsgr.MainActivity
import com.example.soprintsgr.R
import com.example.soprintsgr.data.AuthService
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {
    private lateinit var authService: AuthService
    private lateinit var progressBar: ProgressBar
    private lateinit var loginButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        authService = AuthService(this)

        val usernameEditText = findViewById<EditText>(R.id.usernameEditText)
        val passwordEditText = findViewById<EditText>(R.id.passwordEditText)
        loginButton = findViewById<Button>(R.id.loginButton)
        progressBar = findViewById<ProgressBar>(R.id.progressBar)

        // Password visibility toggle
        passwordEditText.setOnTouchListener { v, event ->
            val DRAWABLE_RIGHT = 2
            if (event.action == android.view.MotionEvent.ACTION_UP) {
                if (event.rawX >= (passwordEditText.right - passwordEditText.compoundDrawables[DRAWABLE_RIGHT].bounds.width())) {
                    val selection = passwordEditText.selectionEnd
                    if (passwordEditText.transformationMethod == android.text.method.PasswordTransformationMethod.getInstance()) {
                        passwordEditText.transformationMethod = android.text.method.HideReturnsTransformationMethod.getInstance()
                        passwordEditText.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_lock, 0, R.drawable.ic_visibility, 0)
                    } else {
                        passwordEditText.transformationMethod = android.text.method.PasswordTransformationMethod.getInstance()
                        passwordEditText.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_lock, 0, R.drawable.ic_visibility_off, 0)
                    }
                    passwordEditText.setSelection(selection)
                    return@setOnTouchListener true
                }
            }
            false
        }

        loginButton.setOnClickListener {
            val username = usernameEditText.text.toString().trim()
            val password = passwordEditText.text.toString()

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Por favor ingrese usuario y contraseña", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            performLogin(username, password)
        }
    }

    private fun performLogin(username: String, password: String) {
        lifecycleScope.launch {
            try {
                showLoading(true)
                
                val result = authService.login(username, password)
                
                result.onSuccess { loginResponse ->
                    Toast.makeText(
                        this@LoginActivity, 
                        "Bienvenido ${loginResponse.nombre}!", 
                        Toast.LENGTH_SHORT
                    ).show()
                    
                    val intent = Intent(this@LoginActivity, MainActivity::class.java)
                    startActivity(intent)
                    finish()
                }.onFailure { error ->
                    Toast.makeText(
                        this@LoginActivity, 
                        error.message ?: "Error al iniciar sesión", 
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@LoginActivity, 
                    "Error de conexión: ${e.message}", 
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                showLoading(false)
            }
        }
    }

    private fun showLoading(isLoading: Boolean) {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        loginButton.isEnabled = !isLoading
    }
}
