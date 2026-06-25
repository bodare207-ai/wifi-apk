package com.example.wifishare

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class ReceiverActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var infoText: TextView
    private lateinit var connectButton: Button
    private lateinit var disconnectButton: Button
    private lateinit var backButton: Button
    private lateinit var proxyIpInput: EditText
    private lateinit var proxyPortInput: EditText

    private var receiverService: ReceiverService? = null
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as ReceiverService.LocalBinder
            receiverService = binder.getService()
            isBound = true
            updateStatus()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
            receiverService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_receiver)

        statusText = findViewById(R.id.statusText)
        infoText = findViewById(R.id.infoText)
        connectButton = findViewById(R.id.connectButton)
        disconnectButton = findViewById(R.id.disconnectButton)
        backButton = findViewById(R.id.backButton)
        proxyIpInput = findViewById(R.id.proxyIpInput)
        proxyPortInput = findViewById(R.id.proxyPortInput)

        proxyPortInput.setText("8080")

        connectButton.setOnClickListener {
            startReceiverService()
        }

        disconnectButton.setOnClickListener {
            stopReceiverService()
        }

        backButton.setOnClickListener {
            finish()
        }
    }

    private fun startReceiverService() {
        val ip = proxyIpInput.text.toString().trim()
        val port = proxyPortInput.text.toString().trim()

        if (ip.isEmpty()) {
            Toast.makeText(this, "Please enter sender IP address", Toast.LENGTH_SHORT).show()
            return
        }

        if (port.isEmpty()) {
            Toast.makeText(this, "Please enter proxy port", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(this, ReceiverService::class.java)
        intent.putExtra("PROXY_IP", ip)
        intent.putExtra("PROXY_PORT", port.toIntOrNull() ?: 8080)
        startService(intent)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
        statusText.text = "Status: CONNECTING..."
        statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
        Toast.makeText(this, "Connecting to sender...", Toast.LENGTH_SHORT).show()
    }

    private fun stopReceiverService() {
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
        val intent = Intent(this, ReceiverService::class.java)
        stopService(intent)
        receiverService = null
        statusText.text = "Status: DISCONNECTED"
        statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
        infoText.text = "Connected to: -\nProxy: -"
        Toast.makeText(this, "Disconnected", Toast.LENGTH_SHORT).show()
    }

    private fun updateStatus() {
        receiverService?.let { service ->
            statusText.text = "Status: CONNECTED"
            statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            val connectedTo = service.getConnectedTo() ?: "N/A"
            val proxyInfo = service.getProxyInfo() ?: "N/A"
            infoText.text = "Connected to: $connectedTo\nProxy: $proxyInfo"
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