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
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class TasksActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var taskAdapter: TaskAdapter
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmptyState: TextView
    private lateinit var taskRepository: TaskRepository
    private lateinit var activeTaskManager: com.example.soprintsgr.data.ActiveTaskManager
    private lateinit var sessionManager: com.example.soprintsgr.data.SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tasks)

        // Setup toolbar
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        taskRepository = TaskRepository(this)
        activeTaskManager = com.example.soprintsgr.data.ActiveTaskManager.getInstance(this)
        sessionManager = com.example.soprintsgr.data.SessionManager(this)
        
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
                
                // Check if there's an active task (EN PROCESO)
                val activeTask = taskRepository.hasActiveTask(tasks)
                if (activeTask != null) {
                    // Update task data without changing the timestamp to preserve timer
                    activeTaskManager.updateTaskData(activeTask)
                    val intent = Intent(this@TasksActivity, TaskInProgressActivity::class.java)
                    startActivity(intent)
                    finish()
                    return@launch
                }
                
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
        val btnIniciarTarea = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnIniciarTarea)

        val btnFinalizar = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnFinalizar)
        val layoutArchivosAdjuntos = dialogView.findViewById<View>(R.id.layoutArchivosAdjuntos)
        val rvArchivosAdjuntos = dialogView.findViewById<RecyclerView>(R.id.rvArchivosAdjuntos)
        
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

        // Show archivos adjuntos if exists
        if (!task.archivosAdjuntos.isNullOrEmpty()) {
            layoutArchivosAdjuntos.visibility = View.VISIBLE
            rvArchivosAdjuntos.layoutManager = LinearLayoutManager(this)
            rvArchivosAdjuntos.adapter = ArchivoAdjuntoAdapter(task.archivosAdjuntos) { archivo ->
                previewFile(archivo)
            }
        } else {
            layoutArchivosAdjuntos.visibility = View.GONE
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
        
        // Show appropriate button based on task status
        val isEnProceso = task.estadoTarea.idEstadoTarea == 2
        val isCreada = task.estadoTarea.idEstadoTarea == 1
        val canFinalize = task.estadoTarea.nombre !in listOf("COMPLETADA", "CANCELADA")
        
        when {
            isEnProceso -> {
                // Task is in progress, show finalizar button
                btnIniciarTarea.visibility = View.GONE
                btnFinalizar.visibility = View.VISIBLE
                btnFinalizar.setOnClickListener {
                    dialog.dismiss()
                    showFinalizarDialog(task)
                }
            }
            isCreada -> {
                // Task is created, show iniciar button
                btnIniciarTarea.visibility = View.VISIBLE
                btnFinalizar.visibility = View.GONE
                btnIniciarTarea.setOnClickListener {
                    dialog.dismiss()
                    iniciarTarea(task)
                }
            }
            else -> {
                // Task is completed or cancelled
                btnIniciarTarea.visibility = View.GONE
                btnFinalizar.visibility = View.GONE
            }
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

    private fun iniciarTarea(task: Task) {
        lifecycleScope.launch {
            try {
                // Check if there's already an active task
                if (activeTaskManager.hasActiveTask()) {
                    android.widget.Toast.makeText(
                        this@TasksActivity,
                        "Ya tienes una tarea en proceso. Finalízala antes de iniciar otra.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }

                // Show loading
                val progressDialog = android.app.ProgressDialog.show(
                    this@TasksActivity,
                    "Iniciando tarea",
                    "Por favor espera...",
                    true
                )

                val result = taskRepository.iniciarTarea(task.idTarea)

                progressDialog.dismiss()

                if (result.isSuccess) {
                    val updatedTask = result.getOrNull()!!
                    activeTaskManager.startTask(updatedTask)

                    android.widget.Toast.makeText(
                        this@TasksActivity,
                        "Tarea iniciada exitosamente",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()

                    // Navigate to TaskInProgressActivity
                    val intent = Intent(this@TasksActivity, TaskInProgressActivity::class.java)
                    startActivity(intent)
                    finish()
                } else {
                    android.widget.Toast.makeText(
                        this@TasksActivity,
                        "Error al iniciar tarea: ${result.exceptionOrNull()?.message}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                android.widget.Toast.makeText(
                    this@TasksActivity,
                    "Error: ${e.message}",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showFinalizarDialog(task: Task) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_finalizar_tarea, null)
        
        // Referencias a las vistas
        val llCodigoSection = dialogView.findViewById<android.widget.LinearLayout>(R.id.llCodigoSection)
        val etCode1 = dialogView.findViewById<android.widget.EditText>(R.id.etCode1)
        val etCode2 = dialogView.findViewById<android.widget.EditText>(R.id.etCode2)
        val etCode3 = dialogView.findViewById<android.widget.EditText>(R.id.etCode3)
        val etCode4 = dialogView.findViewById<android.widget.EditText>(R.id.etCode4)
        val etObservacion = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etObservacion)
        val tvMensajeAyuda = dialogView.findViewById<TextView>(R.id.tvMensajeAyuda)
        val cvError = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cvError)
        val tvError = dialogView.findViewById<TextView>(R.id.tvError)
        val btnCancelar = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancelar)
        val btnFinalizar = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnFinalizar)

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
    
    private fun setupCodeInputs(et1: android.widget.EditText, et2: android.widget.EditText, et3: android.widget.EditText, et4: android.widget.EditText) {
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
        btnFinalizar: com.google.android.material.button.MaterialButton,
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

    private fun openInMaps(lat: Double, lng: Double) {
        val uri = "geo:$lat,$lng?q=$lat,$lng"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
        intent.setPackage("com.google.android.apps.maps")
        startActivity(intent)
    }

    private fun previewFile(archivo: com.example.soprintsgr.data.api.ArchivoAdjunto) {
        val extension = archivo.nombreOriginal.substringAfterLast('.', "").lowercase()
        
        when (extension) {
            "jpg", "jpeg", "png", "gif" -> previewImage(archivo)
            "pdf" -> previewPdf(archivo)
            else -> downloadFile(archivo) // Fallback to download for other types
        }
    }

    private fun previewImage(archivo: com.example.soprintsgr.data.api.ArchivoAdjunto) {
        val token = sessionManager.getToken()
        if (token == null) {
            android.widget.Toast.makeText(this, "Error: Token nulo", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        val url = "https://seguimiento.srv1070869.hstgr.cloud/api/archivos/${archivo.idArchivo}"
        android.util.Log.d("PreviewImage", "Loading URL: $url")

        val dialogView = layoutInflater.inflate(R.layout.dialog_image_preview, null)
        val ivPreview = dialogView.findViewById<android.widget.ImageView>(R.id.ivPreview)
        val progressBar = dialogView.findViewById<android.widget.ProgressBar>(R.id.progressBarPreview)
        val tvError = dialogView.findViewById<android.widget.TextView>(R.id.tvErrorPreview)

        val glideUrl = GlideUrl(
            url,
            LazyHeaders.Builder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        )

        Glide.with(this)
            .load(glideUrl)
            .listener(object : RequestListener<android.graphics.drawable.Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<android.graphics.drawable.Drawable>,
                    isFirstResource: Boolean
                ): Boolean {
                    android.util.Log.e("PreviewImage", "Load failed", e)
                    progressBar.visibility = View.GONE
                    tvError.visibility = View.VISIBLE
                    tvError.text = "Error: ${e?.message}"
                    return false
                }

                override fun onResourceReady(
                    resource: android.graphics.drawable.Drawable,
                    model: Any,
                    target: Target<android.graphics.drawable.Drawable>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    progressBar.visibility = View.GONE
                    return false
                }
            })
            .into(ivPreview)

        AlertDialog.Builder(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            .setView(dialogView)
            .setPositiveButton("Cerrar", null)
            .show()
    }

    private fun previewPdf(archivo: com.example.soprintsgr.data.api.ArchivoAdjunto) {
        val token = sessionManager.getToken() ?: return
        
        showLoading(true)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val urlStr = "https://seguimiento.srv1070869.hstgr.cloud/api/archivos/${archivo.idArchivo}"
                val url = URL(urlStr)
                val connection = url.openConnection() as HttpURLConnection
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.connect()

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val inputStream = connection.inputStream
                    val file = File(cacheDir, archivo.nombreOriginal)
                    val outputStream = FileOutputStream(file)
                    
                    inputStream.copyTo(outputStream)
                    outputStream.close()
                    inputStream.close()
                    
                    withContext(Dispatchers.Main) {
                        showLoading(false)
                        openPdf(file)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        showLoading(false)
                        android.widget.Toast.makeText(this@TasksActivity, "Error al descargar PDF", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    android.widget.Toast.makeText(this@TasksActivity, "Error: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun openPdf(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.provider",
                file
            )
            
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(uri, "application/pdf")
            intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            startActivity(intent)
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "No hay aplicación para abrir PDF", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun downloadFile(archivo: com.example.soprintsgr.data.api.ArchivoAdjunto) {
        val token = sessionManager.getToken()
        if (token.isNullOrEmpty()) {
            android.widget.Toast.makeText(this, "Error: No hay sesión activa", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val url = "https://seguimiento.srv1070869.hstgr.cloud/api/archivos/${archivo.idArchivo}"
            
            val request = android.app.DownloadManager.Request(Uri.parse(url))
                .setTitle(archivo.nombreOriginal)
                .setDescription("Descargando archivo adjunto...")
                .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, "SoprintSGR/${archivo.nombreOriginal}")
                .addRequestHeader("Authorization", "Bearer $token")
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            val downloadManager = getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
            downloadManager.enqueue(request)
            
            android.widget.Toast.makeText(this, "Descargando ${archivo.nombreOriginal}...", android.widget.Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "Error al iniciar descarga: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
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
