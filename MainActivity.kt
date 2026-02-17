package com.example.collegebustracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.*
import com.example.collegebustracker.ui.theme.CollegeBusTrackerTheme
import com.google.firebase.FirebaseApp
import com.google.firebase.database.FirebaseDatabase

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 🔥 Initialize Firebase
        FirebaseApp.initializeApp(this)

        setContent {
            CollegeBusTrackerTheme {

                val navController = rememberNavController()

                NavHost(
                    navController = navController,
                    startDestination = "mode"
                ) {

                    composable("mode") {
                        ModeSelectionScreen(navController)
                    }

                    composable("driverLogin") {
                        DriverLoginScreen(navController)
                    }

                    composable("driverDashboard/{plate}") { backStackEntry ->
                        val plate =
                            backStackEntry.arguments?.getString("plate") ?: ""
                        DriverDashboardScreen(plate)
                    }
                }
            }
        }
    }
}

///////////////////////////////////////////////////////////

@Composable
fun ModeSelectionScreen(navController: NavController) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            "College Bus Tracker",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(40.dp))

        Button(
            onClick = { navController.navigate("driverLogin") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Driver Mode")
        }
    }
}

///////////////////////////////////////////////////////////

@Composable
fun DriverLoginScreen(navController: NavController) {

    val busesRef =
        FirebaseDatabase.getInstance()
            .getReference("buses")

    var plate by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.Center
    ) {

        Text(
            "Driver Login",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = plate,
            onValueChange = { plate = it.uppercase().trim() },
            label = { Text("Number Plate") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it.trim() },
            label = { Text("Password (Last 4 digits)") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {

                if (plate.isBlank() || password.isBlank()) {
                    error = "Fill all fields"
                    return@Button
                }

                loading = true
                error = ""

                busesRef.child(plate)
                    .get()
                    .addOnSuccessListener { snapshot ->

                        loading = false

                        if (!snapshot.exists()) {
                            error = "Bus not found"
                            return@addOnSuccessListener
                        }

                        val storedPassword =
                            snapshot.child("password")
                                .getValue(String::class.java)

                        if (storedPassword == password) {
                            navController.navigate("driverDashboard/$plate")
                        } else {
                            error = "Invalid password"
                        }
                    }
                    .addOnFailureListener {
                        loading = false
                        error = it.message ?: "Database error"
                    }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Login")
        }

        if (loading) {
            Spacer(modifier = Modifier.height(10.dp))
            CircularProgressIndicator()
        }

        if (error.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

///////////////////////////////////////////////////////////

@Composable
fun DriverDashboardScreen(plate: String) {

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Welcome Driver\nBus: $plate",
            style = MaterialTheme.typography.headlineMedium
        )
    }
}
