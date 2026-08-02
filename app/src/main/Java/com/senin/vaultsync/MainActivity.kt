package com.senin.vaultsync

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
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
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.senin.vaultsync.data.FtpClient
import com.senin.vaultsync.data.MediaCompressor
import com.senin.vaultsync.data.SettingsStore
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Bulut sürücüsü mantığıyla çalışan basit dosya gezgini:
 * - Yerel kopya / senkron motoru YOK.
 * - Her işlem doğrudan USB (FTP) üzerinde, anlık yapılır.
 * - USB her zaman tek doğru kaynak: uygulama silinip tekrar kurulsa bile
 *   veri kaybı olmaz, çünkü hiçbir şey yereldeki "silinme"ye bakarak
 *   USB'de otomatik silme yapmıyor. Silme sadece kullanıcının bilerek
 *   bastığı "Sil" butonuyla olur.
 *
 * Kasıtlı olarak Compose, Room, WorkManager, arka plan servisi
 * KULLANILMIYOR — kararlılık için minimum bağımlılık.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var settingsStore: SettingsStore
    private var currentPath: String = ""

    private lateinit var rootLayout: LinearLayout
    private lateinit var pathText: TextView

    private val pickFileLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri?.let { uploadPickedFile(it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashHandler.install(this)

        val previousCrash = CrashHandler.readLastCrash(this)
        if (previousCrash != null) {
            showPlainCrashScreen(previousCrash)
            return
        }

        try {
            settingsStore = SettingsStore(applicationContext)
            if (settingsStore.isConfigured()) {
                currentPath = settingsStore.load().rootPath
                buildBrowserUi()
                refreshList()
            } else {
                buildSettingsUi()
            }
        } catch (t: Throwable) {
            val sw = StringWriter()
            t.printStackTrace(PrintWriter(sw))
            showPlainCrashScreen(sw.toString())
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    // ---------------- AYARLAR EKRANI ----------------

    private fun buildSettingsUi() {
        val config = settingsStore.load()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(32), dp(20), dp(20))
        }

        root.addView(TextView(this).apply {
            text = "VaultSync — Bağlantı Ayarları"
            textSize = 20f
            setPadding(0, 0, 0, dp(12))
        })

        val hostInput = EditText(this).apply { hint = "Modem IP (ör. 192.168.1.1)"; setText(config.host) }
        val userInput = EditText(this).apply { hint = "FTP kullanıcı adı"; setText(config.username) }
        val passInput = EditText(this).apply {
            hint = "FTP parolası"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(config.password)
        }
        val rootInput = EditText(this).apply {
            hint = "Başlangıç klasörü (opsiyonel, ör. usb1_1)"
            setText(config.rootPath)
        }

        root.addView(hostInput)
        root.addView(userInput)
        root.addView(passInput)
        root.addView(rootInput)

        val saveButton = Button(this).apply {
            text = "Bağlan"
            setOnClickListener {
                settingsStore.save(
                    SettingsStore.Config(
                        host = hostInput.text.toString().trim(),
                        username = userInput.text.toString().trim(),
                        password = passInput.text.toString(),
                        rootPath = rootInput.text.toString().trim().trim('/')
                    )
                )
                currentPath = rootInput.text.toString().trim().trim('/')
                buildBrowserUi()
                refreshList()
            }
        }
        root.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(12))
        })
        root.addView(saveButton)

        setContentView(ScrollView(this).apply { addView(root) })
    }

    // ---------------- GEZİNTİ EKRANI ----------------

    private fun buildBrowserUi() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(24), dp(16), dp(16))
        }

        pathText = TextView(this).apply {
            textSize = 14f
            setPadding(0, 0, 0, dp(8))
        }
        container.addView(pathText)

        val buttonRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttonRow.addView(Button(this).apply {
            text = "Geri"
            setOnClickListener { navigateUp() }
        })
        buttonRow.addView(Button(this).apply {
            text = "Yenile"
            setOnClickListener { refreshList() }
        })
        buttonRow.addView(Button(this).apply {
            text = "Ayarlar"
            setOnClickListener { buildSettingsUi() }
        })
        container.addView(buttonRow)

        val actionRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actionRow.addView(Button(this).apply {
            text = "Dosya Yükle"
            setOnClickListener { pickFileLauncher.launch(arrayOf("*/*")) }
        })
        actionRow.addView(Button(this).apply {
            text = "Klasör Oluştur"
            setOnClickListener { promptCreateFolder() }
        })
        container.addView(actionRow)

        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, 0)
        }
        container.addView(rootLayout)

        setContentView(ScrollView(this).apply { addView(container) })
    }

    private fun navigateUp() {
        if (currentPath.isEmpty()) return
        currentPath = currentPath.substringBeforeLast('/', "")
        refreshList()
    }

    private fun refreshList() {
        pathText.text = "📁 /$currentPath"
        rootLayout.removeAllViews()
        rootLayout.addView(TextView(this).apply { text = "Yükleniyor..." })

        Thread {
            try {
                val config = settingsStore.load()
                val client = FtpClient(config.host, config.username, config.password)
                val entries = client.list(currentPath)
                runOnUiThread { showEntries(entries) }
            } catch (e: Exception) {
                runOnUiThread {
                    rootLayout.removeAllViews()
                    rootLayout.addView(TextView(this).apply {
                        text = "Bağlantı hatası: ${e.message}"
                    })
                }
            }
        }.start()
    }

    private fun showEntries(entries: List<FtpClient.Entry>) {
        rootLayout.removeAllViews()
        if (entries.isEmpty()) {
            rootLayout.addView(TextView(this).apply { text = "(bu klasör boş)" })
            return
        }
        for (entry in entries) {
            val row = TextView(this).apply {
                text = (if (entry.isDirectory) "📁 " else "📄 ") + entry.name +
                    if (!entry.isDirectory) "  (${entry.size / 1024} KB)" else ""
                textSize = 16f
                setPadding(0, dp(10), 0, dp(10))
                setOnClickListener {
                    if (entry.isDirectory) {
                        currentPath = if (currentPath.isEmpty()) entry.name else "$currentPath/${entry.name}"
                        refreshList()
                    } else {
                        showFileActions(entry)
                    }
                }
            }
            rootLayout.addView(row)
        }
    }

    private fun showFileActions(entry: FtpClient.Entry) {
        AlertDialog.Builder(this)
            .setTitle(entry.name)
            .setItems(arrayOf("İndir", "Sil", "İptal")) { _, which ->
                when (which) {
                    0 -> downloadFile(entry)
                    1 -> confirmDelete(entry)
                }
            }
            .show()
    }

    private fun downloadFile(entry: FtpClient.Entry) {
        Thread {
            try {
                val config = settingsStore.load()
                val client = FtpClient(config.host, config.username, config.password)
                val dest = File(getExternalFilesDir(null) ?: filesDir, "indirilenler/${entry.name}")
                client.download(currentPath, entry.name, dest)
                runOnUiThread {
                    Toast.makeText(this, "İndirildi: ${dest.absolutePath}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "İndirme hatası: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    private fun confirmDelete(entry: FtpClient.Entry) {
        AlertDialog.Builder(this)
            .setTitle("Silinsin mi?")
            .setMessage("${entry.name} USB'den kalıcı olarak silinecek.")
            .setPositiveButton("Sil") { _, _ ->
                Thread {
                    try {
                        val config = settingsStore.load()
                        val client = FtpClient(config.host, config.username, config.password)
                        client.delete(currentPath, entry.name, entry.isDirectory)
                        runOnUiThread { refreshList() }
                    } catch (e: Exception) {
                        runOnUiThread { Toast.makeText(this, "Silme hatası: ${e.message}", Toast.LENGTH_LONG).show() }
                    }
                }.start()
            }
            .setNegativeButton("Vazgeç", null)
            .show()
    }

    private fun promptCreateFolder() {
        val input = EditText(this)
        AlertDialog.Builder(this)
            .setTitle("Yeni Klasör Adı")
            .setView(input)
            .setPositiveButton("Oluştur") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) return@setPositiveButton
                Thread {
                    try {
                        val config = settingsStore.load()
                        val client = FtpClient(config.host, config.username, config.password)
                        client.makeDirectory(currentPath, name)
                        runOnUiThread { refreshList() }
                    } catch (e: Exception) {
                        runOnUiThread { Toast.makeText(this, "Hata: ${e.message}", Toast.LENGTH_LONG).show() }
                    }
                }.start()
            }
            .setNegativeButton("Vazgeç", null)
            .show()
    }

    private fun uploadPickedFile(uri: Uri) {
        val resolver = contentResolver
        val displayName = resolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && idx >= 0) cursor.getString(idx) else null
        } ?: "dosya_${System.currentTimeMillis()}"

        val mimeType = resolver.getType(uri) ?: ""

        Toast.makeText(this, "Yükleniyor: $displayName", Toast.LENGTH_SHORT).show()

        Thread {
            try {
                val tempDir = File(cacheDir, "upload_tmp").apply { mkdirs() }
                var localFile = File(tempDir, displayName)
                resolver.openInputStream(uri)?.use { input ->
                    localFile.outputStream().use { output -> input.copyTo(output) }
                }

                // Görsel ise küçültmeyi dene; başarısız olursa orijinali kullan.
                if (mimeType.startsWith("image/")) {
                    val compressed = File(tempDir, "c_$displayName")
                    val result = MediaCompressor.compressImage(this, uri, compressed)
                    if (result != null) localFile = result
                } else if (mimeType.startsWith("video/")) {
                    val compressed = File(tempDir, "c_$displayName")
                    val result = MediaCompressor.compressVideo(this, uri, compressed)
                    if (result != null) localFile = result
                }

                val config = settingsStore.load()
                val client = FtpClient(config.host, config.username, config.password)
                client.upload(currentPath, displayName, localFile)

                localFile.delete()
                runOnUiThread {
                    Toast.makeText(this, "Yüklendi: $displayName", Toast.LENGTH_SHORT).show()
                    refreshList()
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Yükleme hatası: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    // ---------------- ÇÖKME EKRANI ----------------

    private fun showPlainCrashScreen(errorText: String) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(40), dp(20), dp(20))
        }
        layout.addView(TextView(this).apply {
            text = "VaultSync bir önceki açılışta çöktü. Hata detayı:"
            textSize = 16f
            setPadding(0, 0, 0, dp(12))
        })
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
        layout.addView(copyButton)
        layout.addView(continueButton)
        layout.addView(TextView(this).apply {
            text = errorText
            textSize = 12f
            setTextIsSelectable(true)
        })
        setContentView(ScrollView(this).apply { addView(layout); gravity = Gravity.TOP })
    }
}
