package com.example.soprintsgr.ui.login

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import android.widget.LinearLayout
import android.net.Uri
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
        
        // Check if user is already logged in
        val sessionManager = com.example.soprintsgr.data.SessionManager(this)
        if (sessionManager.isLoggedIn()) {
            // Skip login and go directly to MainActivity
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
            return
        }
        
        setContentView(R.layout.activity_login)

        authService = AuthService(this)

        val usernameEditText = findViewById<EditText>(R.id.usernameEditText)
        val passwordEditText = findViewById<EditText>(R.id.passwordEditText)
        loginButton = findViewById<Button>(R.id.loginButton)
        progressBar = findViewById<ProgressBar>(R.id.progressBar)
        
        // Elements to hide when password has focus
        val usernameLabel = findViewById<android.widget.TextView>(R.id.usernameLabel)

        // Focus listener for password field - hide username section with smooth animation
        passwordEditText.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                // Animate hide with smooth fade out
                usernameLabel?.animate()
                    ?.alpha(0f)
                    ?.setDuration(500)
                    ?.setInterpolator(android.view.animation.DecelerateInterpolator())
                    ?.withEndAction {
                        usernameLabel?.visibility = View.GONE
                    }?.start()
                usernameEditText.animate()
                    .alpha(0f)
                    .setDuration(500)
                    .setInterpolator(android.view.animation.DecelerateInterpolator())
                    .withEndAction {
                        usernameEditText.visibility = View.GONE
                    }.start()
            }
        }

        // Detect keyboard visibility to restore username field
        val rootView = findViewById<View>(android.R.id.content)
        rootView.viewTreeObserver.addOnGlobalLayoutListener {
            val rect = android.graphics.Rect()
            rootView.getWindowVisibleDisplayFrame(rect)
            val screenHeight = rootView.rootView.height
            val keypadHeight = screenHeight - rect.bottom
            
            // If keyboard is hidden (less than 200 pixels visible for keyboard)
            if (keypadHeight < 200) {
                // Clear focus from password field
                if (!usernameEditText.hasFocus() && usernameEditText.visibility == View.GONE) {
                    // Animate show with smooth fade in
                    usernameLabel?.visibility = View.VISIBLE
                    usernameLabel?.animate()
                        ?.alpha(1f)
                        ?.setDuration(500)
                        ?.setInterpolator(android.view.animation.DecelerateInterpolator())
                        ?.start()
                    usernameEditText.visibility = View.VISIBLE
                    usernameEditText.animate()
                        .alpha(1f)
                        .setDuration(500)
                        .setInterpolator(android.view.animation.DecelerateInterpolator())
                        .start()
                    passwordEditText.clearFocus()
                }
            }
        }

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

        val contactLayout = findViewById<LinearLayout>(R.id.contactLayout)
        contactLayout.setOnClickListener {
            val contactNumber = "+593963879454"
            val url = "https://api.whatsapp.com/send?phone=$contactNumber"
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse(url)
            startActivity(intent)
        }
    }

    private fun performLogin(username: String, password: String) {
        lifecycleScope.launch {
            try {
                showLoading(true)
                
                val result = authService.login(username, password)
                
                result.onSuccess { loginResponse ->
                    // Guardar token FCM si existe
                    com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                        if (!task.isSuccessful) {
                            return@addOnCompleteListener
                        }
                        val token = task.result
                        
                        lifecycleScope.launch {
                            try {
                                if (loginResponse.idUsuario != null) {
                                    authService.updateFcmToken(loginResponse.idUsuario.toLong(), token)
                                }
                            } catch (e: Exception) {
                                runOnUiThread {
                                    Toast.makeText(
                                        this@LoginActivity, 
                                        "Error actualizando token notificaciones: ${e.message}", 
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    }

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
