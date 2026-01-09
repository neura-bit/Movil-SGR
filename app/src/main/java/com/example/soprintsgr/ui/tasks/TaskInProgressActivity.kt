package com.example.soprintsgr.ui.tasks

import android.content.Context
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.soprintsgr.data.api.ArchivoAdjunto
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import android.app.DownloadManager
import android.os.Environment
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import okhttp3.OkHttpClient
import okhttp3.Request
import com.example.soprintsgr.data.SessionManager

class TaskInProgressActivity : AppCompatActivity() {
    private lateinit var tvTimer: TextView
    private lateinit var tvTaskName: TextView
    private lateinit var tvClienteName: TextView
    private lateinit var tvClienteAddress: TextView
    private lateinit var tvClientePhone: TextView
    private lateinit var btnCallClient: MaterialButton
    private lateinit var btnOpenMap: MaterialButton
    private lateinit var btnFinishTask: MaterialButton
    private lateinit var fabBack: FloatingActionButton
    
    // Attachments UI
    private lateinit var cvAttachments: View
    private lateinit var rvArchivosAdjuntos: RecyclerView
    private lateinit var cvComentario: View
    private lateinit var tvComentario: TextView
    private lateinit var tvVerMasComentario: TextView
    private lateinit var sessionManager: SessionManager
    
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
        sessionManager = SessionManager(this)
        
        initViews()
        loadTaskData()
        startTimer()
        setupListeners()
    }

    private fun initViews() {
        tvTimer = findViewById(R.id.tvTimer)
        tvTaskName = findViewById(R.id.tvTaskName)
        tvClienteName = findViewById(R.id.tvClienteName)
        tvClienteAddress = findViewById(R.id.tvClienteAddress)
        tvClientePhone = findViewById(R.id.tvClientePhone)
        btnCallClient = findViewById(R.id.btnCallClient)
        btnOpenMap = findViewById(R.id.btnOpenMap)
        btnFinishTask = findViewById(R.id.btnFinishTask)
        fabBack = findViewById(R.id.fabBack)

        // Attachments
        cvAttachments = findViewById(R.id.cvAttachments)
        rvArchivosAdjuntos = findViewById(R.id.rvArchivosAdjuntos)
        rvArchivosAdjuntos.layoutManager = LinearLayoutManager(this)

        // Comments
        cvComentario = findViewById(R.id.cvComentario)
        tvComentario = findViewById(R.id.tvComentario)
        tvVerMasComentario = findViewById(R.id.tvVerMasComentario)
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
            tvClienteName.text = task.cliente.nombre
            tvClienteAddress.text = task.cliente.direccion
            tvClientePhone.text = task.cliente.telefono

            // Load Attachments
            if (!task.archivosAdjuntos.isNullOrEmpty()) {
                cvAttachments.visibility = View.VISIBLE
                val adapter = ArchivoAdjuntoAdapter(task.archivosAdjuntos) { archivo ->
                    previewFile(archivo)
                }
                rvArchivosAdjuntos.adapter = adapter
            } else {
                cvAttachments.visibility = View.GONE
            }

            // Show Comentario if exists
            if (!task.comentario.isNullOrEmpty()) {
                cvComentario.visibility = View.VISIBLE
                tvComentario.text = task.comentario
                
                // Reset state
                tvComentario.maxLines = 3
                tvVerMasComentario.visibility = View.GONE
                tvVerMasComentario.text = "Ver más"
                
                // Check if text exceeds 3 lines
                // Check if text exceeds 3 lines
                tvComentario.post {
                    val layout = tvComentario.layout
                    if (layout != null) {
                        val lines = layout.lineCount
                        if (lines > 0) {
                            val ellipsisCount = layout.getEllipsisCount(lines - 1)
                            if (ellipsisCount > 0) {
                                tvVerMasComentario.visibility = View.VISIBLE
                                tvVerMasComentario.setOnClickListener {
                                    if (tvComentario.maxLines == 3) {
                                        // Expand
                                        tvComentario.maxLines = Int.MAX_VALUE
                                        tvVerMasComentario.text = "Ver menos"
                                    } else {
                                        // Collapse
                                        tvComentario.maxLines = 3
                                        tvVerMasComentario.text = "Ver más"
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                cvComentario.visibility = View.GONE
            }
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

    private fun previewFile(archivo: ArchivoAdjunto) {
        val extension = archivo.nombreOriginal.substringAfterLast('.', "").lowercase()

        when (extension) {
            "jpg", "jpeg", "png", "gif", "webp" -> previewImage(archivo)
            "pdf" -> previewPdf(archivo)
            else -> downloadFile(archivo)
        }
    }

    private fun previewImage(archivo: ArchivoAdjunto) {
        val token = sessionManager.getToken()
        if (token == null) {
            Toast.makeText(this, "Error: Token nulo", Toast.LENGTH_SHORT).show()
            return
        }

        val url = "https://seguimiento.srv1070869.hstgr.cloud/api/archivos/${archivo.idArchivo}"
        
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

    private fun previewPdf(archivo: ArchivoAdjunto) {
        val token = sessionManager.getToken() ?: return
        val url = "https://seguimiento.srv1070869.hstgr.cloud/api/archivos/${archivo.idArchivo}"
        
        // Show loading
        val progressDialog = android.app.ProgressDialog(this).apply {
            setMessage("Descargando PDF...")
            setCancelable(false)
            show()
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $token")
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) throw Exception("Failed to download file: ${response.code()}")

                val inputStream = response.body()?.byteStream() ?: throw Exception("Empty response body")
                val filename = "temp_${System.currentTimeMillis()}.pdf"
                val file = File(cacheDir, filename)
                val outputStream = FileOutputStream(file)

                inputStream.use { input ->
                    outputStream.use { output ->
                        input.copyTo(output)
                    }
                }

                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    openPdf(file)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    Toast.makeText(this@TaskInProgressActivity, "Error al descargar PDF: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun openPdf(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.provider",
                file
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "No se encontró una aplicación para abrir PDF", Toast.LENGTH_SHORT).show()
        }
    }

    private fun downloadFile(archivo: ArchivoAdjunto) {
        val token = sessionManager.getToken()
        if (token == null) {
            Toast.makeText(this, "Error de sesión", Toast.LENGTH_SHORT).show()
            return
        }

        val request = DownloadManager.Request(Uri.parse("https://seguimiento.srv1070869.hstgr.cloud/api/archivos/${archivo.idArchivo}"))
            .setTitle(archivo.nombreOriginal)
            .setDescription("Descargando archivo...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "SoprintSGR/${archivo.nombreOriginal}")
            .addRequestHeader("Authorization", "Bearer $token")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadManager.enqueue(request)
        Toast.makeText(this, "Descarga iniciada...", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(timerRunnable)
    }
}
