package com.example.soprintsgr.ui.login

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.soprintsgr.MainActivity
import com.example.soprintsgr.R
import com.example.soprintsgr.data.AuthService

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val usernameEditText = findViewById<EditText>(R.id.usernameEditText)
        val passwordEditText = findViewById<EditText>(R.id.passwordEditText)
        val loginButton = findViewById<Button>(R.id.loginButton)

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
            val username = usernameEditText.text.toString()
            val password = passwordEditText.text.toString()

            if (AuthService.login(username, password)) {
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                finish()
            } else {
                Toast.makeText(this, "Credenciales inválidas", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
