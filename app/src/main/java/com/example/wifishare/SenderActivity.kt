package com.example.wifishare

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class SenderActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var infoText: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var backButton: Button

    private var senderService: SenderService? = null
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as SenderService.LocalBinder
            senderService = binder.getService()
            isBound = true
            updateStatus()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
            senderService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sender)

        statusText = findViewById(R.id.statusText)
        infoText = findViewById(R.id.infoText)
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)
        backButton = findViewById(R.id.backButton)

        requestPermissions()

        startButton.setOnClickListener {
            startSenderService()
        }

        stopButton.setOnClickListener {
            stopSenderService()
        }

        backButton.setOnClickListener {
            finish()
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf<String>()

        permissions.add(Manifest.permission.ACCESS_WIFI_STATE)
        permissions.add(Manifest.permission.CHANGE_WIFI_STATE)
        permissions.add(Manifest.permission.INTERNET)
        permissions.add(Manifest.permission.ACCESS_NETWORK_STATE)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        val neededPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (neededPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, neededPermissions, 100)
        }
    }

    private fun startSenderService() {
        val intent = Intent(this, SenderService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
        statusText.text = "Status: STARTING..."
        statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
        Toast.makeText(this, "Starting sender service...", Toast.LENGTH_SHORT).show()
    }

    private fun stopSenderService() {
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
        val intent = Intent(this, SenderService::class.java)
        stopService(intent)
        senderService = null
        statusText.text = "Status: STOPPED"
        statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
        infoText.text = "SSID: -\nPassword: -\nProxy: -\nConnected Clients: 0"
        Toast.makeText(this, "Service stopped", Toast.LENGTH_SHORT).show()
    }

    private fun updateStatus() {
        senderService?.let { service ->
            statusText.text = "Status: RUNNING"
            statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            val ssid = service.getSSID() ?: "N/A"
            val password = service.getPassword() ?: "N/A"
            val proxyInfo = service.getProxyInfo() ?: "Not Started"
            val clients = service.getConnectedClients()
            infoText.text = "SSID: $ssid\nPassword: $password\nProxy: $proxyInfo\nConnected Clients: $clients"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }
}