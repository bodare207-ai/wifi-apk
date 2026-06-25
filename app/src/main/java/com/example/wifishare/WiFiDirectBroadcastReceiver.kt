package com.example.wifishare

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.wifi.p2p.WifiP2pManager

class WiFiDirectBroadcastReceiver(
    private val manager: WifiP2pManager,
    private val channel: WifiP2pManager.Channel,
    private val service: SenderService
) : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null) return

        when (intent.action) {
            WifiP2pManager.WIFI_P2P_STATE_CHANGED -> {
                val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                    // Wi-Fi Direct is enabled
                }
            }
            WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED -> {
                // Connection state changed
            }
            WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED -> {
                // Device info changed
            }
        }
    }
}