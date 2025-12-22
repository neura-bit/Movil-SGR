package com.example.soprintsgr

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.example.soprintsgr.data.LocationService
import com.example.soprintsgr.data.SessionManager
import com.example.soprintsgr.data.TaskRepository
import com.example.soprintsgr.data.api.RetrofitClient
import com.example.soprintsgr.data.api.Task
import com.example.soprintsgr.ui.login.LoginActivity
import com.example.soprintsgr.ui.tasks.TasksActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.soprintsgr.data.LocationRepository
import com.example.soprintsgr.data.api.Messenger

class MainActivity : AppCompatActivity(), OnMapReadyCallback, NavigationView.OnNavigationItemSelectedListener {

    private lateinit var map: GoogleMap
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var sessionManager: SessionManager
    private var isDarkTheme = false
    private var messengerJob: Job? = null
    private var taskJob: Job? = null
    private val messengerMarkers = mutableMapOf<Int, Marker>()
    private val taskMarkers = mutableMapOf<Int, Marker>()

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
            
            if (fineLocationGranted || coarseLocationGranted) {
                startLocationService()
                enableMyLocation()
                getDeviceLocation()
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

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        drawerLayout = findViewById(R.id.drawer_layout)
        val navView: NavigationView = findViewById(R.id.nav_view)
        navView.setNavigationItemSelectedListener(this)
        
        // Update navigation header with user info
        updateNavigationHeader(navView)

        // Setup Menu Button
        val btnMenu: ImageView = findViewById(R.id.btn_menu)
        btnMenu.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        val fabThemeToggle: FloatingActionButton = findViewById(R.id.fab_theme_toggle)
        fabThemeToggle.setOnClickListener {
            toggleMapTheme()
        }

        // Setup Center Location FAB
        val fabCenterLocation: FloatingActionButton = findViewById(R.id.fab_center_location)
        fabCenterLocation.setOnClickListener {
            centerOnMyLocation()
        }

        checkPermissionsAndStartService()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        enableMyLocation()
        getDeviceLocation()
        
        // Apply clean map style by default (removes POIs and clutter)
        applyCleanMapStyle()

        startMessengerUpdates()
        startTaskUpdates()
        
        map.setOnMarkerClickListener { marker ->
            val messenger = marker.tag as? Messenger
            if (messenger != null) {
                showMessengerDetails(messenger)
                return@setOnMarkerClickListener false
            }
            
            val task = marker.tag as? Task
            if (task != null) {
                showTaskDetails(task)
                return@setOnMarkerClickListener false
            }
            
            false
        }
    }

    private fun startMessengerUpdates() {
        // Only show other messengers for ADMIN and ASESOR roles
        val userRole = sessionManager.getRol()
        if (userRole == "MENSAJERO") {
            // Messengers should not see other messengers, only their own location
            return
        }
        
        messengerJob?.cancel()
        messengerJob = CoroutineScope(Dispatchers.IO).launch {
            val locationRepository = LocationRepository(this@MainActivity)
            while (true) {
                val messengers = locationRepository.getMessengers()
                withContext(Dispatchers.Main) {
                    updateMessengerMarkers(messengers)
                }
                delay(30000) // Update every 30 seconds
            }
        }
    }

    private fun updateMessengerMarkers(messengers: List<Messenger>) {
        // Remove markers for messengers that are no longer active (optional, depending on requirements)
        // For now, we just update or add
        
        for (messenger in messengers) {
            val position = LatLng(messenger.latitud, messenger.longitud)
            val existingMarker = messengerMarkers[messenger.idMensajero]

            if (existingMarker != null) {
                existingMarker.position = position
                existingMarker.tag = messenger // Update tag with latest data
            } else {
                val marker = map.addMarker(
                    MarkerOptions()
                        .position(position)
                        .title(messenger.nombreCompleto)
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
                )
                if (marker != null) {
                    marker.tag = messenger
                    messengerMarkers[messenger.idMensajero] = marker
                }
            }
        }
    }

    private fun showMessengerDetails(messenger: Messenger) {
        val message = """
            Nombre: ${messenger.nombreCompleto}
            Tarea: ${messenger.nombreTareaActual}
            Actualizado: ${messenger.fechaUltimaActualizacion}
        """.trimIndent()

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Detalles del Mensajero")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun startTaskUpdates() {
        taskJob?.cancel()
        taskJob = CoroutineScope(Dispatchers.IO).launch {
            val taskRepository = TaskRepository(this@MainActivity)
            while (true) {
                val tasks = taskRepository.getMyTasks()
                withContext(Dispatchers.Main) {
                    updateTaskMarkers(tasks)
                }
                delay(1800000) // 30 minutes = 1800000 ms
            }
        }
    }

    private fun updateTaskMarkers(tasks: List<Task>) {
        // Remove all existing task markers
        taskMarkers.values.forEach { it.remove() }
        taskMarkers.clear()
        
        val activeTasks = mutableListOf<Task>()
        
        // Add new task markers (exclude completed tasks)
        for (task in tasks) {
            // Skip completed tasks
            if (task.estadoTarea.nombre.uppercase() == "COMPLETADA") {
                continue
            }
            
            activeTasks.add(task)
            
            val position = LatLng(task.cliente.latitud, task.cliente.longitud)
            
            // Use custom icon for task markers (scaled to 4% = 96% smaller)
            val icon = try {
                val bitmap = android.graphics.BitmapFactory.decodeResource(resources, R.drawable.icon)
                val scaledBitmap = android.graphics.Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * 0.04).toInt(),
                    (bitmap.height * 0.04).toInt(),
                    false
                )
                BitmapDescriptorFactory.fromBitmap(scaledBitmap)
            } catch (e: Exception) {
                // Fallback to colored markers if icon not found
                val markerColor = getTaskMarkerColor(task.estadoTarea.nombre)
                BitmapDescriptorFactory.defaultMarker(markerColor)
            }
            
            val marker = map.addMarker(
                MarkerOptions()
                    .position(position)
                    .title("${task.nombre}")
                    .snippet("${task.cliente.nombre} - ${task.tipoOperacion.nombre}")
                    .icon(icon)
            )
            
            if (marker != null) {
                marker.tag = task
                taskMarkers[task.idTarea] = marker
            }
        }
        
        // Center camera on nearest task on first load
        if (activeTasks.isNotEmpty() && taskMarkers.size == activeTasks.size) {
            centerOnNearestTask(activeTasks)
        }
    }

    private fun getTaskMarkerColor(estado: String): Float {
        return when (estado.uppercase()) {
            "CREADA" -> BitmapDescriptorFactory.HUE_GREEN
            "EN_PROCESO", "EN PROCESO" -> BitmapDescriptorFactory.HUE_YELLOW
            "CANCELADA" -> BitmapDescriptorFactory.HUE_AZURE // Gray-ish
            else -> BitmapDescriptorFactory.HUE_ORANGE
        }
    }

    private fun centerOnNearestTask(tasks: List<Task>) {
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            ) {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    if (location != null) {
                        val currentLatLng = LatLng(location.latitude, location.longitude)
                        
                        // Find nearest task
                        var nearestTask: Task? = null
                        var minDistance = Float.MAX_VALUE
                        
                        for (task in tasks) {
                            val taskLatLng = LatLng(task.cliente.latitud, task.cliente.longitud)
                            val results = FloatArray(1)
                            android.location.Location.distanceBetween(
                                currentLatLng.latitude, currentLatLng.longitude,
                                taskLatLng.latitude, taskLatLng.longitude,
                                results
                            )
                            
                            if (results[0] < minDistance) {
                                minDistance = results[0]
                                nearestTask = task
                            }
                        }
                        
                        // Center camera to show both current location and nearest task
                        nearestTask?.let {
                            val taskPosition = LatLng(it.cliente.latitud, it.cliente.longitud)
                            
                            // Create bounds that include both points
                            val boundsBuilder = com.google.android.gms.maps.model.LatLngBounds.Builder()
                            boundsBuilder.include(currentLatLng)
                            boundsBuilder.include(taskPosition)
                            val bounds = boundsBuilder.build()
                            
                            // Add more padding for better centering (not at corners)
                            val padding = 400 // padding in pixels
                            val cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, padding)
                            map.animateCamera(cameraUpdate)
                        }
                    }
                }
            }
        } catch (e: SecurityException) {
            // Log error
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
        tvTaskEstado.setBackgroundColor(getTaskEstadoColor(task.estadoTarea.nombre))
        
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
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Cerrar", null)
            .create()
        
        // Set phone call click listener
        btnCallPhone.setOnClickListener {
            val intent = Intent(Intent.ACTION_DIAL)
            intent.data = android.net.Uri.parse("tel:${task.cliente.telefono}")
            startActivity(intent)
        }
        
        // Set open map click listener - center on task location
        btnOpenMap.setOnClickListener {
            val position = LatLng(task.cliente.latitud, task.cliente.longitud)
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(position, 17f))
            dialog.dismiss()
        }
        
        // Hide finalizar button in map view (only show in TasksActivity)
        btnFinalizar.visibility = View.GONE
        
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

    override fun onDestroy() {
        super.onDestroy()
        messengerJob?.cancel()
        taskJob?.cancel()
    }

    private fun enableMyLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        ) {
            if (::map.isInitialized) {
                map.isMyLocationEnabled = true
                map.uiSettings.isMyLocationButtonEnabled = true // Enable location button
            }
        }
    }

    private fun getDeviceLocation() {
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            ) {
                val locationResult = fusedLocationClient.lastLocation
                locationResult.addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        val lastKnownLocation = task.result
                        if (lastKnownLocation != null) {
                            map.moveCamera(
                                CameraUpdateFactory.newLatLngZoom(
                                    LatLng(lastKnownLocation.latitude, lastKnownLocation.longitude), 15f
                                )
                            )
                        }
                    } else {
                        map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(-0.1807, -78.4678), 15f)) // Default to Quito
                        map.uiSettings.isMyLocationButtonEnabled = false
                    }
                }
            }
        } catch (e: SecurityException) {
            // Log error
        }
    }



    private fun applyCleanMapStyle() {
        if (!::map.isInitialized) return

        try {
            val success = map.setMapStyle(
                MapStyleOptions.loadRawResourceStyle(
                    this, R.raw.map_style_clean
                )
            )
            if (!success) {
                // Log error - style couldn't be applied
            }
        } catch (e: Exception) {
            // Log error - exception loading style
        }
    }

    private fun toggleMapTheme() {
        if (!::map.isInitialized) return

        isDarkTheme = !isDarkTheme
        if (isDarkTheme) {
            try {
                val success = map.setMapStyle(
                    MapStyleOptions.loadRawResourceStyle(
                        this, R.raw.map_style_dark
                    )
                )
                if (!success) {
                    // Log error
                }
            } catch (e: Exception) {
                // Log error
            }
        } else {
            // Apply clean style instead of default
            applyCleanMapStyle()
        }
    }

    private fun updateNavigationHeader(navView: NavigationView) {
        val headerView = navView.getHeaderView(0)
        val tvUserFullName = headerView.findViewById<TextView>(R.id.tvUserFullName)
        val tvUsername = headerView.findViewById<TextView>(R.id.tvUsername)
        
        tvUserFullName.text = sessionManager.getNombreCompleto()
        tvUsername.text = "@${sessionManager.getUsername()}"
    }
    
    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun centerOnMyLocation() {
        if (!::map.isInitialized) return
        
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            ) {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    if (location != null) {
                        val currentLatLng = LatLng(location.latitude, location.longitude)
                        map.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 16f))
                    }
                }
            }
        } catch (e: SecurityException) {
            // Log error
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_home -> {
                // Handle Home
            }
            R.id.nav_tasks -> {
                val intent = Intent(this, TasksActivity::class.java)
                startActivity(intent)
            }
            R.id.nav_deliveries -> {
                val intent = Intent(this, com.example.soprintsgr.ui.completed.CompletedTasksActivity::class.java)
                startActivity(intent)
            }
            R.id.nav_logout -> {
                sessionManager.clearSession()
                navigateToLogin()
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
            startLocationService()
            enableMyLocation()
            getDeviceLocation()
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