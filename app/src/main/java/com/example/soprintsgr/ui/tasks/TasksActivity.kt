package com.example.soprintsgr.ui.tasks

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.soprintsgr.R
import com.example.soprintsgr.data.TaskRepository
import com.example.soprintsgr.data.api.Task
import kotlinx.coroutines.launch

class TasksActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var taskAdapter: TaskAdapter
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmptyState: TextView
    private lateinit var taskRepository: TaskRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tasks)

        // Setup toolbar
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        taskRepository = TaskRepository(this)
        
        recyclerView = findViewById(R.id.recyclerViewTasks)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        progressBar = findViewById(R.id.progressBar)
        tvEmptyState = findViewById(R.id.tvEmptyState)
        
        setupRecyclerView()
        setupSwipeRefresh()
        
        loadTasks()
    }

    private fun setupRecyclerView() {
        taskAdapter = TaskAdapter(emptyList()) { task ->
            showTaskDetails(task)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = taskAdapter
    }

    private fun setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener {
            loadTasks()
        }
    }

    private fun loadTasks() {
        lifecycleScope.launch {
            try {
                showLoading(true)
                val tasks = taskRepository.getMyTasks()
                
                if (tasks.isEmpty()) {
                    showEmptyState(true)
                } else {
                    showEmptyState(false)
                    taskAdapter.updateTasks(tasks)
                }
            } catch (e: Exception) {
                // Handle error
            } finally {
                showLoading(false)
                swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    private fun showTaskDetails(task: Task) {
        // Inflate the custom layout
        val dialogView = layoutInflater.inflate(R.layout.dialog_task_details, null)
        
        // Bind data to views
        val tvTaskTitle = dialogView.findViewById<TextView>(R.id.tvTaskTitle)
        val tvTaskEstado = dialogView.findViewById<TextView>(R.id.tvTaskEstado)
        val tvClienteNombre = dialogView.findViewById<TextView>(R.id.tvClienteNombre)
        val tvClienteDireccion = dialogView.findViewById<TextView>(R.id.tvClienteDireccion)
        val tvClienteTelefono = dialogView.findViewById<TextView>(R.id.tvClienteTelefono)
        val btnCallPhone = dialogView.findViewById<android.widget.ImageButton>(R.id.btnCallPhone)
        val tvTipoOperacion = dialogView.findViewById<TextView>(R.id.tvTipoOperacion)
        val tvCategoria = dialogView.findViewById<TextView>(R.id.tvCategoria)
        val tvFechaLimite = dialogView.findViewById<TextView>(R.id.tvFechaLimite)
        val layoutComentario = dialogView.findViewById<View>(R.id.layoutComentario)
        val tvComentario = dialogView.findViewById<TextView>(R.id.tvComentario)
        val btnOpenMap = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnOpenMap)
        val btnFinalizar = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnFinalizar)
        
        // Set task data
        tvTaskTitle.text = task.nombre
        tvTaskEstado.text = task.estadoTarea.nombre
        
        // Set estado background color
        tvTaskEstado.setBackgroundColor(getEstadoColor(task.estadoTarea.nombre))
        
        // Set cliente data
        tvClienteNombre.text = task.cliente.nombre
        tvClienteDireccion.text = task.cliente.direccion
        tvClienteTelefono.text = task.cliente.telefono
        
        // Set task details
        tvTipoOperacion.text = task.tipoOperacion.nombre
        tvCategoria.text = task.categoria.nombre
        tvFechaLimite.text = task.fechaLimite
        
        // Show comentario if exists
        if (!task.comentario.isNullOrEmpty()) {
            layoutComentario.visibility = View.VISIBLE
            tvComentario.text = task.comentario
        } else {
            layoutComentario.visibility = View.GONE
        }
        
        // Create dialog first
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Cerrar", null)
            .create()
        
        // Set phone call click listener
        btnCallPhone.setOnClickListener {
            val intent = Intent(Intent.ACTION_DIAL)
            intent.data = Uri.parse("tel:${task.cliente.telefono}")
            startActivity(intent)
        }
        
        // Set open map click listener
        btnOpenMap.setOnClickListener {
            openInMaps(task.cliente.latitud, task.cliente.longitud)
        }
        
        // Show/hide finalizar button based on task status
        val canFinalize = task.estadoTarea.nombre !in listOf("COMPLETADA", "CANCELADA")
        if (canFinalize) {
            btnFinalizar.visibility = View.VISIBLE
            btnFinalizar.setOnClickListener {
                dialog.dismiss()
                showFinalizarDialog(task)
            }
        } else {
            btnFinalizar.visibility = View.GONE
        }
        
        // Show dialog
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun getEstadoColor(estado: String): Int {
        return when (estado.uppercase()) {
            "CREADA" -> android.graphics.Color.parseColor("#4CAF50") // Green
            "EN_PROCESO", "EN PROCESO" -> android.graphics.Color.parseColor("#FF9800") // Orange
            "COMPLETADA" -> android.graphics.Color.parseColor("#2196F3") // Blue
            "CANCELADA" -> android.graphics.Color.parseColor("#9E9E9E") // Gray
            else -> android.graphics.Color.parseColor("#607D8B") // Default gray
        }
    }

    private fun showFinalizarDialog(task: Task) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_finalizar_tarea, null)
        
        val tilCodigo = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.tilCodigo)
        val etCodigo = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etCodigo)
        val etObservacion = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etObservacion)
        val tvMensajeAyuda = dialogView.findViewById<TextView>(R.id.tvMensajeAyuda)
        val tvError = dialogView.findViewById<TextView>(R.id.tvError)
        val btnCancelar = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancelar)
        val btnFinalizar = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnFinalizar)

        // Configure dialog based on task type
        val isEntrega = task.tipoOperacion.nombre.equals("Entrega", ignoreCase = true)
        if (isEntrega) {
            tilCodigo.visibility = View.VISIBLE
            tvMensajeAyuda.text = "Solicita el código de verificación al cliente e ingresa la observación."
        } else {
            tilCodigo.visibility = View.GONE
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
            tvError.visibility = View.GONE
            
            val observacion = etObservacion.text?.toString()?.trim() ?: ""
            
            // Validate observation
            if (observacion.isEmpty()) {
                tvError.text = "La observación es requerida"
                tvError.visibility = View.VISIBLE
                return@setOnClickListener
            }

            // Validate code if it's a delivery task
            if (isEntrega) {
                val codigo = etCodigo.text?.toString()?.trim() ?: ""
                if (codigo.isEmpty()) {
                    tvError.text = "El código de verificación es requerido"
                    tvError.visibility = View.VISIBLE
                    return@setOnClickListener
                }
                finalizarTarea(task, codigo, observacion, btnFinalizar, tvError, dialog)
            } else {
                finalizarTarea(task, null, observacion, btnFinalizar, tvError, dialog)
            }
        }

        dialog.show()
    }

    private fun finalizarTarea(
        task: Task,
        codigo: String?,
        observacion: String,
        btnFinalizar: com.google.android.material.button.MaterialButton,
        tvError: TextView,
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
                    // Success
                    android.widget.Toast.makeText(
                        this@TasksActivity,
                        "Tarea finalizada exitosamente",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    dialog.dismiss()
                    loadTasks() // Refresh task list
                } else {
                    // Error
                    val errorMessage = result.exceptionOrNull()?.message ?: "Error desconocido"
                    tvError.text = errorMessage
                    tvError.visibility = View.VISIBLE
                    
                    // Reset button state
                    btnFinalizar.isEnabled = true
                    btnFinalizar.text = "Finalizar"
                    btnFinalizar.setIconResource(R.drawable.ic_check_circle)
                }
            } catch (e: Exception) {
                tvError.text = "Error: ${e.message}"
                tvError.visibility = View.VISIBLE
                
                // Reset button state
                btnFinalizar.isEnabled = true
                btnFinalizar.text = "Finalizar"
                btnFinalizar.setIconResource(R.drawable.ic_check_circle)
            }
        }
    }

    private fun openInMaps(lat: Double, lng: Double) {
        val uri = "geo:$lat,$lng?q=$lat,$lng"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
        intent.setPackage("com.google.android.apps.maps")
        startActivity(intent)
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showEmptyState(show: Boolean) {
        tvEmptyState.visibility = if (show) View.VISIBLE else View.GONE
        recyclerView.visibility = if (show) View.GONE else View.VISIBLE
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
