package com.example.collegebustracker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.collegebustracker.ui.theme.CollegeBusTrackerTheme
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.FirebaseApp
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.launch
import java.util.UUID

// --- CONFIGURATION ---
val database = Firebase.database(
    "https://collegebustracker-85e6a-default-rtdb.asia-southeast1.firebasedatabase.app"
)

private fun Any?.asDouble(): Double? {
    return when (this) {
        is Long -> this.toDouble()
        is Double -> this
        is String -> this.toDoubleOrNull()
        else -> null
    }
}

private fun Any?.asLong(): Long? {
    return when (this) {
        is Long -> this
        is Double -> this.toLong()
        is String -> this.toLongOrNull()
        else -> null
    }
}

// --- DATA CLASSES ---
data class Stop(
    val name: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
)

data class Bus(
    val plate: String,
    val routeName: String = "",
    val busNumber: Long = 0,
    val isLive: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val speed: Double = 0.0
)

data class RouteResult(
    val points: List<LatLng>,
    val distanceKm: Float,
    val durationMin: Int
)

///////////////////////////////////////////////////////////
// DEVICE ID
///////////////////////////////////////////////////////////

fun getOrCreateDeviceId(context: Context): String {
    val prefs = context.getSharedPreferences("device_prefs", Context.MODE_PRIVATE)
    var id = prefs.getString("device_id", null)
    if (id == null) {
        id = UUID.randomUUID().toString()
        prefs.edit { putString("device_id", id) }
    }
    return id
}

///////////////////////////////////////////////////////////
// MAIN ACTIVITY
///////////////////////////////////////////////////////////

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)

        setContent {
            CollegeBusTrackerTheme {
                val navController = rememberNavController()
                NavHost(navController, startDestination = "mode") {
                    composable("mode") { ModeSelectionScreen(navController) }
                    composable("driverLogin") { DriverLoginScreen(navController) }
                    composable("driverInfo/{plate}") {
                        val plate = it.arguments?.getString("plate") ?: ""
                        DriverInfoScreen(navController, plate)
                    }
                    composable("driverMap/{plate}") {
                        val plate = it.arguments?.getString("plate") ?: ""
                        DriverMapScreen(navController, plate)
                    }
                    composable("student") { StudentScreen() }
                }
            }
        }
    }
}

///////////////////////////////////////////////////////////
// MODE SCREEN
///////////////////////////////////////////////////////////

@Composable
fun ModeSelectionScreen(navController: NavController) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "College Bus Tracker",
                style = MaterialTheme.typography.headlineLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Smart campus transport system",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(50.dp))
            Card(
                shape = RoundedCornerShape(28.dp),
                elevation = CardDefaults.cardElevation(10.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Button(
                        onClick = { navController.navigate("driverLogin") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text("🚍 Driver Mode")
                    }
                    OutlinedButton(
                        onClick = { navController.navigate("student") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text("🎓 Student Mode")
                    }
                }
            }
        }
    }
}


///////////////////////////////////////////////////////////
// DRIVER LOGIN
///////////////////////////////////////////////////////////

@Composable
fun DriverLoginScreen(navController: NavController) {
    val busesRef = database.getReference("buses")
    val context = LocalContext.current
    val deviceId = getOrCreateDeviceId(context)

    var plate by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(shape = RoundedCornerShape(24.dp)) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Driver Login", style = MaterialTheme.typography.headlineMedium)
                OutlinedTextField(
                    value = plate,
                    onValueChange = { plate = it.uppercase().trim() },
                    label = { Text("Bus Plate") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it.trim() },
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (error.isNotEmpty())
                    Text(error, color = MaterialTheme.colorScheme.error)
                Button(
                    onClick = {
                        busesRef.child(plate).get().addOnSuccessListener { snapshot ->
                            if (!snapshot.exists()) {
                                error = "Bus not found"
                                return@addOnSuccessListener
                            }

                            val storedPassword = snapshot.child("password").getValue(String::class.java)
                            val isLive = snapshot.child("isLive").getValue(Boolean::class.java) ?: false
                            val activeDevice = snapshot.child("activeDeviceId").getValue(String::class.java) ?: ""

                            if (storedPassword != password) {
                                error = "Invalid password"
                                return@addOnSuccessListener
                            }

                            if (isLive && activeDevice != deviceId) {
                                error = "Bus already active on another device"
                                return@addOnSuccessListener
                            }

                            navController.navigate("driverInfo/$plate")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(50)
                ) { Text("Login") }
            }
        }
    }
}


///////////////////////////////////////////////////////////
// DRIVER INFO
///////////////////////////////////////////////////////////

@Composable
fun DriverInfoScreen(navController: NavController, plate: String) {
    val busRef = database.getReference("buses").child(plate)
    val context = LocalContext.current
    val deviceId = getOrCreateDeviceId(context)

    var route by remember { mutableStateOf("") }
    var busNumber by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        busRef.get().addOnSuccessListener {
            route = it.child("routeName").getValue(String::class.java) ?: ""
            busNumber = it.child("busNumber").value.asLong()?.toString() ?: ""
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(shape = RoundedCornerShape(28.dp)) {
            Column(
                modifier = Modifier.padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Bus Details", style = MaterialTheme.typography.labelMedium)
                Text(plate, style = MaterialTheme.typography.headlineMedium)
                HorizontalDivider()
                Text("Bus Number")
                Text(busNumber, style = MaterialTheme.typography.titleLarge)
                Text("Route")
                Text(route, style = MaterialTheme.typography.titleLarge)
                Button(
                    onClick = {
                        busRef.runTransaction(object : Transaction.Handler {
                            override fun doTransaction(currentData: MutableData): Transaction.Result {
                                val isLive = currentData.child("isLive").getValue(Boolean::class.java) ?: false
                                val activeDevice = currentData.child("activeDeviceId").getValue(String::class.java) ?: ""

                                if (isLive && activeDevice != deviceId) {
                                    return Transaction.abort()
                                }

                                currentData.child("isLive").value = true
                                currentData.child("activeDeviceId").value = deviceId
                                currentData.child("timestamp").value = System.currentTimeMillis()

                                return Transaction.success(currentData)
                            }

                            override fun onComplete(
                                error: DatabaseError?,
                                committed: Boolean,
                                currentData: DataSnapshot?
                            ) {
                                if (committed)
                                    navController.navigate("driverMap/$plate")
                            }
                        })
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(50)
                ) { Text("Start Live") }
            }
        }
    }
}

///////////////////////////////////////////////////////////
// DRIVER MAP
///////////////////////////////////////////////////////////

@Composable
fun DriverMapScreen(navController: NavController, plate: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val busRef = database.getReference("buses").child(plate)
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    var hasPermission by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(28.6139, 77.2090), 10f)
    }

    var locationCallback by remember { mutableStateOf<LocationCallback?>(null) }

    DisposableEffect(hasPermission) {
        if (hasPermission) {
            val request = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                1000L
            )
                .setMinUpdateIntervalMillis(500L)
                .setWaitForAccurateLocation(false)
                .build()

            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val location = result.lastLocation ?: return
                    val latLng = LatLng(location.latitude, location.longitude)

                    scope.launch {
                        cameraPositionState.animate(
                            CameraUpdateFactory.newCameraPosition(
                                CameraPosition.fromLatLngZoom(latLng, 16f)
                            ),
                            durationMs = 1000
                        )
                    }

                    busRef.updateChildren(
                        mapOf(
                            "latitude" to location.latitude,
                            "longitude" to location.longitude,
                            "speed" to (location.speed * 3.6),
                            "timestamp" to System.currentTimeMillis()
                        )
                    )
                }
            }

            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                fusedLocationClient.requestLocationUpdates(
                    request,
                    callback,
                    Looper.getMainLooper()
                )
                locationCallback = callback
            }

            onDispose {
                locationCallback?.let {
                    fusedLocationClient.removeLocationUpdates(it)
                }
            }
        } else {
            onDispose {}
        }
    }

    Box(Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = hasPermission)
        )
        Button(
            onClick = {
                locationCallback?.let {
                    fusedLocationClient.removeLocationUpdates(it)
                }
                busRef.updateChildren(
                    mapOf(
                        "isLive" to false,
                        "activeDeviceId" to ""
                    )
                ).addOnCompleteListener {
                    navController.popBackStack()
                }
            },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(20.dp),
            shape = RoundedCornerShape(50)
        ) {
            Text("End Trip")
        }
    }
}

///////////////////////////////////////////////////////////
// BUS ITEM COMPOSABLE
///////////////////////////////////////////////////////////
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusItem(
    bus: Bus,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(bus.routeName)
                Text("Bus No: ${bus.busNumber}")
            }

            if (bus.isLive) {
                Text("● Live", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

///////////////////////////////////////////////////////////
// STUDENT PANEL
///////////////////////////////////////////////////////////

@Composable
fun StudentScreen() {

    // --- NO API KEY NEEDED ---
    // Using OSRM Public Server for Free Routing
    // -------------------------

    var buses by remember { mutableStateOf<List<Bus>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedBus by remember { mutableStateOf<Bus?>(null) }
    var liveDistance by remember { mutableStateOf(0f) }
    var etaMinutes by remember { mutableStateOf(0) }
    var roadRoute by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var isMapFullScreen by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val fusedLocationClient =
        remember { LocationServices.getFusedLocationProviderClient(context) }

    var studentLocation by remember { mutableStateOf<LatLng?>(null) }
    var hasLocationPermission by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasLocationPermission = granted }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    // 🔹 Student Location
    DisposableEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            val request = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                2000L
            ).build()

            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let {
                        studentLocation = LatLng(it.latitude, it.longitude)
                    }
                }
            }

            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                fusedLocationClient.requestLocationUpdates(
                    request,
                    callback,
                    Looper.getMainLooper()
                )
            }

            onDispose {
                fusedLocationClient.removeLocationUpdates(callback)
            }
        } else {
            onDispose { }
        }
    }

    // 🔹 Firebase Bus Listener
    val busesRef = database.getReference("buses")

    DisposableEffect(Unit) {
        val busListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                isLoading = false
                val busList = mutableListOf<Bus>()
                snapshot.children.forEach { busSnapshot ->
                    val plate = busSnapshot.key ?: return@forEach
                    val routeName =
                        busSnapshot.child("routeName").getValue(String::class.java) ?: ""
                    val busNumber =
                        busSnapshot.child("busNumber").value.asLong() ?: 0L
                    val isLive =
                        busSnapshot.child("isLive").getValue(Boolean::class.java) ?: false
                    val latitude =
                        busSnapshot.child("latitude").value.asDouble()
                    val longitude =
                        busSnapshot.child("longitude").value.asDouble()
                    val speed =
                        busSnapshot.child("speed").value.asDouble() ?: 0.0

                    busList.add(
                        Bus(
                            plate,
                            routeName,
                            busNumber,
                            isLive,
                            latitude,
                            longitude,
                            speed
                        )
                    )
                }
                buses = busList

                selectedBus?.let { current ->
                    selectedBus = buses.find { it.plate == current.plate }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                isLoading = false
                errorMessage = error.message
            }
        }
        busesRef.addValueEventListener(busListener)
        onDispose { busesRef.removeEventListener(busListener) }
    }

    val cameraPositionState = rememberCameraPositionState()
    val animatedLat = remember { Animatable(0.0f) }
    val animatedLng = remember { Animatable(0.0f) }

    // 🔹 Animate bus
    LaunchedEffect(selectedBus) {
        selectedBus?.let { bus ->
            if (bus.isLive && bus.latitude != null && bus.longitude != null) {
                val newLat = bus.latitude.toFloat()
                val newLng = bus.longitude.toFloat()

                if (animatedLat.value == 0f) {
                    animatedLat.snapTo(newLat)
                    animatedLng.snapTo(newLng)
                } else {
                    animatedLat.animateTo(newLat, tween(1000))
                    animatedLng.animateTo(newLng, tween(1000))
                }

                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(bus.latitude, bus.longitude),
                        16f
                    )
                )
            }
        }
    }

    // 🔹 FETCH FREE OSRM ROUTE
    LaunchedEffect(selectedBus, studentLocation) {
        if (
            selectedBus != null &&
            selectedBus!!.isLive &&
            selectedBus!!.latitude != null &&
            selectedBus!!.longitude != null &&
            studentLocation != null
        ) {
            // Only fetch if route is empty
            if (roadRoute.isEmpty()) {
                val result = fetchRouteOSRM(
                    context,
                    studentLocation!!,
                    LatLng(selectedBus!!.latitude!!, selectedBus!!.longitude!!)
                )

                if (result != null) {
                    roadRoute = result.points
                    liveDistance = result.distanceKm
                    etaMinutes = result.durationMin
                }
            }
        }
    }

    // 🔹 RESET VALUES WHEN SWITCHING BUSES
    LaunchedEffect(selectedBus?.plate) {
        if (selectedBus == null || !selectedBus!!.isLive) {
            liveDistance = 0f
            etaMinutes = 0
            roadRoute = emptyList()
        } else {
            roadRoute = emptyList()
        }
    }

    // ===================== UI ======================

    Column(Modifier.fillMaxSize()) {

        // 🔹 Distance + ETA Card
        if (selectedBus != null && selectedBus!!.isLive && studentLocation != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Distance")
                        Text("${String.format("%.2f", liveDistance)} km")
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("ETA")
                        Text(if (etaMinutes > 0) "$etaMinutes min" else "< 1 min")
                    }
                }
            }
        }

        // 🔹 Map
        Box(
            modifier = if (isMapFullScreen) Modifier.fillMaxSize() else Modifier.weight(1f)
        ) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(
                    isMyLocationEnabled = hasLocationPermission
                )
            ) {
                // Draw REAL ROAD POLYLINE (from OSRM)
                if (roadRoute.isNotEmpty()) {
                    Polyline(
                        points = roadRoute,
                        color = Color.Blue,
                        width = 8f
                    )
                }
                // Fallback: Dashed Straight Line (while loading)
                else if (selectedBus != null && selectedBus!!.isLive && studentLocation != null) {
                    val busLoc = LatLng(animatedLat.value.toDouble(), animatedLng.value.toDouble())
                    Polyline(
                        points = listOf(studentLocation!!, busLoc),
                        color = Color.Blue,
                        width = 5f,
                        pattern = listOf(Dash(20f), Gap(20f))
                    )
                }

                // Bus marker
                selectedBus?.let { bus ->
                    if (bus.isLive && bus.latitude != null && bus.longitude != null) {
                        Marker(
                            state = MarkerState(
                                position = LatLng(
                                    animatedLat.value.toDouble(),
                                    animatedLng.value.toDouble()
                                )
                            ),
                            title = bus.routeName
                        )
                    }
                }
            }

            // --- FULLSCREEN BUTTON ---
            IconButton(
                onClick = { isMapFullScreen = !isMapFullScreen },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = if (isMapFullScreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                    contentDescription = "Fullscreen",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        // 🔹 Bus List (Hidden if Map is Fullscreen)
        if (!isMapFullScreen) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isLoading -> CircularProgressIndicator()
                    errorMessage != null -> Text("Error: $errorMessage")
                    buses.isEmpty() -> Text("No buses available.")
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp)
                        ) {
                            items(buses, key = { it.plate }) { bus ->
                                BusItem(bus = bus) {
                                    selectedBus = bus
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// 🔹 OSRM HELPER FUNCTIONS (No Google API Key needed)

suspend fun fetchRouteOSRM(
    context: Context,
    origin: LatLng,
    dest: LatLng
): RouteResult? {
    return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {

        // OSRM Public API URL (Note: OSRM uses Longitude,Latitude order)
        val urlString =
            "http://router.project-osrm.org/route/v1/driving/" +
                    "${origin.longitude},${origin.latitude};${dest.longitude},${dest.latitude}" +
                    "?overview=full&geometries=polyline"

        try {
            val url = java.net.URL(urlString)
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"
            connection.connect()

            val response = connection.inputStream.bufferedReader().use { it.readText() }

            val jsonObject = org.json.JSONObject(response)
            val code = jsonObject.optString("code")

            if (code != "Ok") {
                Log.e("OSRM_API", "Error: $code")
                return@withContext null
            }

            val routes = jsonObject.getJSONArray("routes")

            if (routes.length() > 0) {
                val route = routes.getJSONObject(0)

                // Get Geometry
                val geometry = route.getString("geometry")
                val decodedPoints = decodePoly(geometry)

                // Get Distance (meters) and Duration (seconds)
                val distanceMeters = route.getDouble("distance")
                val durationSeconds = route.getDouble("duration")

                return@withContext RouteResult(
                    points = decodedPoints,
                    distanceKm = (distanceMeters / 1000).toFloat(),
                    durationMin = (durationSeconds / 60).toInt()
                )
            }
            null

        } catch (e: Exception) {
            Log.e("OSRM_API", "Exception: ${e.message}")
            null
        }
    }
}

fun decodePoly(encoded: String): List<LatLng> {
    val poly = ArrayList<LatLng>()
    var index = 0
    val len = encoded.length
    var lat = 0
    var lng = 0

    while (index < len) {
        var b: Int
        var shift = 0
        var result = 0
        do {
            b = encoded[index++].code - 63
            result = result or ((b and 0x1f) shl shift)
            shift += 5
        } while (b >= 0x20)
        val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
        lat += dlat

        shift = 0
        result = 0
        do {
            b = encoded[index++].code - 63
            result = result or ((b and 0x1f) shl shift)
            shift += 5
        } while (b >= 0x20)
        val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
        lng += dlng

        val p = LatLng(lat.toDouble() / 1E5, lng.toDouble() / 1E5)
        poly.add(p)
    }
    return poly
}