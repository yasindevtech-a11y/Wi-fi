package com.senin.vaultsync

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.senin.vaultsync.data.AppDatabase

class VaultSyncApp : Application() {

    // Uygulama boyunca tek bir veritabanı örneği kullanılır
    lateinit var database: AppDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        CrashHandler.install(this)
        try {
            database = AppDatabase.getInstance(this)
            createNotificationChannel()
        } catch (e: Exception) {
            // İlk açılışta bir sorun olsa bile uygulamanın tamamen kapanmasını
            // değil, MainActivity'nin hata ekranını göstermesini istiyoruz.
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Senkron Bildirimleri",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "vault_sync_channel"
    }
}
