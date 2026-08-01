package com.senin.vaultsync.data

import android.content.Context

/**
 * FTP bağlantı bilgilerini düz SharedPreferences ile saklar.
 * Bilerek DataStore/coroutine kullanmıyoruz: senkron, basit, az bağımlılıklı.
 */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("vault_settings", Context.MODE_PRIVATE)

    data class Config(
        val host: String = "192.168.1.1",
        val username: String = "",
        val password: String = "",
        val rootPath: String = ""
    )

    fun load(): Config = Config(
        host = prefs.getString("host", "192.168.1.1") ?: "192.168.1.1",
        username = prefs.getString("username", "") ?: "",
        password = prefs.getString("password", "") ?: "",
        rootPath = prefs.getString("rootPath", "") ?: ""
    )

    fun save(config: Config) {
        prefs.edit()
            .putString("host", config.host)
            .putString("username", config.username)
            .putString("password", config.password)
            .putString("rootPath", config.rootPath)
            .apply()
    }

    fun isConfigured(): Boolean = load().username.isNotBlank()
}
