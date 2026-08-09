package com.userapp.ftpmanager

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            val prefs = Prefs(applicationContext)
            val cfg = prefs.load() ?: return@withContext Result.failure()
            val localUriStr = cfg.localFolderUri ?: return@withContext Result.failure()
            try {
                SyncManager.sync(
                    applicationContext,
                    cfg.host, cfg.port, cfg.user, cfg.pass, cfg.remotePath,
                    Uri.parse(localUriStr)
                )
                Result.success()
            } catch (e: Exception) {
                Result.retry()
            }
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "ftp_auto_sync"
    }
}
