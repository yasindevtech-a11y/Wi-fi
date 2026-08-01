package com.senin.vaultsync.sync

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.senin.vaultsync.VaultSyncApp
import com.senin.vaultsync.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

class SyncForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification("Senkron başlıyor..."))

        scope.launch {
            val app = application as VaultSyncApp
            val settings = SettingsStore(applicationContext).config.first()
            val vaultDir = File(getExternalFilesDir(null), "vault")
            val engine = SyncEngine(vaultDir, app.database.syncManifestDao())

            engine.run(settings) { progress ->
                when (progress) {
                    is SyncEngine.Progress.FileDone ->
                        updateNotification("${progress.action}: ${progress.relativePath}")
                    is SyncEngine.Progress.Info -> updateNotification(progress.message)
                    is SyncEngine.Progress.Error -> updateNotification("Hata: ${progress.message}")
                    SyncEngine.Progress.Finished -> updateNotification("Senkron tamamlandı")
                }
            }
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, VaultSyncApp.CHANNEL_ID)
            .setContentTitle("VaultSync")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setOngoing(true)
            .build()

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val NOTIFICATION_ID = 42
    }
}

private typealias NotificationManager = android.app.NotificationManager
