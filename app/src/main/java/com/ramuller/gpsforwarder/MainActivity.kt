package com.ramuller.gpsforwarder
import com.ramuller.gpsforwarder.service.GpsSocketService

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import android.Manifest
import android.content.Intent
import androidx.core.app.ActivityCompat
import com.ramuller.gpsforwarder.ui.theme.GPSForwarderTheme
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter

import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import kotlin.coroutines.resume

class MainActivity : ComponentActivity() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val TAG = "GPSForwarder"

    private val SERVER_IP = "192.168.231.125"  // Replace with your target IP
    private val SERVER_PORT = 5050          // Replace with your target port
    private lateinit var logView: TextView
    private lateinit var logScrollView: ScrollView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Link the activity to the layout you just created
        setContentView(R.layout.activity_main)
        // Link the views
        logView = findViewById(R.id.logView)
        logScrollView = findViewById(R.id.logScrollView)
        log("Started\n")
        // Request location permission if not granted
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                1
            )
            return
        }

        enableEdgeToEdge()

        // val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        // Start sending loop
        // fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        // startSendingLoop()
        // createSocket()
        val ip = getDeviceIpAddress()
        log("Local IP: $ip")

        // Start the socket service
        val intent = Intent(this, GpsSocketService::class.java)
        Log.e("GpsSocketService","ContextCompat.startForegroundService")
        intent.putExtra("port", 2768)
        ContextCompat.startForegroundService(this, intent)


        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)

        // 🔁 Logging function        ContextCompat.startForegroundService(this, intent)
//        CoroutineScope(Dispatchers.IO).launch {
//            val ip = getDeviceIpAddress()
//            log("Local IP: $ip")
//            val PORT = 5050
//            val serverSocket = ServerSocket(PORT)
//            log("Listening on port $PORT")
//
//            while (true) {
//                val client = serverSocket.accept()
//                log("Client connected: ${client.inetAddress.hostAddress}")
//
//                launch {
//                    handleClient(client)
//                }
//            }
//        }
    }
    fun log(message: String) {
        Log.d("GPSForwarder", message)
        runOnUiThread {
            logView.append("$message\n")

            // Auto-scroll to bottom of ScrollView
            logScrollView.post {
                logScrollView.fullScroll(View.FOCUS_DOWN)
            }
        }
    }

    fun handleClient(socket: Socket) {
        CoroutineScope(Dispatchers.IO).launch {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val writer = PrintWriter(socket.getOutputStream(), true)

            val line = "Wait"
            try {
                while (line != null) {
                    writer.println("Send something to get GPS coordinates")

                    val line = reader.readLine()
                    log("Received: $line")
                    val location = requestCurrentLocation()
                    if (location != null) {
                        log("Received: $line")
                        writer.println("GPS : ${location.latitude},${location.longitude}")

                    }

                }
            }catch (e: Exception) {
                e.printStackTrace()
            } finally {
                socket.close()
            }
        }
    }

    fun createSocket() {
        // super.onCreate()
        Log.d(TAG, "RALF Service created")
        Log.d(TAG, "RALF not yet")
        val PORT = 5050

        Log.d(TAG, "RALF: Start socket ")
        try {
            // Listen on all interfaces: 0.0.0.0
            val serverSocket = ServerSocket(PORT)

            // Log.d(TAG, "Server listening on 0.0.0.0:$PORT")
            log("Server listening on 0.0.0.0:$PORT")

            while (true) {
                log("In loop")
                val clientSocket: Socket = serverSocket.accept()
                log("Client connected: ${clientSocket.inetAddress.hostAddress}")

                // Handle each client in a new coroutine
                CoroutineScope(Dispatchers.IO).launch {
                    handleClient(clientSocket)
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "In catch: ${Log.getStackTraceString(e)}")
        }
    }

    fun getDeviceIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (iface in interfaces) {
                val addresses = iface.inetAddresses
                for (address in addresses) {
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        return address.hostAddress ?: "Unavailable"
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "Unavailable"
    }

    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val message = intent?.getStringExtra("logMessage") ?: return
            log(message) // Your existing log() function
        }
    }
    override fun onStart() {
        super.onStart()
        LocalBroadcastManager.getInstance(this)
        .registerReceiver(logReceiver, IntentFilter("GpsSocketLog"))
}
    private fun startSendingLoop() {
        log("Start sending loop")
        CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                try {
                    val location = requestCurrentLocation()
                    if (location != null) {
                        val socket = Socket(SERVER_IP, SERVER_PORT)
                        val writer = PrintWriter(socket.getOutputStream(), true)

                        // val message = "${location.latitude},${location.longitude}"
                        log("message ${location.latitude},${location.longitude}")

                        socket.close()
                    } else {
                        log("Location is null from Listener")
                    }

                } catch (e: Exception) {
                    log("Exception: ${e.message}")
                }
                // Wait 5 seconds before next send
                kotlinx.coroutines.delay(500)
            }
        }
    }

    suspend fun requestCurrentLocation(): Location? = suspendCancellableCoroutine { cont ->
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
            .setMaxUpdates(1)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                fusedLocationClient.removeLocationUpdates(this)
                cont.resume(result.lastLocation)
            }

            override fun onLocationAvailability(availability: LocationAvailability) {
                if (!availability.isLocationAvailable) {
                    fusedLocationClient.removeLocationUpdates(this)
                    cont.resume(null)
                }
            }
        }

        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }

        fusedLocationClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
    }
}
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    GPSForwarderTheme {
        Greeting("Android")
    }
}