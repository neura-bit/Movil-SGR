package com.example.soprintsgr.ui.tasks

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.soprintsgr.R
import com.example.soprintsgr.data.ActiveTaskManager
import com.example.soprintsgr.data.TaskRepository
import com.example.soprintsgr.data.api.Task
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class TaskInProgressActivity : AppCompatActivity() {
    private lateinit var tvTimer: TextView
    private lateinit var tvTaskName: TextView
    private lateinit var tvTipoOperacion: TextView
    private lateinit var tvCategoria: TextView
    private lateinit var tvClienteName: TextView
    private lateinit var tvClienteAddress: TextView
    private lateinit var tvClientePhone: TextView
    private lateinit var btnCallClient: MaterialButton
    private lateinit var btnOpenMap: MaterialButton
    private lateinit var btnFinishTask: MaterialButton
    private lateinit var fabBack: FloatingActionButton
    
    private lateinit var activeTaskManager: ActiveTaskManager
    private lateinit var taskRepository: TaskRepository
    private var currentTask: Task? = null
    
    private val handler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            updateTimer()
            handler.postDelayed(this, 1000) // Update every second
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_task_in_progress)
        
        activeTaskManager = ActiveTaskManager.getInstance(this)
        taskRepository = TaskRepository(this)
        
        initViews()
        loadTaskData()
        startTimer()
        setupListeners()
    }

    private fun initViews() {
        tvTimer = findViewById(R.id.tvTimer)
        tvTaskName = findViewById(R.id.tvTaskName)
        tvTipoOperacion = findViewById(R.id.tvTipoOperacion)
        tvCategoria = findViewById(R.id.tvCategoria)
        tvClienteName = findViewById(R.id.tvClienteName)
        tvClienteAddress = findViewById(R.id.tvClienteAddress)
        tvClientePhone = findViewById(R.id.tvClientePhone)
        btnCallClient = findViewById(R.id.btnCallClient)
        btnOpenMap = findViewById(R.id.btnOpenMap)
        btnFinishTask = findViewById(R.id.btnFinishTask)
        fabBack = findViewById(R.id.fabBack)
    }

    private fun loadTaskData() {
        currentTask = activeTaskManager.getActiveTask()
        
        if (currentTask == null) {
            Toast.makeText(this, "No hay tarea activa", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        currentTask?.let { task ->
            tvTaskName.text = task.nombre
            tvTipoOperacion.text = task.tipoOperacion.nombre
            tvCategoria.text = task.categoria.nombre
            tvClienteName.text = task.cliente.nombre
            tvClienteAddress.text = task.cliente.direccion
            tvClientePhone.text = task.cliente.telefono
        }
    }

    private fun startTimer() {
        handler.post(timerRunnable)
    }

    private fun updateTimer() {
        val elapsedMillis = activeTaskManager.getElapsedTime()
        val hours = TimeUnit.MILLISECONDS.toHours(elapsedMillis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsedMillis) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(elapsedMillis) % 60
        
        tvTimer.text = String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun setupListeners() {
        fabBack.setOnClickListener {
            finish()
        }
        
        btnCallClient.setOnClickListener {
            currentTask?.let { task ->
                val intent = Intent(Intent.ACTION_DIAL)
                intent.data = Uri.parse("tel:${task.cliente.telefono}")
                startActivity(intent)
            }
        }
        
        btnOpenMap.setOnClickListener {
            currentTask?.let { task ->
                val uri = "geo:${task.cliente.latitud},${task.cliente.longitud}?q=${task.cliente.latitud},${task.cliente.longitud}"
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                intent.setPackage("com.google.android.apps.maps")
                
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    // If Google Maps is not installed, open in browser
                    val browserIntent = Intent(Intent.ACTION_VIEW, 
                        Uri.parse("https://www.google.com/maps/search/?api=1&query=${task.cliente.latitud},${task.cliente.longitud}"))
                    startActivity(browserIntent)
                }
            }
        }
        
        btnFinishTask.setOnClickListener {
            currentTask?.let { task ->
                showFinalizarDialog(task)
            }
        }
    }

    private fun showFinalizarDialog(task: Task) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_finalizar_tarea, null)
        
        // Referencias a las vistas
        val llCodigoSection = dialogView.findViewById<LinearLayout>(R.id.llCodigoSection)
        val etCode1 = dialogView.findViewById<EditText>(R.id.etCode1)
        val etCode2 = dialogView.findViewById<EditText>(R.id.etCode2)
        val etCode3 = dialogView.findViewById<EditText>(R.id.etCode3)
        val etCode4 = dialogView.findViewById<EditText>(R.id.etCode4)
        val etObservacion = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etObservacion)
        val tvMensajeAyuda = dialogView.findViewById<TextView>(R.id.tvMensajeAyuda)
        val cvError = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cvError)
        val tvError = dialogView.findViewById<TextView>(R.id.tvError)
        val btnCancelar = dialogView.findViewById<MaterialButton>(R.id.btnCancelar)
        val btnFinalizar = dialogView.findViewById<MaterialButton>(R.id.btnFinalizar)

        // Configure dialog based on task type
        val isEntrega = task.tipoOperacion.nombre.equals("Entrega", ignoreCase = true)
        if (isEntrega) {
            llCodigoSection.visibility = View.VISIBLE
            tvMensajeAyuda.text = "Solicita el código de verificación de 4 dígitos al cliente e ingresa una observación."
            
            // Setup auto-focus entre cajas
            setupCodeInputs(etCode1, etCode2, etCode3, etCode4)
            
            // Focus en la primera caja
            etCode1.requestFocus()
            etCode1.postDelayed({
                val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.showSoftInput(etCode1, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }, 200)
        } else {
            llCodigoSection.visibility = View.GONE
            tvMensajeAyuda.text = "Por favor, ingresa una observación para completar esta tarea."
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnCancelar.setOnClickListener {
            dialog.dismiss()
        }

        btnFinalizar.setOnClickListener {
            cvError.visibility = View.GONE
            
            val observacion = etObservacion.text?.toString()?.trim() ?: ""
            
            // Validate observation
            if (observacion.isEmpty()) {
                tvError.text = "La observación es requerida"
                cvError.visibility = View.VISIBLE
                return@setOnClickListener
            }

            // Validate code if it's a delivery task
            if (isEntrega) {
                val code1 = etCode1.text?.toString()?.trim() ?: ""
                val code2 = etCode2.text?.toString()?.trim() ?: ""
                val code3 = etCode3.text?.toString()?.trim() ?: ""
                val code4 = etCode4.text?.toString()?.trim() ?: ""
                
                if (code1.isEmpty() || code2.isEmpty() || code3.isEmpty() || code4.isEmpty()) {
                    tvError.text = "Debes ingresar los 4 dígitos del código de verificación"
                    cvError.visibility = View.VISIBLE
                    
                    // Focus en la primera caja vacía
                    when {
                        code1.isEmpty() -> etCode1.requestFocus()
                        code2.isEmpty() -> etCode2.requestFocus()
                        code3.isEmpty() -> etCode3.requestFocus()
                        code4.isEmpty() -> etCode4.requestFocus()
                    }
                    return@setOnClickListener
                }
                
                val codigo = "$code1$code2$code3$code4"
                finalizarTarea(task, codigo, observacion, btnFinalizar, tvError, cvError, dialog)
            } else {
                finalizarTarea(task, null, observacion, btnFinalizar, tvError, cvError, dialog)
            }
        }

        dialog.show()
    }
    
    private fun setupCodeInputs(et1: EditText, et2: EditText, et3: EditText, et4: EditText) {
        val editTexts = listOf(et1, et2, et3, et4)
        
        editTexts.forEachIndexed { index, editText ->
            editText.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                
                override fun afterTextChanged(s: android.text.Editable?) {
                    if (s?.length == 1 && index < editTexts.size - 1) {
                        // Move to next field
                        editTexts[index + 1].requestFocus()
                    }
                }
            })
            
            // Handle backspace to move to previous field
            editText.setOnKeyListener { _, keyCode, event ->
                if (keyCode == android.view.KeyEvent.KEYCODE_DEL && 
                    event.action == android.view.KeyEvent.ACTION_DOWN &&
                    editText.text.isEmpty() && index > 0) {
                    editTexts[index - 1].requestFocus()
                    editTexts[index - 1].setSelection(editTexts[index - 1].text.length)
                    true
                } else {
                    false
                }
            }
        }
    }

    private fun finalizarTarea(
        task: Task,
        codigo: String?,
        observacion: String,
        btnFinalizar: MaterialButton,
        tvError: TextView,
        cvError: com.google.android.material.card.MaterialCardView,
        dialog: AlertDialog
    ) {
        lifecycleScope.launch {
            try {
                // Show loading state
                btnFinalizar.isEnabled = false
                btnFinalizar.text = "Finalizando..."
                btnFinalizar.icon = null

                val result = if (codigo != null) {
                    // Delivery task - with code verification
                    taskRepository.finalizarTareaConCodigo(task.idTarea, codigo, observacion)
                } else {
                    // Pickup task - without code
                    taskRepository.finalizarTareaSinCodigo(task.idTarea, observacion)
                }

                if (result.isSuccess) {
                    // Success - clear active task and finish
                    activeTaskManager.clearActiveTask()
                    
                    // Send broadcast to MainActivity to refresh map
                    val refreshIntent = Intent("com.example.soprintsgr.TASK_COMPLETED")
                    sendBroadcast(refreshIntent)
                    
                    Toast.makeText(
                        this@TaskInProgressActivity,
                        "Tarea finalizada exitosamente",
                        Toast.LENGTH_SHORT
                    ).show()
                    
                    dialog.dismiss()
                    finish()
                } else {
                    // Error
                    val errorMessage = result.exceptionOrNull()?.message ?: "Error desconocido"
                    tvError.text = errorMessage
                    cvError.visibility = View.VISIBLE
                    
                    // Reset button state
                    btnFinalizar.isEnabled = true
                    btnFinalizar.text = "Finalizar"
                    btnFinalizar.setIconResource(R.drawable.ic_check_circle)
                }
            } catch (e: Exception) {
                tvError.text = "Error: ${e.message}"
                cvError.visibility = View.VISIBLE
                
                // Reset button state
                btnFinalizar.isEnabled = true
                btnFinalizar.text = "Finalizar"
                btnFinalizar.setIconResource(R.drawable.ic_check_circle)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(timerRunnable)
    }
}
