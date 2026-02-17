package com.example.collegebustracker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.navigation.NavController
import androidx.navigation.compose.*
import com.example.collegebustracker.ui.theme.CollegeBusTrackerTheme
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.FirebaseApp
import com.google.firebase.database.*
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.google.maps.android.compose.*
import java.util.UUID

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

                    composable("mode") {
                        ModeSelectionScreen(navController)
                    }

                    composable("driverLogin") {
                        DriverLoginScreen(navController)
                    }

                    composable("driverInfo/{plate}") {
                        val plate = it.arguments?.getString("plate") ?: ""
                        DriverInfoScreen(navController, plate)
                    }

                    composable("driverMap/{plate}") {
                        val plate = it.arguments?.getString("plate") ?: ""
                        DriverMapScreen(navController, plate)
                    }

                    composable("student") {
                        StudentScreen()
                    }
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

    val database = Firebase.database("https://collegebustracker-85e6a-default-rtdb.asia-southeast1.firebasedatabase.app")
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

                            val storedPassword =
                                snapshot.child("password").getValue(String::class.java)

                            val isLive =
                                snapshot.child("isLive").getValue(Boolean::class.java) ?: false

                            val activeDevice =
                                snapshot.child("activeDeviceId").getValue(String::class.java) ?: ""

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

    val database = Firebase.database("https://collegebustracker-85e6a-default-rtdb.asia-southeast1.firebasedatabase.app")
    val busRef = database.getReference("buses").child(plate)

    val context = LocalContext.current
    val deviceId = getOrCreateDeviceId(context)

    var route by remember { mutableStateOf("") }
    var busNumber by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        busRef.get().addOnSuccessListener {
            route = it.child("routeName").getValue(String::class.java) ?: ""
            busNumber = it.child("busNumber").getValue(Int::class.java)?.toString() ?: ""
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

                                val isLive =
                                    currentData.child("isLive").getValue(Boolean::class.java) ?: false

                                val activeDevice =
                                    currentData.child("activeDeviceId").getValue(String::class.java) ?: ""

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
    val deviceId = getOrCreateDeviceId(context)

    val database = Firebase.database(
        "https://collegebustracker-85e6a-default-rtdb.asia-southeast1.firebasedatabase.app"
    )

    val busRef = database.getReference("buses").child(plate)

    val fusedLocationClient =
        LocationServices.getFusedLocationProviderClient(context)

    var hasPermission by remember { mutableStateOf(false) }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            LatLng(28.6139, 77.2090), 10f
        )
    }

    // ✅ STORE CALLBACK
    var locationCallback by remember { mutableStateOf<LocationCallback?>(null) }

    DisposableEffect(hasPermission) {

        if (hasPermission) {

            val request = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                4000L
            ).build()

            val callback = object : LocationCallback() {

                override fun onLocationResult(result: LocationResult) {

                    val location = result.lastLocation ?: return

                    // 🔐 SAFETY CHECK — DO NOT UPDATE IF OFFLINE
                    busRef.child("isLive").get().addOnSuccessListener { snapshot ->

                        val live =
                            snapshot.getValue(Boolean::class.java) ?: false

                        if (!live) return@addOnSuccessListener

                        val latLng = LatLng(
                            location.latitude,
                            location.longitude
                        )

                        cameraPositionState.position =
                            CameraPosition.fromLatLngZoom(latLng, 16f)

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
            }

            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {

                fusedLocationClient.requestLocationUpdates(
                    request,
                    callback,
                    null
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
            properties = MapProperties(
                isMyLocationEnabled = hasPermission
            )
        )

        Button(
            onClick = {

                // 1️⃣ STOP UPDATES FIRST
                locationCallback?.let {
                    fusedLocationClient.removeLocationUpdates(it)
                }

                // 2️⃣ SET OFFLINE IN FIREBASE
                busRef.updateChildren(
                    mapOf(
                        "isLive" to false,
                        "activeDeviceId" to "",
                        "timestamp" to System.currentTimeMillis()
                    )
                ).addOnCompleteListener {

                    // 3️⃣ NAVIGATE AFTER SUCCESS
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
// STUDENT PANEL
///////////////////////////////////////////////////////////

@Composable
fun StudentScreen() {

    val database = Firebase.database("https://collegebustracker-85e6a-default-rtdb.asia-southeast1.firebasedatabase.app")
    val busesRef = database.getReference("buses")

    var busLocation by remember { mutableStateOf<LatLng?>(null) }

    LaunchedEffect(Unit) {
        busesRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {

                snapshot.children.forEach {

                    val isLive =
                        it.child("isLive").getValue(Boolean::class.java) ?: false

                    if (isLive) {
                        val lat =
                            it.child("latitude").getValue(Double::class.java)
                        val lng =
                            it.child("longitude").getValue(Double::class.java)

                        if (lat != null && lng != null)
                            busLocation = LatLng(lat, lng)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    val cameraPositionState = rememberCameraPositionState()

    busLocation?.let {
        cameraPositionState.position =
            CameraPosition.fromLatLngZoom(it, 16f)
    }

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState
    ) {
        busLocation?.let {
            Marker(
                state = MarkerState(position = it),
                title = "Live Bus"
            )
        }
    }
}
