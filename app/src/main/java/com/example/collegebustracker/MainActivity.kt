package com.example.collegebustracker

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.compose.*
import com.example.collegebustracker.ui.theme.CollegeBusTrackerTheme
import com.google.firebase.database.*
import com.google.android.gms.location.*
import com.google.maps.android.compose.*
import com.google.android.gms.maps.model.*



class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            CollegeBusTrackerTheme {

                val navController = rememberNavController()

                NavHost(
                    navController = navController,
                    startDestination = "mode"
                ) {
                    composable("mode") { ModeSelectionScreen(navController) }
                    composable("driver") { DriverLoginScreen(navController) }

                    composable("student") { StudentScreen() }
                }
            }
        }
    }
}

//////////////////////////////////////////////////////////////
// MODE SELECTION
//////////////////////////////////////////////////////////////

@Composable
fun ModeSelectionScreen(navController: NavController) {

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text("College Bus Tracker",
            style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(40.dp))

        Button(
            onClick = { navController.navigate("driver") },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Driver Mode") }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = { navController.navigate("student") },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Student Mode") }
    }
}

//////////////////////////////////////////////////////////////
// DRIVER SCREEN
//////////////////////////////////////////////////////////////

@Composable
fun DriverLoginScreen(navController: NavController) {

    val database = FirebaseDatabase.getInstance()
    val registeredRef = database.getReference("registeredBuses")

    var plate by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {

        Text(
            text = "Driver Login",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(30.dp))

        OutlinedTextField(
            value = plate,
            onValueChange = { plate = it.uppercase() },
            label = { Text("Number Plate") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password (Last 4 digits)") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(20.dp))

        if (errorText.isNotEmpty()) {
            Text(errorText, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(10.dp))
        }

        Button(
            onClick = {

                if (plate.length < 4) {
                    errorText = "Invalid Plate Number"
                    return@Button
                }

                val expectedPassword = plate.takeLast(4)

                if (password != expectedPassword) {
                    errorText = "Incorrect Password"
                    return@Button
                }

                // Check if plate exists in registered buses
                registeredRef.child(plate)
                    .get()
                    .addOnSuccessListener { snapshot ->

                        if (snapshot.exists()) {
                            navController.navigate("driverLive/$plate")
                        } else {
                            errorText = "Bus Not Registered"
                        }
                    }
                    .addOnFailureListener {
                        errorText = "Database Error"
                    }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Login & Start Bus")
        }
    }
}



//////////////////////////////////////////////////////////////
// STUDENT SCREEN
//////////////////////////////////////////////////////////////

@Composable
fun StudentScreen() {

    val database = FirebaseDatabase.getInstance()
    val busRef = database.getReference("busLocation")

    var busLocation by remember { mutableStateOf<LatLng?>(null) }

    val cameraPositionState = rememberCameraPositionState()

    DisposableEffect(Unit) {

        val listener = object : ValueEventListener {

            override fun onDataChange(snapshot: DataSnapshot) {

                val lat = snapshot.child("latitude")
                    .getValue(Double::class.java)

                val lng = snapshot.child("longitude")
                    .getValue(Double::class.java)

                if (lat != null && lng != null) {

                    val latLng = LatLng(lat, lng)
                    busLocation = latLng

                    cameraPositionState.position =
                        CameraPosition.fromLatLngZoom(latLng, 17f)
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        }

        busRef.addValueEventListener(listener)

        onDispose {
            busRef.removeEventListener(listener)
        }
    }

    if (busLocation != null) {

        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState
        ) {
            Marker(
                state = MarkerState(position = busLocation!!),
                title = "Bus Location"
            )
        }

    } else {

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Waiting for bus location...")
        }
    }
}


//////////////////////////////////////////////////////////////
// LOCATION FUNCTION
//////////////////////////////////////////////////////////////

@androidx.annotation.RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
fun startLocationUpdates(
    fusedLocationClient: FusedLocationProviderClient,
    onLocationReceived: (Location) -> Unit
) {

    val request = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY,
        3000
    ).build()

    val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { onLocationReceived(it) }
        }
    }

    fusedLocationClient.requestLocationUpdates(
        request,
        callback,
        null
    )
}
