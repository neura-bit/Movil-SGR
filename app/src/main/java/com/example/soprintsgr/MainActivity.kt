package com.example.soprintsgr

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
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
import com.example.soprintsgr.ui.login.LoginActivity
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
    private var isDarkTheme = false
    private var messengerJob: Job? = null
    private val messengerMarkers = mutableMapOf<Int, Marker>()

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
        setContentView(R.layout.activity_main)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        drawerLayout = findViewById(R.id.drawer_layout)
        val navView: NavigationView = findViewById(R.id.nav_view)
        navView.setNavigationItemSelectedListener(this)

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

        checkPermissionsAndStartService()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        enableMyLocation()
        getDeviceLocation()
        
        // Default to dark theme if preferred, or just standard
        // toggleMapTheme() // Uncomment to start in dark mode

        startMessengerUpdates()
        
        map.setOnMarkerClickListener { marker ->
            val messenger = marker.tag as? Messenger
            if (messenger != null) {
                showMessengerDetails(messenger)
            }
            false
        }
    }

    private fun startMessengerUpdates() {
        messengerJob?.cancel()
        messengerJob = CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                val messengers = LocationRepository.getMessengers()
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

    override fun onDestroy() {
        super.onDestroy()
        messengerJob?.cancel()
    }

    private fun enableMyLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        ) {
            if (::map.isInitialized) {
                map.isMyLocationEnabled = true
                map.uiSettings.isMyLocationButtonEnabled = false // We will use our own logic or rely on auto-center
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
                            updateLocationStatus(lastKnownLocation.latitude, lastKnownLocation.longitude)
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

    private fun updateLocationStatus(lat: Double, lon: Double) {
        val tvLocationStatus: TextView = findViewById(R.id.tv_location_status)
        tvLocationStatus.text = "Ubicación: $lat, $lon"
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
            map.setMapStyle(null) // Reset to standard style
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_home -> {
                // Handle Home
            }
            R.id.nav_deliveries -> {
                // Handle Deliveries
            }
            R.id.nav_logout -> {
                val intent = Intent(this, LoginActivity::class.java)
                startActivity(intent)
                finish()
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