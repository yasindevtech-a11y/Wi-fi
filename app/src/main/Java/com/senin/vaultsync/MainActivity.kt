package com.senin.vaultsync

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.senin.vaultsync.data.SettingsStore
import com.senin.vaultsync.sync.SyncForegroundService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Not: Bu ekran KASITLI olarak Jetpack Compose kullanmıyor, sadece klasik
 * Android View'lar (LinearLayout, EditText, Button, TextView) ile
 * programatik olarak kuruluyor. Bunun sebebi: Compose ile art arda anında
 * çökme yaşandığı için, sorunun Compose'dan mı kaynaklandığını test etmek
 * ve daha az bağımlılıkla, daha kararlı bir sürüm sunmak.
 */
class MainActivity : ComponentActivity() {

    private lateinit var settingsStore: SettingsStore
    private lateinit var vaultDir: File
    private lateinit var filesListText: TextView

    private lateinit var hostInput: EditText
    private lateinit var usernameInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var ssidInput: EditText

    private val pickFileLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri?.let { copyIntoVault(it) }
            refreshFileList()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashHandler.install(this)

        // Önceki açılışta kaydedilmiş bir çökme var mı? Varsa önce onu göster.
        val previousCrash = CrashHandler.readLastCrash(this)
        if (previousCrash != null) {
            showPlainCrashScreen(previousCrash)
            return
        }

        try {
            settingsStore = SettingsStore(applicationContext)
            vaultDir = File(getExternalFilesDir(null) ?: filesDir, "vault")
            buildMainUi()
        } catch (t: Throwable) {
            val sw = StringWriter()
            t.printStackTrace(PrintWriter(sw))
            showPlainCrashScreen(sw.toString())
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun buildMainUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(32), dp(20), dp(20))
        }

        fun label(text: String) = TextView(this).apply {
            this.text = text
            textSize = 18f
            setPadding(0, dp(12), 0, dp(4))
        }

        fun spacer(height: Int = 8) = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(height))
        }

        root.addView(TextView(this).apply {
            text = "VaultSync Ayarları"
            textSize = 22f
            setPadding(0, 0, 0, dp(8))
        })

        hostInput = EditText(this).apply { hint = "Modem IP (ör. 192.168.1.1)" }
        usernameInput = EditText(this).apply { hint = "FTP kullanıcı adı" }
        passwordInput = EditText(this).apply {
            hint = "FTP parolası"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        ssidInput = EditText(this).apply { hint = "Ev WiFi adı (SSID)" }

        root.addView(hostInput)
        root.addView(usernameInput)
        root.addView(passwordInput)
        root.addView(ssidInput)

        val saveButton = Button(this).apply {
            text = "Ayarları Kaydet"
            setOnClickListener { saveSettings() }
        }
        root.addView(spacer(12))
        root.addView(saveButton)

        val syncButton = Button(this).apply {
            text = "Şimdi Senkronize Et"
            setOnClickListener {
                startForegroundService(Intent(this@MainActivity, SyncForegroundService::class.java))
                Toast.makeText(this@MainActivity, "Senkron başlatıldı", Toast.LENGTH_SHORT).show()
            }
        }
        root.addView(spacer(8))
        root.addView(syncButton)

        root.addView(label("Kasaya Dosya Ekle"))
        val pickButton = Button(this).apply {
            text = "Dosya Seç"
            setOnClickListener { pickFileLauncher.launch(arrayOf("*/*")) }
        }
        root.addView(pickButton)

        root.addView(label("Kasadaki Dosyalar"))
        filesListText = TextView(this).apply { textSize = 14f }
        root.addView(filesListText)

        val scrollView = ScrollView(this).apply { addView(root) }
        setContentView(scrollView)

        loadSettingsIntoFields()
        refreshFileList()
    }

    private fun loadSettingsIntoFields() {
        lifecycleScope.launch {
            val config = settingsStore.config.first()
            hostInput.setText(config.host)
            usernameInput.setText(config.username)
            passwordInput.setText(config.password)
            ssidInput.setText(config.homeSsid)
        }
    }

    private fun saveSettings() {
        lifecycleScope.launch {
            settingsStore.save(
                SettingsStore.Config(
                    host = hostInput.text.toString(),
                    username = usernameInput.text.toString(),
                    password = passwordInput.text.toString(),
                    homeSsid = ssidInput.text.toString()
                )
            )
            Toast.makeText(this@MainActivity, "Kaydedildi", Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshFileList() {
        val files = if (vaultDir.exists()) {
            vaultDir.walkTopDown()
                .filter { it.isFile }
                .map { it.relativeTo(vaultDir).path }
                .toList()
        } else emptyList()

        filesListText.text = if (files.isEmpty()) {
            "(henüz dosya yok)"
        } else {
            files.joinToString("\n")
        }
    }

    private fun copyIntoVault(uri: Uri) {
        val resolver = contentResolver
        val displayName = resolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
        } ?: "dosya_${System.currentTimeMillis()}"

        vaultDir.mkdirs()
        val dest = File(vaultDir, displayName)
        resolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
    }

    /** Çökme sonrası hata metnini gösterir + kopyalama butonu ekler */
    private fun showPlainCrashScreen(errorText: String) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(40), dp(20), dp(20))
        }

        val title = TextView(this).apply {
            text = "VaultSync bir önceki açılışta çöktü. Hata detayı:"
            textSize = 16f
            setPadding(0, 0, 0, dp(12))
        }

        val body = TextView(this).apply {
            text = errorText
            textSize = 12f
            setTextIsSelectable(true)
        }

        val copyButton = Button(this).apply {
            text = "Hatayı Kopyala"
            setOnClickListener {
                val clipboard = getSystemService(ClipboardManager::class.java)
                clipboard.setPrimaryClip(ClipData.newPlainText("VaultSync hata", errorText))
                Toast.makeText(this@MainActivity, "Kopyalandı", Toast.LENGTH_SHORT).show()
            }
        }

        val continueButton = Button(this).apply {
            text = "Kaydı Temizle ve Devam Et"
            setOnClickListener {
                CrashHandler.clear(this@MainActivity)
                recreate()
            }
        }

        layout.addView(title)
        layout.addView(copyButton)
        layout.addView(continueButton)
        layout.addView(body)

        val scrollView = ScrollView(this).apply {
            addView(layout)
            gravity = Gravity.TOP
        }
        setContentView(scrollView)
    }
}
