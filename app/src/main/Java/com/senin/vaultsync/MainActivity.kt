package com.senin.vaultsync

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import android.widget.LinearLayout
import android.widget.Button as AndroidButton
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.senin.vaultsync.data.SettingsStore
import com.senin.vaultsync.sync.SyncForegroundService
import kotlinx.coroutines.launch
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class MainActivity : ComponentActivity() {

    private lateinit var settingsStore: SettingsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashHandler.install(this)

        // Önceki açılışta kaydedilmiş bir çökme var mı? Varsa Compose'a hiç
        // dokunmadan, düz Android View ile göster (Compose'un kendisi hata
        // veriyorsa bile bu ekran çalışsın diye).
        val previousCrash = CrashHandler.readLastCrash(this)
        if (previousCrash != null) {
            showPlainCrashScreen(previousCrash)
            return
        }

        // Normal açılışı da try-catch'e alıyoruz: eğer burada bir şey patlarsa
        // yeniden başlatmayı beklemeden, aynı anda hatayı ekranda gösteririz.
        try {
            settingsStore = SettingsStore(applicationContext)
            setContent {
                MaterialTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        VaultScreen(
                            settingsStore = settingsStore,
                            vaultDir = File(getExternalFilesDir(null) ?: filesDir, "vault"),
                            onSyncNow = {
                                startForegroundService(Intent(this, SyncForegroundService::class.java))
                            }
                        )
                    }
                }
            }
        } catch (t: Throwable) {
            val sw = StringWriter()
            t.printStackTrace(PrintWriter(sw))
            showPlainCrashScreen(sw.toString())
        }
    }

    /** Compose kullanmadan, saf Android View'larla çökme metnini gösterir + kopyalama butonu ekler */
    private fun showPlainCrashScreen(errorText: String) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 80, 40, 40)
        }

        val title = TextView(this).apply {
            text = "VaultSync bir önceki açılışta çöktü. Hata detayı:"
            textSize = 16f
            setPadding(0, 0, 0, 24)
        }

        val body = TextView(this).apply {
            text = errorText
            textSize = 12f
            setTextIsSelectable(true)
        }

        val copyButton = AndroidButton(this).apply {
            text = "Hatayı Kopyala"
            setOnClickListener {
                val clipboard = getSystemService(ClipboardManager::class.java)
                clipboard.setPrimaryClip(ClipData.newPlainText("VaultSync hata", errorText))
                Toast.makeText(this@MainActivity, "Kopyalandı", Toast.LENGTH_SHORT).show()
            }
        }

        val continueButton = AndroidButton(this).apply {
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

        val scrollView = ScrollView(this).apply { addView(layout) }
        setContentView(scrollView)
    }
}

@Composable
fun VaultScreen(
    settingsStore: SettingsStore,
    vaultDir: File,
    onSyncNow: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val config by settingsStore.config.collectAsState(initial = SettingsStore.Config())

    var host by remember(config) { mutableStateOf(config.host) }
    var username by remember(config) { mutableStateOf(config.username) }
    var password by remember(config) { mutableStateOf(config.password) }
    var homeSsid by remember(config) { mutableStateOf(config.homeSsid) }

    var files by remember { mutableStateOf(listVaultFiles(vaultDir)) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { copyIntoVault(context, it, vaultDir) }
        files = listVaultFiles(vaultDir)
    }

    Column(modifier = Modifier.padding(16.dp).verticalScrollIfNeeded()) {
        Text("VaultSync Ayarları", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(host, { host = it }, label = { Text("Modem IP (ör. 192.168.1.1)") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(username, { username = it }, label = { Text("FTP kullanıcı adı") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(password, { password = it }, label = { Text("FTP parolası") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(homeSsid, { homeSsid = it }, label = { Text("Ev WiFi adı (SSID)") }, modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(12.dp))
        Button(onClick = {
            scope.launch {
                settingsStore.save(SettingsStore.Config(host, username, password, homeSsid))
            }
        }, modifier = Modifier.fillMaxWidth()) {
            Text("Ayarları Kaydet")
        }

        Spacer(Modifier.height(8.dp))
        Button(onClick = onSyncNow, modifier = Modifier.fillMaxWidth()) {
            Text("Şimdi Senkronize Et")
        }

        Spacer(Modifier.height(16.dp))
        Text("Kasaya Dosya Ekle", style = MaterialTheme.typography.titleMedium)
        Button(onClick = { pickFile.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
            Text("Dosya Seç")
        }

        Spacer(Modifier.height(16.dp))
        Text("Kasadaki Dosyalar (${files.size})", style = MaterialTheme.typography.titleMedium)
        LazyColumn {
            items(files) { f ->
                Text(f, modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
}

private fun listVaultFiles(vaultDir: File): List<String> {
    if (!vaultDir.exists()) return emptyList()
    return vaultDir.walkTopDown()
        .filter { it.isFile }
        .map { it.relativeTo(vaultDir).path }
        .toList()
}

private fun copyIntoVault(context: android.content.Context, uri: Uri, vaultDir: File) {
    val resolver = context.contentResolver
    val displayName = resolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
    } ?: "dosya_${System.currentTimeMillis()}"

    vaultDir.mkdirs()
    val dest = File(vaultDir, displayName)
    resolver.openInputStream(uri)?.use { input ->
        dest.outputStream().use { output -> input.copyTo(output) }
    }
}

private fun Modifier.verticalScrollIfNeeded(): Modifier = this
