package com.example.wifishare

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.p2p.WifiP2pManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import java.net.NetworkInterface
import java.util.concurrent.atomic.AtomicInteger

class SenderService : Service() {

    companion object {
        private const val TAG = "SenderService"
        private const val CHANNEL_ID = "SenderChannel"
        private const val NOTIFICATION_ID = 1001
        private const val PROXY_PORT = 8080
    }

    private val binder = LocalBinder()
    private lateinit var wifiP2pManager: WifiP2pManager
    private lateinit var channel: WifiP2pManager.Channel
    private var broadcastReceiver: WiFiDirectBroadcastReceiver? = null
    private var proxyServer: ProxyServer? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private var ssid: String? = null
    private var password: String? = null
    private var isServiceRunning = false
    private val connectedClients = AtomicInteger(0)

    inner class LocalBinder : Binder() {
        fun getService(): SenderService = this@SenderService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "SenderService created")

        wifiP2pManager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        channel = wifiP2pManager.initialize(this, mainLooper, null)

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "WiFiShare::WakeLock"
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "SenderService started")

        if (!isServiceRunning) {
            startForeground(NOTIFICATION_ID, createNotification())
            startWiFiDirectGroup()
            startProxyServer()
            isServiceRunning = true
            wakeLock?.acquire(10 * 60 * 1000L)
        }

        return START_STICKY
    }

    private fun startWiFiDirectGroup() {
        Log.d(TAG, "Creating Wi-Fi Direct group...")

        broadcastReceiver = WiFiDirectBroadcastReceiver(wifiP2pManager, channel, this)
        val intentFilter = android.content.IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED)
        }
        registerReceiver(broadcastReceiver, intentFilter)

        wifiP2pManager.createGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Wi-Fi Direct group created successfully")
                getGroupInfo()
            }

            override fun onFailure(reason: Int) {
                Log.e(TAG, "Failed to create Wi-Fi Direct group: $reason")
                wifiP2pManager.removeGroup(channel, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        Log.d(TAG, "Removed existing group, retrying...")
                        wifiP2pManager.createGroup(channel, this@SenderService)
                    }
                    override fun onFailure(reason: Int) {
                        Log.e(TAG, "Failed to remove group: $reason")
                    }
                })
            }
        })
    }

    private fun getGroupInfo() {
        wifiP2pManager.requestGroupInfo(channel) { group ->
            if (group != null && group.isGroupOwner) {
                ssid = group.networkName
                password = group.passphrase
                Log.d(TAG, "Group Info - SSID: $ssid, Password: $password")
                updateNotification("WiFi: $ssid")
            }
        }
    }

    private fun startProxyServer() {
        Log.d(TAG, "Starting SOCKS5 proxy server on port $PROXY_PORT")

        try {
            proxyServer = ProxyServer(PROXY_PORT)
            proxyServer?.start()
            Log.d(TAG, "SOCKS5 proxy started on port $PROXY_PORT")
            updateNotification("Proxy running on port $PROXY_PORT")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start proxy server: ${e.message}")
        }
    }

    fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.name?.startsWith("p2p") == true) {
                    val addresses = networkInterface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val address = addresses.nextElement()
                        if (!address.isLoopbackAddress && address is java.net.Inet4Address) {
                            return address.hostAddress
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting local IP: ${e.message}")
        }
        return null
    }

    fun getProxyInfo(): String? {
        val ip = getLocalIpAddress()
        return if (ip != null) "$ip:$PROXY_PORT" else null
    }

    fun getSSID(): String? = ssid
    fun getPassword(): String? = password
    fun getConnectedClients(): Int = connectedClients.get()

    fun incrementClients() {
        connectedClients.incrementAndGet()
    }

    fun decrementClients() {
        connectedClients.decrementAndGet()
    }

    private fun createNotification(): Notification {
        createNotificationChannel()

        val intent = Intent(this, SenderActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("WiFi Share - Sender Active")
            .setContentText("Sharing internet via Wi-Fi Direct")
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("WiFi Share - Sender Active")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "WiFi Share Sender",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Wi-Fi Direct sharing running in background"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "SenderService destroying")

        isServiceRunning = false

        proxyServer?.stop()
        proxyServer = null

        try {
            wifiP2pManager.removeGroup(channel, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error removing group: ${e.message}")
        }

        try {
            broadcastReceiver?.let { unregisterReceiver(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver: ${e.message}")
        }

        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }

        stopForeground(true)
    }
}