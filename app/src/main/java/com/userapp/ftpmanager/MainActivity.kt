package com.userapp.ftpmanager

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
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

class MainActivity : AppCompatActivity() {

    private lateinit var etHost: EditText
    private lateinit var etPort: EditText
    private lateinit var etUser: EditText
    private lateinit var etPass: EditText
    private lateinit var etPath: EditText
    private lateinit var listView: ListView
    private lateinit var tvSelected: TextView
    private lateinit var tvLog: TextView
    private lateinit var tvLocalFolder: TextView
    private lateinit var cbAutoSync: CheckBox

    private lateinit var prefs: Prefs

    private var currentFiles: List<FTPFile> = emptyList()
    private var selectedFile: FTPFile? = null
    private var localFolderUri: Uri? = null

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

        etHost = findViewById(R.id.etHost)
        etPort = findViewById(R.id.etPort)
        etUser = findViewById(R.id.etUser)
        etPass = findViewById(R.id.etPass)
        etPath = findViewById(R.id.etPath)
        listView = findViewById(R.id.listView)
        tvSelected = findViewById(R.id.tvSelected)
        tvLog = findViewById(R.id.tvLog)
        tvLocalFolder = findViewById(R.id.tvLocalFolder)
        cbAutoSync = findViewById(R.id.cbAutoSync)

        loadPrefs()

        findViewById<Button>(R.id.btnConnect).setOnClickListener { savePrefs(); doList() }
        findViewById<Button>(R.id.btnList).setOnClickListener { savePrefs(); doList() }
        findViewById<Button>(R.id.btnUpload).setOnClickListener { uploadPicker.launch("*/*") }
        findViewById<Button>(R.id.btnDownload).setOnClickListener { startDownload() }
        findViewById<Button>(R.id.btnDelete).setOnClickListener { doDelete() }
        findViewById<Button>(R.id.btnRename).setOnClickListener { showRenameDialog() }
        findViewById<Button>(R.id.btnPickFolder).setOnClickListener { folderPicker.launch(null) }
        findViewById<Button>(R.id.btnSyncNow).setOnClickListener { savePrefs(); doSyncNow() }

        cbAutoSync.setOnCheckedChangeListener { _, checked ->
            prefs.setAutoSync(checked)
            if (checked) scheduleAutoSync() else cancelAutoSync()
        }

        listView.setOnItemClickListener { _, _, position, _ ->
            selectedFile = currentFiles.getOrNull(position)
            tvSelected.text = "Seçili dosya: ${selectedFile?.name ?: "yok"}"
        }
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
            .setRequiredNetworkType(NetworkType.UNMETERED) // sadece Wi-Fi, mobil veri harcamaz
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
            Toast.makeText(this, "Önce yerel klasör seçin", Toast.LENGTH_SHORT).show()
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
                val names = files.map { (if (it.isDirectory) "[KLASÖR] " else "") + it.name }
                listView.adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_list_item_1, names)
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
