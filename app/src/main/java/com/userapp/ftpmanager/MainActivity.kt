package com.userapp.ftpmanager

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import org.apache.commons.net.ftp.FTPReply
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity(), FileActionListener {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var etHost: EditText
    private lateinit var etPort: EditText
    private lateinit var etUser: EditText
    private lateinit var etPass: EditText
    private lateinit var etPath: EditText
    private lateinit var listView: ListView
    private lateinit var tvSelected: TextView
    private lateinit var tvLog: TextView
    private lateinit var tvLocalFolder: TextView
    private lateinit var tvBreadcrumb: TextView
    private lateinit var cbAutoSync: CheckBox
    private lateinit var logContainer: ScrollView
    private lateinit var btnToggleLog: TextView

    private lateinit var prefs: Prefs
    private lateinit var fileAdapter: FileAdapter

    private var currentFiles: List<FTPFile> = emptyList()
    private var selectedFile: FTPFile? = null
    private var localFolderUri: Uri? = null
    private var logVisible = false

    private fun host() = etHost.text.toString().trim()
    private fun port() = etPort.text.toString().trim().ifEmpty { "21" }.toIntOrNull() ?: 21
    private fun user() = etUser.text.toString().trim()
    private fun pass() = etPass.text.toString()
    private fun remotePath() = etPath.text.toString().trim().ifEmpty { "/" }

    private val uploadPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) doUpload(uri)
    }

    private var pendingDownloadFile: FTPFile? = null
    private val downloadPicker = registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri: Uri? ->
        val f = pendingDownloadFile
        if (uri != null && f != null) doDownload(f, uri)
        pendingDownloadFile = null
    }

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            localFolderUri = uri
            tvLocalFolder.text = "Seçili klasör: ${uri.lastPathSegment}"
            savePrefs()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = Prefs(this)

        drawerLayout = findViewById(R.id.drawerLayout)
        etHost = findViewById(R.id.etHost)
        etPort = findViewById(R.id.etPort)
        etUser = findViewById(R.id.etUser)
        etPass = findViewById(R.id.etPass)
        etPath = findViewById(R.id.etPath)
        listView = findViewById(R.id.listView)
        tvSelected = findViewById(R.id.tvSelected)
        tvLog = findViewById(R.id.tvLog)
        tvLocalFolder = findViewById(R.id.tvLocalFolder)
        tvBreadcrumb = findViewById(R.id.tvBreadcrumb)
        cbAutoSync = findViewById(R.id.cbAutoSync)
        logContainer = findViewById(R.id.logContainer)
        btnToggleLog = findViewById(R.id.btnToggleLog)

        fileAdapter = FileAdapter(this, emptyList(), this)
        listView.adapter = fileAdapter

        loadPrefs()
        tvBreadcrumb.text = remotePath()

        findViewById<TextView>(R.id.btnMenu).setOnClickListener {
            drawerLayout.openDrawer(Gravity.START)
        }
        findViewById<TextView>(R.id.btnUp).setOnClickListener { navigateUp() }
        findViewById<Button>(R.id.btnGo).setOnClickListener { savePrefs(); tvBreadcrumb.text = remotePath(); doList() }
        findViewById<Button>(R.id.btnConnect).setOnClickListener { savePrefs(); tvBreadcrumb.text = remotePath(); doList(); drawerLayout.closeDrawers() }
        findViewById<Button>(R.id.btnList).setOnClickListener { savePrefs(); doList() }
        findViewById<Button>(R.id.btnUpload).setOnClickListener { uploadPicker.launch("*/*") }
        findViewById<Button>(R.id.btnSyncNow).setOnClickListener { savePrefs(); doSyncNow() }
        findViewById<Button>(R.id.btnPickFolder).setOnClickListener { folderPicker.launch(null) }
        btnToggleLog.setOnClickListener { toggleLog() }

        cbAutoSync.setOnCheckedChangeListener { _, checked ->
            prefs.setAutoSync(checked)
            if (checked) scheduleAutoSync() else cancelAutoSync()
        }
    }

    private fun toggleLog() {
        logVisible = !logVisible
        logContainer.visibility = if (logVisible) View.VISIBLE else View.GONE
        btnToggleLog.text = if (logVisible) "Günlük ▴" else "Günlük ▾"
    }

    // ---------- FileActionListener ----------
    override fun onOpenFolder(f: FTPFile) = navigateInto(f.name)

    override fun onSelect(f: FTPFile) {
        selectedFile = f
        tvSelected.text = "Seçili dosya: ${f.name}"
    }

    override fun onDownload(f: FTPFile) {
        selectedFile = f
        startDownload()
    }

    override fun onDelete(f: FTPFile) {
        selectedFile = f
        doDelete()
    }

    override fun onRename(f: FTPFile) {
        selectedFile = f
        showRenameDialog()
    }

    override fun onMove(f: FTPFile) {
        val base = remotePath().trimEnd('/')
        val currentFull = "$base/${f.name}"
        val input = EditText(this)
        input.setText(currentFull)
        AlertDialog.Builder(this)
            .setTitle("Taşı: yeni tam yol")
            .setMessage("Dosyanın taşınacağı tam yolu ve adını yaz")
            .setView(input)
            .setPositiveButton("Taşı") { _, _ ->
                val newPath = input.text.toString().trim()
                if (newPath.isNotEmpty()) doMove(f, currentFull, newPath)
            }
            .setNegativeButton("Vazgeç", null)
            .show()
    }

    override fun onCopy(f: FTPFile) {
        val base = remotePath().trimEnd('/')
        val currentFull = "$base/${f.name}"
        val input = EditText(this)
        input.setText(currentFull)
        AlertDialog.Builder(this)
            .setTitle("Kopyala: yeni tam yol")
            .setMessage("Kopyanın oluşturulacağı tam yolu ve adını yaz")
            .setView(input)
            .setPositiveButton("Kopyala") { _, _ ->
                val newPath = input.text.toString().trim()
                if (newPath.isNotEmpty()) doCopy(f, currentFull, newPath)
            }
            .setNegativeButton("Vazgeç", null)
            .show()
    }
    // -----------------------------------------

    private fun navigateInto(folderName: String) {
        val base = remotePath().trimEnd('/')
        val newPath = if (base.isEmpty()) "/$folderName" else "$base/$folderName"
        etPath.setText(newPath)
        tvBreadcrumb.text = newPath
        selectedFile = null
        tvSelected.text = "Seçili dosya: yok"
        savePrefs()
        doList()
    }

    private fun navigateUp() {
        val base = remotePath().trimEnd('/')
        if (base.isEmpty() || base == "/") {
            Toast.makeText(this, "Zaten en üst klasördesiniz", Toast.LENGTH_SHORT).show()
            return
        }
        val lastSlash = base.lastIndexOf('/')
        val parent = if (lastSlash <= 0) "/" else base.substring(0, lastSlash)
        etPath.setText(parent)
        tvBreadcrumb.text = parent
        selectedFile = null
        tvSelected.text = "Seçili dosya: yok"
        savePrefs()
        doList()
    }

    private fun loadPrefs() {
        val cfg = prefs.load()
        if (cfg != null) {
            etHost.setText(cfg.host)
            etPort.setText(cfg.port.toString())
            etUser.setText(cfg.user)
            etPass.setText(cfg.pass)
            etPath.setText(cfg.remotePath)
            if (cfg.localFolderUri != null) {
                localFolderUri = Uri.parse(cfg.localFolderUri)
                tvLocalFolder.text = "Seçili klasör: ${localFolderUri?.lastPathSegment}"
            }
        }
        cbAutoSync.isChecked = prefs.isAutoSync()
    }

    private fun savePrefs() {
        prefs.save(host(), port(), user(), pass(), remotePath(), localFolderUri?.toString())
    }

    private fun scheduleAutoSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .build()
        val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            SyncWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
        log("Otomatik senkronizasyon açıldı (yalnızca Wi-Fi)")
    }

    private fun cancelAutoSync() {
        WorkManager.getInstance(this).cancelUniqueWork(SyncWorker.UNIQUE_WORK_NAME)
        log("Otomatik senkronizasyon kapatıldı")
    }

    private fun doSyncNow() {
        val uri = localFolderUri
        if (uri == null) {
            Toast.makeText(this, "Önce yan menüden yerel klasör seçin", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            log("Senkronizasyon başladı...")
            val result = withContext(Dispatchers.IO) {
                SyncManager.sync(this@MainActivity, host(), port(), user(), pass(), remotePath(), uri) { msg ->
                    log(msg)
                }
            }
            log("Senkronizasyon bitti. İndirilen: ${result.downloaded}, Yüklenen: ${result.uploaded}, Hata: ${result.errors}")
        }
    }

    private fun log(msg: String) {
        runOnUiThread {
            tvLog.text = "$msg\n${tvLog.text}"
        }
    }

    private fun openClient(): FTPClient? {
        return try {
            val client = FTPClient()
            client.connectTimeout = 8000
            client.connect(host(), port())
            if (!FTPReply.isPositiveCompletion(client.replyCode)) {
                client.disconnect()
                log("Bağlantı reddedildi: ${client.replyString}")
                return null
            }
            if (!client.login(user(), pass())) {
                log("Giriş başarısız: kullanıcı adı/şifre hatalı")
                client.disconnect()
                return null
            }
            client.enterLocalPassiveMode()
            client.setFileType(org.apache.commons.net.ftp.FTP.BINARY_FILE_TYPE)
            client
        } catch (e: Exception) {
            log("Bağlantı hatası: ${e.message}")
            null
        }
    }

    private fun doList() {
        lifecycleScope.launch {
            log("Bağlanılıyor...")
            val files = withContext(Dispatchers.IO) {
                val client = openClient() ?: return@withContext null
                try {
                    val list = client.listFiles(remotePath())
                    client.logout()
                    list?.toList() ?: emptyList()
                } catch (e: Exception) {
                    log("Listeleme hatası: ${e.message}")
                    null
                } finally {
                    try { client.disconnect() } catch (_: Exception) {}
                }
            }
            if (files != null) {
                currentFiles = files
                fileAdapter.update(files)
                tvBreadcrumb.text = remotePath()
                log("Listeleme tamamlandı: ${files.size} öğe")
            }
        }
    }

    private fun doUpload(uri: Uri) {
        lifecycleScope.launch {
            log("Yükleniyor...")
            val fileName = queryFileName(uri) ?: "upload_${System.currentTimeMillis()}"
            val ok = withContext(Dispatchers.IO) {
                val client = openClient() ?: return@withContext false
                try {
                    contentResolver.openInputStream(uri)?.use { input ->
                        val remoteFullPath = remotePath().trimEnd('/') + "/" + fileName
                        client.storeFile(remoteFullPath, input)
                    } ?: false
                } catch (e: Exception) {
                    log("Yükleme hatası: ${e.message}")
                    false
                } finally {
                    try { client.logout(); client.disconnect() } catch (_: Exception) {}
                }
            }
            log(if (ok) "Yükleme başarılı: $fileName" else "Yükleme başarısız")
            if (ok) doList()
        }
    }

    private fun startDownload() {
        val f = selectedFile
        if (f == null) {
            Toast.makeText(this, "Önce bir dosya seçin", Toast.LENGTH_SHORT).show()
            return
        }
        pendingDownloadFile = f
        downloadPicker.launch(f.name)
    }

    private fun doDownload(f: FTPFile, uri: Uri) {
        lifecycleScope.launch {
            log("İndiriliyor: ${f.name}")
            val ok = withContext(Dispatchers.IO) {
                val client = openClient() ?: return@withContext false
                try {
                    contentResolver.openOutputStream(uri)?.use { output ->
                        val remoteFullPath = remotePath().trimEnd('/') + "/" + f.name
                        client.retrieveFile(remoteFullPath, output)
                    } ?: false
                } catch (e: Exception) {
                    log("İndirme hatası: ${e.message}")
                    false
                } finally {
                    try { client.logout(); client.disconnect() } catch (_: Exception) {}
                }
            }
            log(if (ok) "İndirme başarılı: ${f.name}" else "İndirme başarısız")
        }
    }

    private fun doDelete() {
        val f = selectedFile
        if (f == null) {
            Toast.makeText(this, "Önce bir dosya seçin", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Sil")
            .setMessage("'${f.name}' silinsin mi?")
            .setPositiveButton("Sil") { _, _ ->
                lifecycleScope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        val client = openClient() ?: return@withContext false
                        try {
                            val remoteFullPath = remotePath().trimEnd('/') + "/" + f.name
                            if (f.isDirectory) client.removeDirectory(remoteFullPath)
                            else client.deleteFile(remoteFullPath)
                        } catch (e: Exception) {
                            log("Silme hatası: ${e.message}")
                            false
                        } finally {
                            try { client.logout(); client.disconnect() } catch (_: Exception) {}
                        }
                    }
                    log(if (ok) "Silindi: ${f.name}" else "Silme başarısız")
                    selectedFile = null
                    tvSelected.text = "Seçili dosya: yok"
                    if (ok) doList()
                }
            }
            .setNegativeButton("Vazgeç", null)
            .show()
    }

    private fun showRenameDialog() {
        val f = selectedFile
        if (f == null) {
            Toast.makeText(this, "Önce bir dosya seçin", Toast.LENGTH_SHORT).show()
            return
        }
        val input = EditText(this)
        input.setText(f.name)
        AlertDialog.Builder(this)
            .setTitle("Yeni ad")
            .setView(input)
            .setPositiveButton("Değiştir") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) doRename(f, newName)
            }
            .setNegativeButton("Vazgeç", null)
            .show()
    }

    private fun doRename(f: FTPFile, newName: String) {
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                val client = openClient() ?: return@withContext false
                try {
                    val base = remotePath().trimEnd('/')
                    val oldPath = "$base/${f.name}"
                    val newPath = "$base/$newName"
                    client.rename(oldPath, newPath)
                } catch (e: Exception) {
                    log("Yeniden adlandırma hatası: ${e.message}")
                    false
                } finally {
                    try { client.logout(); client.disconnect() } catch (_: Exception) {}
                }
            }
            log(if (ok) "Ad değiştirildi: ${f.name} -> $newName" else "Ad değiştirme başarısız")
            if (ok) doList()
        }
    }

    private fun doMove(f: FTPFile, oldPath: String, newPath: String) {
        lifecycleScope.launch {
            log("Taşınıyor: ${f.name}")
            val ok = withContext(Dispatchers.IO) {
                val client = openClient() ?: return@withContext false
                try {
                    FtpOps.move(client, oldPath, newPath)
                } finally {
                    try { client.logout(); client.disconnect() } catch (_: Exception) {}
                }
            }
            log(if (ok) "Taşındı: $newPath" else "Taşıma başarısız")
            if (ok) doList()
        }
    }

    private fun doCopy(f: FTPFile, oldPath: String, newPath: String) {
        lifecycleScope.launch {
            log("Kopyalanıyor: ${f.name}")
            val ok = withContext(Dispatchers.IO) {
                val client = openClient() ?: return@withContext false
                try {
                    if (f.isDirectory) {
                        FtpOps.copyDir(this@MainActivity, client, oldPath, newPath) { msg -> log(msg) }
                    } else {
                        FtpOps.copyFile(this@MainActivity, client, oldPath, newPath)
                    }
                } finally {
                    try { client.logout(); client.disconnect() } catch (_: Exception) {}
                }
            }
            log(if (ok) "Kopyalandı: $newPath" else "Kopyalama başarısız")
            if (ok) doList()
        }
    }

    private fun queryFileName(uri: Uri): String? {
        var name: String? = null
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (it.moveToFirst() && idx >= 0) name = it.getString(idx)
        }
        return name
    }
}
