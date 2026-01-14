package com.example.soprintsgr.ui.completed

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
import com.example.soprintsgr.ui.tasks.TaskAdapter
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class CompletedTasksActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var taskAdapter: TaskAdapter
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmptyState: TextView
    private lateinit var chipGroupFilter: ChipGroup
    private lateinit var chipToday: Chip
    private lateinit var chipYesterday: Chip
    private lateinit var chipWeek: Chip
    private lateinit var taskRepository: TaskRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_completed_tasks)

        // Setup toolbar
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        taskRepository = TaskRepository(this)
        
        recyclerView = findViewById(R.id.recyclerViewCompletedTasks)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        progressBar = findViewById(R.id.progressBar)
        tvEmptyState = findViewById(R.id.tvEmptyState)
        chipGroupFilter = findViewById(R.id.chipGroupFilter)
        chipToday = findViewById(R.id.chipToday)
        chipYesterday = findViewById(R.id.chipYesterday)
        chipWeek = findViewById(R.id.chipWeek)
        
        setupRecyclerView()
        setupSwipeRefresh()
        setupChipFilters()
        
        // Load today's tasks by default
        loadCompletedTasks(FilterPeriod.TODAY)
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
            val selectedPeriod = when (chipGroupFilter.checkedChipId) {
                R.id.chipToday -> FilterPeriod.TODAY
                R.id.chipYesterday -> FilterPeriod.YESTERDAY
                R.id.chipWeek -> FilterPeriod.WEEK
                else -> FilterPeriod.TODAY
            }
            loadCompletedTasks(selectedPeriod)
        }
    }

    private fun setupChipFilters() {
        chipGroupFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val period = when (checkedIds.first()) {
                    R.id.chipToday -> FilterPeriod.TODAY
                    R.id.chipYesterday -> FilterPeriod.YESTERDAY
                    R.id.chipWeek -> FilterPeriod.WEEK
                    else -> FilterPeriod.TODAY
                }
                loadCompletedTasks(period)
            }
        }
    }

    private fun loadCompletedTasks(period: FilterPeriod) {
        lifecycleScope.launch {
            try {
                showLoading(true)
                val (fechaInicio, fechaFin) = getDateRange(period)
                
                android.util.Log.d("CompletedTasks", "Fetching with dates: $fechaInicio to $fechaFin")
                val tasks = taskRepository.getCompletedTasks(fechaInicio, fechaFin)
                android.util.Log.d("CompletedTasks", "Received ${tasks.size} tasks")
                
                if (tasks.isEmpty()) {
                    showEmptyState(true)
                } else {
                    showEmptyState(false)
                    taskAdapter.updateTasks(tasks)
                }
            } catch (e: Exception) {
                // Handle error
                android.util.Log.e("CompletedTasks", "Error loading tasks", e)
                showEmptyState(true)
            } finally {
                showLoading(false)
                swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    private fun getDateRange(period: FilterPeriod): Pair<String, String> {
        val calendarStart = Calendar.getInstance()
        val calendarEnd = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        
        when (period) {
            FilterPeriod.TODAY -> {
                // Start: today at 00:00:00
                calendarStart.set(Calendar.HOUR_OF_DAY, 0)
                calendarStart.set(Calendar.MINUTE, 0)
                calendarStart.set(Calendar.SECOND, 0)
                // End: today at 23:59:59
                calendarEnd.set(Calendar.HOUR_OF_DAY, 23)
                calendarEnd.set(Calendar.MINUTE, 59)
                calendarEnd.set(Calendar.SECOND, 59)
            }
            FilterPeriod.YESTERDAY -> {
                // Start: yesterday at 00:00:00
                calendarStart.add(Calendar.DAY_OF_MONTH, -1)
                calendarStart.set(Calendar.HOUR_OF_DAY, 0)
                calendarStart.set(Calendar.MINUTE, 0)
                calendarStart.set(Calendar.SECOND, 0)
                // End: yesterday at 23:59:59
                calendarEnd.add(Calendar.DAY_OF_MONTH, -1)
                calendarEnd.set(Calendar.HOUR_OF_DAY, 23)
                calendarEnd.set(Calendar.MINUTE, 59)
                calendarEnd.set(Calendar.SECOND, 59)
            }
            FilterPeriod.WEEK -> {
                // Start: 7 days ago at 00:00:00
                calendarStart.add(Calendar.DAY_OF_MONTH, -7)
                calendarStart.set(Calendar.HOUR_OF_DAY, 0)
                calendarStart.set(Calendar.MINUTE, 0)
                calendarStart.set(Calendar.SECOND, 0)
                // End: today at 23:59:59
                calendarEnd.set(Calendar.HOUR_OF_DAY, 23)
                calendarEnd.set(Calendar.MINUTE, 59)
                calendarEnd.set(Calendar.SECOND, 59)
            }
        }
        
        val fechaInicio = dateFormat.format(calendarStart.time)
        val fechaFin = dateFormat.format(calendarEnd.time)
        
        android.util.Log.d("CompletedTasks", "Filter: $period, fechaInicio: $fechaInicio, fechaFin: $fechaFin")
        
        return Pair(fechaInicio, fechaFin)
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
        val btnIniciarTarea = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnIniciarTarea)
        
        // Set task data
        tvTaskTitle.text = task.nombre
        tvTaskEstado.text = task.estadoTarea.nombre
        
        // Set estado background color
        tvTaskEstado.setBackgroundColor(getTaskEstadoColor(task.estadoTarea.nombre))
        
        // Set cliente data
        tvClienteNombre.text = task.cliente.nombre
        tvClienteDireccion.text = task.cliente.direccion
        tvClienteTelefono.text = task.cliente.telefono
        
        // Set task details
        tvTipoOperacion.text = task.tipoOperacion.nombre
        tvCategoria.text = task.categoria.nombre
        tvFechaLimite.text = task.fechaLimite
        
        // Show comentario and observacion
        val fullComment = buildString {
            if (!task.comentario.isNullOrEmpty()) {
                append("Comentario: ${task.comentario}")
            }
            if (!task.observacion.isNullOrEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append("Observación: ${task.observacion}")
            }
        }
        
        if (fullComment.isNotEmpty()) {
            layoutComentario.visibility = View.VISIBLE
            tvComentario.text = fullComment
        } else {
            layoutComentario.visibility = View.GONE
        }
        
        // Create dialog first
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        
        // Set phone call click listener
        btnCallPhone.setOnClickListener {
            val intent = Intent(Intent.ACTION_DIAL)
            intent.data = Uri.parse("tel:${task.cliente.telefono}")
            startActivity(intent)
        }
        
        // Make address clickable to open maps
        tvClienteDireccion.setOnClickListener {
            val uri = "geo:${task.cliente.latitud},${task.cliente.longitud}?q=${task.cliente.latitud},${task.cliente.longitud}"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
            intent.setPackage("com.google.android.apps.maps")
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else {
                val browserIntent = Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/maps/search/?api=1&query=${task.cliente.latitud},${task.cliente.longitud}"))
                startActivity(browserIntent)
            }
        }
        
        // Hide button (tasks are already completed)
        btnIniciarTarea.visibility = View.GONE
        
        // Show dialog
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun getTaskEstadoColor(estado: String): Int {
        return when (estado.uppercase()) {
            "CREADA" -> android.graphics.Color.parseColor("#4CAF50") // Green
            "EN_PROCESO", "EN PROCESO" -> android.graphics.Color.parseColor("#FF9800") // Orange
            "COMPLETADA" -> android.graphics.Color.parseColor("#2196F3") // Blue
            "CANCELADA" -> android.graphics.Color.parseColor("#9E9E9E") // Gray
            else -> android.graphics.Color.parseColor("#607D8B") // Default gray
        }
    }

    enum class FilterPeriod {
        TODAY,
        YESTERDAY,
        WEEK
    }
}
