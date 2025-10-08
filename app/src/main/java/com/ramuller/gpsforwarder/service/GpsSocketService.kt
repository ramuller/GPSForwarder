package com.ramuller.gpsforwarder.service

import com.ramuller.gpsforwarder.R

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.app.Service.START_STICKY
import android.content.Context
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat.startForeground
import androidx.core.content.ContextCompat.getSystemService
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.SupervisorJob
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket

class GpsSocketService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var serverSocket: ServerSocket

    override fun onBind(intent: Intent?): IBinder? {
        // Not a bound service, so just return null
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, createNotification())

        val serverPort = intent?.getIntExtra("port", 5000) ?: 5000
        sendLogToActivity("Listen to port: $serverPort")
        serviceScope.launch {
            try {
                serverSocket = ServerSocket(serverPort)
                while (true) {
                    val client = serverSocket.accept()
                    val intent = Intent("GpsSocketLog")
                    // intent.putExtra("logMessage", "Client connected: ${client.inetAddress.hostAddress}")
                    val clientIp = client.inetAddress.hostAddress
                    sendLogToActivity("Client connected: $clientIp")
                    launch { handleClient(client) }
                }
            } catch (e: Exception) {
                Log.e("GpsSocketService", "Error in server socket", e)
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("GpsSocketService", "Service destroyed")
    }
    private fun sendLogToActivity(message: String) {
        val intent = Intent("GpsSocketLog")
        intent.putExtra("logMessage", message)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }
    private fun createNotification(): Notification {
        val channelId = "gps_socket_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "GPS Socket Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("GPS Forwarder Running")
            .setContentText("Listening for socket connections")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // 🔁 Change to your icon
            .build()
    }

    private suspend fun handleClient(socket: Socket) {
        val writer = PrintWriter(socket.getOutputStream(), true)
        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

        try {
            while (true) {
                writer.println("Send something to get GPS coordinates")
                val line = reader.readLine()
                if (line == null) {
                    sendLogToActivity("Client disconnected: ${socket.inetAddress.hostAddress}")
                    break // 🔁 Exit the loop
                }
                Log.d("GpsSocketService", "Received: $line")

                CoroutineScope(Dispatchers.IO).launch {
                    val location = requestCurrentLocation(this@GpsSocketService)
                    Log.e("GpsSocketService", "sendLogToActivity")
                    if (location != null) {
                        writer.println("GPS: ${location.latitude}, ${location.longitude}")
                    }
                }
            }
        } catch (e: Exception) {
                Log.e("GpsSocketService", "Client error", e)
        } finally {
            try {
                socket.close()
                Log.e("GpsSocketService", "Socket closed")
            } catch (e: Exception) {
                Log.e("GpsSocketService", "Error closing socket: ${e.message}")
            }
        }
    }

    suspend fun requestCurrentLocation(context: Context): Location? {
        return suspendCancellableCoroutine { cont ->
            val fusedClient = LocationServices.getFusedLocationProviderClient(this)

            fusedClient.lastLocation
                .addOnSuccessListener { location ->
                    cont.resume(location, onCancellation = null)
                }
                .addOnFailureListener {
                    cont.resume(null, onCancellation = null)
                }
        }
    }
}