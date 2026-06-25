package com.example.wifishare

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.URI

class ReceiverService : Service() {

    companion object {
        private const val TAG = "ReceiverService"
        private const val CHANNEL_ID = "ReceiverChannel"
        private const val NOTIFICATION_ID = 1002
    }

    private val binder = LocalBinder()
    private var proxyIp: String? = null
    private var proxyPort: Int = 8080
    private var isConnected = false
    private var oldProxySelector: ProxySelector? = null

    inner class LocalBinder : Binder() {
        fun getService(): ReceiverService = this@ReceiverService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "ReceiverService started")

        proxyIp = intent?.getStringExtra("PROXY_IP")
        proxyPort = intent?.getIntExtra("PROXY_PORT", 8080) ?: 8080

        if (proxyIp == null) {
            Log.e(TAG, "No proxy IP provided")
            stopSelf()
            return START_NOT_STICKY
        }

        if (!isConnected) {
            startForeground(NOTIFICATION_ID, createNotification())
            setupProxy()
            isConnected = true
        }

        return START_STICKY
    }

    private fun setupProxy() {
        Log.d(TAG, "Setting up proxy: $proxyIp:$proxyPort")

        try {
            oldProxySelector = ProxySelector.getDefault()

            val proxySelector = object : ProxySelector() {
                override fun select(uri: URI): List<Proxy> {
                    if (uri.scheme == "http" || uri.scheme == "https") {
                        return listOf(Proxy(Proxy.Type.SOCKS, InetSocketAddress(proxyIp!!, proxyPort)))
                    }
                    return listOf(Proxy.NO_PROXY)
                }

                override fun connectFailed(uri: URI, sa: InetSocketAddress, ioe: IOException) {
                    Log.e(TAG, "Proxy connection failed: ${ioe.message}")
                }
            }

            ProxySelector.setDefault(proxySelector)
            Log.d(TAG, "Proxy set successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to set proxy: ${e.message}")
        }
    }

    fun getConnectedTo(): String? = proxyIp
    fun getProxyInfo(): String? = if (proxyIp != null) "$proxyIp:$proxyPort" else null

    private fun createNotification(): Notification {
        createNotificationChannel()

        val intent = Intent(this, ReceiverActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("WiFi Share - Receiver Active")
            .setContentText("Connected to $proxyIp:$proxyPort")
            .setSmallIcon(android.R.drawable.stat_sys_wifi)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "WiFi Share Receiver",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps receiver connection running"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "ReceiverService destroying")

        oldProxySelector?.let {
            ProxySelector.setDefault(it)
        }

        isConnected = false
        stopForeground(true)
    }
}