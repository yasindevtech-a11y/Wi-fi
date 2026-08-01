package com.senin.vaultsync.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import androidx.core.content.ContextCompat
import com.senin.vaultsync.data.SettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Telefon herhangi bir WiFi'ye bağlandığında tetiklenir.
 * SSID, kullanıcının ayarlardan girdiği "ev SSID"si ile eşleşiyorsa senkronu başlatır.
 */
class WifiConnectionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val wifiManager = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return

        if (!wifiManager.isWifiEnabled) return

        val currentSsid = wifiManager.connectionInfo?.ssid?.trim('"') ?: return

        val settings = SettingsStore(context.applicationContext)
        val homeSsid = runBlocking { settings.config.first().homeSsid }

        if (homeSsid.isNotBlank() && currentSsid == homeSsid) {
            val serviceIntent = Intent(context, SyncForegroundService::class.java)
            ContextCompat.startForegroundService(context, serviceIntent)
        }
    }
}
