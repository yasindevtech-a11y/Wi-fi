package com.senin.vaultsync.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "vault_settings")

/**
 * Modeme/diske nasıl bağlanılacağını ve hangi WiFi'nin "ev" sayılacağını tutar.
 * Örnek değerler (etiketinden):
 *   host = "192.168.1.1"
 *   share = "usb1_1" (Samba panelinde verdiğin paylaşım adı)
 *   homeSsid = "SUPERONLINE_Wi-Fi_4591"
 */
class SettingsStore(private val context: Context) {

    private object Keys {
        val HOST = stringPreferencesKey("ftp_host")
        val USERNAME = stringPreferencesKey("ftp_username")
        val PASSWORD = stringPreferencesKey("ftp_password")
        val HOME_SSID = stringPreferencesKey("home_ssid")
    }

    data class Config(
        val host: String = "192.168.1.1",
        val username: String = "",
        val password: String = "",
        val homeSsid: String = ""
    )

    val config: Flow<Config> = context.dataStore.data.map { prefs ->
        Config(
            host = prefs[Keys.HOST] ?: "192.168.1.1",
            username = prefs[Keys.USERNAME] ?: "",
            password = prefs[Keys.PASSWORD] ?: "",
            homeSsid = prefs[Keys.HOME_SSID] ?: ""
        )
    }

    suspend fun save(config: Config) {
        context.dataStore.edit { prefs ->
            prefs[Keys.HOST] = config.host
            prefs[Keys.USERNAME] = config.username
            prefs[Keys.PASSWORD] = config.password
            prefs[Keys.HOME_SSID] = config.homeSsid
        }
    }
}
