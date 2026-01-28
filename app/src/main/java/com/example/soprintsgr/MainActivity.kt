package com.example.soprintsgr

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.net.Uri
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.soprintsgr.data.ActiveTaskManager
import com.example.soprintsgr.data.LocationService
import com.example.soprintsgr.data.SessionManager
import com.example.soprintsgr.data.TaskRepository
import com.example.soprintsgr.data.api.RetrofitClient
import com.example.soprintsgr.data.api.Task
import com.example.soprintsgr.ui.login.LoginActivity
import com.example.soprintsgr.ui.tasks.TaskAdapter
import com.example.soprintsgr.ui.tasks.TaskInProgressActivity
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var emptyStateContainer: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var taskAdapter: TaskAdapter
    private lateinit var sessionManager: SessionManager
    private lateinit var activeTaskManager: ActiveTaskManager
    
    private var taskJob: Job? = null
    private var bannerTimerJob: Job? = null
    private var bannerView: View? = null
    private lateinit var overdueAlertBanner: LinearLayout
    private lateinit var tvOverdueCount: TextView
    
    // Filter state
    private var allTasks: List<Task> = emptyList()
    private var currentFilter: DateFilter = DateFilter.ALL
    
    private enum class DateFilter {
        ALL, TODAY, FUTURE
    }

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
            
            if (fineLocationGranted || coarseLocationGranted) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    checkBackgroundLocationPermission()
                } else {
                    startLocationService()
                }
            }
        }

    private val requestBackgroundLocationLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                startLocationService()
            } else {
                // We can still run with foreground permission, but warn user
                startLocationService()
                Toast.makeText(this, "Se recomienda permitir ubicación en segundo plano para un mejor seguimiento.", Toast.LENGTH_LONG).show()
            }
        }
    
    private val taskCompletedReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            if (intent?.action == "com.example.soprintsgr.TASK_COMPLETED") {
                loadTasks()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize SessionManager and RetrofitClient
        sessionManager = SessionManager(this)
        RetrofitClient.initialize(this)
        
        // Check if user is logged in
        if (!sessionManager.isLoggedIn()) {
            navigateToLogin()
            return
        }
        
        setContentView(R.layout.activity_main)

        activeTaskManager = ActiveTaskManager.getInstance(this)

        // Initialize views
        drawerLayout = findViewById(R.id.drawer_layout)
        recyclerView = findViewById(R.id.recyclerViewTasks)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        emptyStateContainer = findViewById(R.id.emptyStateContainer)
        progressBar = findViewById(R.id.progressBar)
        overdueAlertBanner = findViewById(R.id.overdueAlertBanner)
        tvOverdueCount = findViewById(R.id.tvOverdueCount)
        
        val navView: NavigationView = findViewById(R.id.nav_view)
        navView.setNavigationItemSelectedListener(this)
        
        // Update navigation header with user info
        updateNavigationHeader(navView)
        
        // Setup logout button in footer
        findViewById<View>(R.id.btnLogout).setOnClickListener {
            sessionManager.clearSession()
            drawerLayout.closeDrawer(GravityCompat.START)
            navigateToLogin()
        }

        // Setup Menu Button
        val btnMenu: ImageView = findViewById(R.id.btn_menu)
        btnMenu.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        // Setup RecyclerView
        setupRecyclerView()
        
        // Setup SwipeRefresh
        swipeRefreshLayout.setColorSchemeColors(
            ContextCompat.getColor(this, R.color.login_accent)
        )
        swipeRefreshLayout.setOnRefreshListener {
            loadTasks()
        }

        // Setup filter chips
        setupFilterChips()

        checkPermissionsAndStartService()
        setupActiveTaskBanner()
        
        // Load tasks
        loadTasks()
        
        // Register broadcast receiver for task completion
        val filter = android.content.IntentFilter("com.example.soprintsgr.TASK_COMPLETED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(taskCompletedReceiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(taskCompletedReceiver, filter)
        }
    }

    private fun setupRecyclerView() {
        taskAdapter = TaskAdapter(emptyList()) { task ->
            onTaskClicked(task)
        }
        
        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = taskAdapter
        }
    }

    private fun setupFilterChips() {
        val chipTodas = findViewById<Chip>(R.id.chipTodas)
        val chipMiDia = findViewById<Chip>(R.id.chipMiDia)
        val chipFuturas = findViewById<Chip>(R.id.chipFuturas)

        chipTodas.setOnClickListener {
            currentFilter = DateFilter.ALL
            applyFilter()
        }

        chipMiDia.setOnClickListener {
            currentFilter = DateFilter.TODAY
            applyFilter()
        }

        chipFuturas.setOnClickListener {
            currentFilter = DateFilter.FUTURE
            applyFilter()
        }
    }

    private fun applyFilter() {
        val filteredTasks = when (currentFilter) {
            DateFilter.ALL -> allTasks
            DateFilter.TODAY -> filterTasksForToday(allTasks)
            DateFilter.FUTURE -> filterTasksForFuture(allTasks)
        }

        // Sort tasks by fechaLimite (earliest first, overdue at top)
        val sortedTasks = sortTasksByDate(filteredTasks)

        if (sortedTasks.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyStateContainer.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyStateContainer.visibility = View.GONE
            taskAdapter.submitList(sortedTasks)
        }
    }

    private fun filterTasksForToday(tasks: List<Task>): List<Task> {
        val today = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val todayString = dateFormat.format(today.time)

        return tasks.filter { task ->
            try {
                val taskDate = task.fechaLimite.substring(0, 10) // Extract date part
                taskDate == todayString
            } catch (e: Exception) {
                false
            }
        }
    }

    private fun filterTasksForFuture(tasks: List<Task>): List<Task> {
        val today = Calendar.getInstance()
        today.set(Calendar.HOUR_OF_DAY, 0)
        today.set(Calendar.MINUTE, 0)
        today.set(Calendar.SECOND, 0)
        today.set(Calendar.MILLISECOND, 0)

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        return tasks.filter { task ->
            try {
                val taskDateString = task.fechaLimite.substring(0, 10)
                val taskDate = dateFormat.parse(taskDateString)
                taskDate != null && taskDate.after(today.time)
            } catch (e: Exception) {
                false
            }
        }
    }

    private fun sortTasksByDate(tasks: List<Task>): List<Task> {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val now = Date()
        
        return tasks.sortedWith(compareBy { task ->
            try {
                val fechaLimite = dateFormat.parse(task.fechaLimite)
                fechaLimite?.time ?: Long.MAX_VALUE
            } catch (e: Exception) {
                // Try alternative format
                try {
                    val altFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    altFormat.parse(task.fechaLimite)?.time ?: Long.MAX_VALUE
                } catch (e2: Exception) {
                    Long.MAX_VALUE
                }
            }
        })
    }

    private fun onTaskClicked(task: Task) {
        // Check if there's already an active task
        if (activeTaskManager.hasActiveTask()) {
            val activeTask = activeTaskManager.getActiveTask()
            if (activeTask?.idTarea == task.idTarea) {
                // Navigate to active task
                val intent = Intent(this, TaskInProgressActivity::class.java)
                startActivity(intent)
            } else {
                // Show message that there's already an active task
                Toast.makeText(
                    this,
                    "Ya tienes una tarea en proceso. Finalízala antes de iniciar otra.",
                    Toast.LENGTH_LONG
                ).show()
            }
            return
        }
        
        // Show task details dialog
        showTaskDetails(task)
    }

    private fun showTaskDetails(task: Task) {
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
        
        // Format fecha limite to readable format
        try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            val date = inputFormat.parse(task.fechaLimite)
            tvFechaLimite.text = date?.let { outputFormat.format(it) } ?: task.fechaLimite
        } catch (e: Exception) {
            tvFechaLimite.text = task.fechaLimite
        }
        
        // Show comentario if exists
        if (!task.comentario.isNullOrEmpty()) {
            layoutComentario.visibility = View.VISIBLE
            tvComentario.text = task.comentario
        } else {
            layoutComentario.visibility = View.GONE
        }
        
        // Create dialog first
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        
        // Set phone call click listener
        btnCallPhone.setOnClickListener {
            val intent = Intent(Intent.ACTION_DIAL)
            intent.data = android.net.Uri.parse("tel:${task.cliente.telefono}")
            startActivity(intent)
        }
        
        // Make address clickable to open maps
        tvClienteDireccion.setOnClickListener {
            val uri = android.net.Uri.parse("geo:${task.cliente.latitud},${task.cliente.longitud}?q=${task.cliente.latitud},${task.cliente.longitud}(${task.cliente.nombre})")
            val mapIntent = Intent(Intent.ACTION_VIEW, uri)
            mapIntent.setPackage("com.google.android.apps.maps")
            if (mapIntent.resolveActivity(packageManager) != null) {
                startActivity(mapIntent)
            } else {
                // If Google Maps is not installed, open in browser
                val browserIntent = Intent(Intent.ACTION_VIEW, 
                    android.net.Uri.parse("https://www.google.com/maps/search/?api=1&query=${task.cliente.latitud},${task.cliente.longitud}"))
                startActivity(browserIntent)
            }
        }
        
        // Show and configure btnIniciarTarea based on task state
        btnIniciarTarea.visibility = View.VISIBLE
        
        if (task.estadoTarea.nombre.uppercase() == "EN PROCESO" || task.estadoTarea.nombre.uppercase() == "EN_PROCESO") {
            btnIniciarTarea.text = "Continuar Tarea"
            btnIniciarTarea.setBackgroundColor(android.graphics.Color.parseColor("#FF9800"))
            btnIniciarTarea.setOnClickListener {
                dialog.dismiss()
                val intent = Intent(this, TaskInProgressActivity::class.java)
                startActivity(intent)
            }
        } else {
            btnIniciarTarea.text = "Iniciar Tarea"
            btnIniciarTarea.setOnClickListener {
                dialog.dismiss()
                iniciarTarea(task)
            }
        }
        
        // Show dialog
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun iniciarTarea(task: Task) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = RetrofitClient.api.iniciarTarea(task.idTarea)
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        // Save active task
                        activeTaskManager.startTask(task)
                        
                        // Navigate to TaskInProgressActivity
                        val intent = Intent(this@MainActivity, TaskInProgressActivity::class.java)
                        startActivity(intent)
                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            "Error al iniciar la tarea",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Error de conexión: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
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

    private fun loadTasks() {
        progressBar.visibility = View.VISIBLE
        emptyStateContainer.visibility = View.GONE
        
        taskJob?.cancel()
        taskJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val taskRepository = TaskRepository(this@MainActivity)
                val tasks = taskRepository.getMyTasks()
                
                // Filter out completed tasks
                val activeTasks = tasks.filter { 
                    it.estadoTarea.nombre.uppercase() != "COMPLETADA" 
                }
                
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    swipeRefreshLayout.isRefreshing = false
                    
                    // Store all active tasks
                    allTasks = activeTasks
                    
                    // Check for overdue tasks
                    checkOverdueTasks(activeTasks)
                    
                    // Apply current filter
                    applyFilter()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    swipeRefreshLayout.isRefreshing = false
                    Toast.makeText(
                        this@MainActivity,
                        "Error al cargar tareas: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun checkOverdueTasks(tasks: List<Task>) {
        val now = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        
        val overdueTasks = tasks.filter { task ->
            try {
                val fechaLimite = dateFormat.parse(task.fechaLimite)
                fechaLimite != null && fechaLimite.before(now.time)
            } catch (e: Exception) {
                // Try alternative format without 'T'
                try {
                    val altFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    val fechaLimite = altFormat.parse(task.fechaLimite)
                    fechaLimite != null && fechaLimite.before(now.time)
                } catch (e2: Exception) {
                    false
                }
            }
        }
        
        if (overdueTasks.isNotEmpty()) {
            overdueAlertBanner.visibility = View.VISIBLE
            tvOverdueCount.text = overdueTasks.size.toString()
        } else {
            overdueAlertBanner.visibility = View.GONE
        }
    }

    private fun setupActiveTaskBanner() {
        if (activeTaskManager.hasActiveTask()) {
            showActiveTaskBanner()
        }
    }

    private fun showActiveTaskBanner() {
        val bannerContainer = findViewById<android.widget.FrameLayout>(R.id.activeTaskBannerContainer)
        
        if (bannerView == null) {
            bannerView = layoutInflater.inflate(R.layout.banner_active_task, bannerContainer, false)
            bannerContainer.addView(bannerView)
            
            bannerView?.setOnClickListener {
                val intent = Intent(this, TaskInProgressActivity::class.java)
                startActivity(intent)
            }
        }
        
        bannerContainer.visibility = View.VISIBLE
        updateBannerInfo()
        startBannerTimerUpdates()
    }

    private fun updateBannerInfo() {
        val task = activeTaskManager.getActiveTask() ?: return
        
        bannerView?.let { banner ->
            val tvBannerTaskName = banner.findViewById<TextView>(R.id.tvBannerTaskName)
            val tvBannerClientName = banner.findViewById<TextView>(R.id.tvBannerClientName)
            
            tvBannerTaskName?.text = task.nombre
            tvBannerClientName?.text = task.cliente.nombre
        }
    }

    private fun startBannerTimerUpdates() {
        bannerTimerJob?.cancel()
        bannerTimerJob = CoroutineScope(Dispatchers.Main).launch {
            while (true) {
                updateBannerTimer()
                delay(1000) // Update every second
            }
        }
    }

    private fun updateBannerTimer() {
        if (!activeTaskManager.hasActiveTask()) {
            hideActiveTaskBanner()
            return
        }
        
        val elapsedMillis = activeTaskManager.getElapsedTime()
        val hours = java.util.concurrent.TimeUnit.MILLISECONDS.toHours(elapsedMillis)
        val minutes = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(elapsedMillis) % 60
        val seconds = java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(elapsedMillis) % 60
        
        bannerView?.let { banner ->
            val tvBannerTimer = banner.findViewById<TextView>(R.id.tvBannerTimer)
            tvBannerTimer?.text = String.format("%02d:%02d:%02d", hours, minutes, seconds)
        }
    }

    private fun hideActiveTaskBanner() {
        val bannerContainer = findViewById<android.widget.FrameLayout>(R.id.activeTaskBannerContainer)
        bannerContainer.visibility = View.GONE
        bannerTimerJob?.cancel()
    }

    override fun onResume() {
        super.onResume()
        // Check if task is still active when returning to MainActivity
        if (activeTaskManager.hasActiveTask()) {
            showActiveTaskBanner()
        } else {
            hideActiveTaskBanner()
        }
        // Reload tasks
        loadTasks()
    }

    override fun onDestroy() {
        super.onDestroy()
        taskJob?.cancel()
        bannerTimerJob?.cancel()
        try {
            unregisterReceiver(taskCompletedReceiver)
        } catch (e: Exception) {
            // Receiver not registered
        }
    }

    private fun updateNavigationHeader(navView: NavigationView) {
        val headerView = navView.getHeaderView(0)
        val tvUserFullName = headerView.findViewById<TextView>(R.id.tvUserFullName)
        val tvUsername = headerView.findViewById<TextView>(R.id.tvUsername)
        val ivProfilePhoto = headerView.findViewById<ImageView>(R.id.ivProfilePhoto)
        
        tvUserFullName.text = sessionManager.getNombreCompleto()
        tvUsername.text = "@${sessionManager.getUsername()}"
        
        // Load profile photo with Glide
        val fotoPerfilUrl = sessionManager.getFotoPerfil()
        if (!fotoPerfilUrl.isNullOrEmpty()) {
            com.bumptech.glide.Glide.with(this)
                .load(fotoPerfilUrl)
                .placeholder(R.drawable.ic_logo_header)
                .error(R.drawable.ic_logo_header)
                .into(ivProfilePhoto)
        } else {
            ivProfilePhoto.setImageResource(R.drawable.ic_logo_header)
        }
    }
    
    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_home -> {
                // Already on home, just close drawer
            }
            R.id.nav_history -> {
                val intent = Intent(this, com.example.soprintsgr.ui.completed.CompletedTasksActivity::class.java)
                startActivity(intent)
            }
        }
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun checkPermissionsAndStartService() {
        val permissionsToRequest = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                checkBatteryOptimization()
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                checkBackgroundLocationPermission()
            } else {
                startLocationService()
            }
        }
    }

    private fun checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent().apply {
                    action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
                // Proceed to check background location after returning or immediately
                // For simplicity, we can chain it, but usually user comes back.
                // We'll rely on onResume to re-check or proceed here.
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    checkBackgroundLocationPermission()
                } else {
                    startLocationService()
                }
            }
        } else {
            startLocationService()
        }
    }

    private fun checkBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // Show rationale
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Ubicación en segundo plano")
                    .setMessage("Para que tus supervisores puedan ver tu ruta completa incluso con la app cerrada, selecciona \"Permitir todo el tiempo\" en la siguiente pantalla.")
                    .setPositiveButton("Entendido") { _, _ ->
                        requestBackgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    }
                    .setNegativeButton("Ahora no") { _, _ ->
                        startLocationService()
                    }
                    .create()
                    .show()
            } else {
                startLocationService()
            }
        }
    }

    private fun startLocationService() {
        val intent = Intent(this, LocationService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}