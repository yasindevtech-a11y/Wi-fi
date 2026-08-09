package com.userapp.ftpmanager

import android.content.Context

data class SyncConfig(
    val host: String,
    val port: Int,
    val user: String,
    val pass: String,
    val remotePath: String,
    val localFolderUri: String?
)

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("ftp_manager_prefs", Context.MODE_PRIVATE)

    fun save(host: String, port: Int, user: String, pass: String, remotePath: String, localFolderUri: String?) {
        sp.edit()
            .putString("host", host)
            .putInt("port", port)
            .putString("user", user)
            .putString("pass", pass)
            .putString("remotePath", remotePath)
            .putString("localFolderUri", localFolderUri)
            .apply()
    }

    fun load(): SyncConfig? {
        val host = sp.getString("host", null) ?: return null
        val port = sp.getInt("port", 21)
        val user = sp.getString("user", "") ?: ""
        val pass = sp.getString("pass", "") ?: ""
        val remotePath = sp.getString("remotePath", "/") ?: "/"
        val localFolderUri = sp.getString("localFolderUri", null)
        return SyncConfig(host, port, user, pass, remotePath, localFolderUri)
    }

    fun setAutoSync(enabled: Boolean) {
        sp.edit().putBoolean("autoSync", enabled).apply()
    }

    fun isAutoSync(): Boolean = sp.getBoolean("autoSync", false)
}
