package com.userapp.ftpmanager

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import org.apache.commons.net.ftp.FTPReply

/**
 * Uzak (FTP) klasör ile telefondaki yerel klasörü aynı tutar.
 * Boyutu değişmemiş dosyalar tekrar indirilmez/yüklenmez -> internet tasarrufu.
 * Telefon sıfırlanıp yerel klasör boş kalırsa, ilk çalıştırmada her şey USB'den
 * yeniden indirilerek yerel kopya otomatik olarak yeniden oluşturulur.
 */
object SyncManager {

    data class SyncResult(val downloaded: Int, val uploaded: Int, val errors: Int)

    fun sync(
        context: Context,
        host: String,
        port: Int,
        user: String,
        pass: String,
        remoteRootPath: String,
        localTreeUri: Uri,
        onLog: (String) -> Unit = {}
    ): SyncResult {
        val client = FTPClient()
        try {
            client.connectTimeout = 8000
            client.connect(host, port)
            if (!FTPReply.isPositiveCompletion(client.replyCode)) {
                onLog("Sunucuya bağlanılamadı")
                return SyncResult(0, 0, 1)
            }
            if (!client.login(user, pass)) {
                onLog("Giriş başarısız")
                client.disconnect()
                return SyncResult(0, 0, 1)
            }
            client.enterLocalPassiveMode()
            client.setFileType(FTP.BINARY_FILE_TYPE)

            val localRoot = DocumentFile.fromTreeUri(context, localTreeUri)
            if (localRoot == null || !localRoot.exists()) {
                onLog("Yerel klasör bulunamadı, tekrar seçin")
                client.disconnect()
                return SyncResult(0, 0, 1)
            }

            val stats = intArrayOf(0, 0, 0) // downloaded, uploaded, errors
            val rootPath = remoteRootPath.trimEnd('/').ifEmpty { "/" }
            syncDir(context, client, rootPath, localRoot, stats, onLog)

            client.logout()
            return SyncResult(stats[0], stats[1], stats[2])
        } catch (e: Exception) {
            onLog("Senkronizasyon hatası: ${e.message}")
            return SyncResult(0, 0, 1)
        } finally {
            try { if (client.isConnected) client.disconnect() } catch (_: Exception) {}
        }
    }

    private fun syncDir(
        context: Context,
        client: FTPClient,
        remotePath: String,
        localDir: DocumentFile,
        stats: IntArray,
        onLog: (String) -> Unit
    ) {
        val remoteFiles: Array<FTPFile> = try {
            client.listFiles(remotePath) ?: emptyArray()
        } catch (e: Exception) {
            onLog("Liste hatası ($remotePath): ${e.message}")
            stats[2]++
            return
        }

        val remoteNames = mutableSetOf<String>()

        // Uzaktakileri yerelle karşılaştır: eksik veya boyutu farklı olan dosyaları indir
        for (rf in remoteFiles) {
            if (rf.name == "." || rf.name == "..") continue
            remoteNames.add(rf.name)
            val remoteFullPath = "$remotePath/${rf.name}"

            if (rf.isDirectory) {
                var localSub = localDir.findFile(rf.name)
                if (localSub == null || !localSub.isDirectory) {
                    localSub = localDir.createDirectory(rf.name)
                }
                if (localSub != null) {
                    syncDir(context, client, remoteFullPath, localSub, stats, onLog)
                }
            } else {
                val localFile = localDir.findFile(rf.name)
                val needsDownload = localFile == null || localFile.length() != rf.size
                if (needsDownload) {
                    try {
                        val target = localFile ?: localDir.createFile(guessMime(rf.name), rf.name)
                        if (target != null) {
                            context.contentResolver.openOutputStream(target.uri)?.use { out ->
                                client.retrieveFile(remoteFullPath, out)
                            }
                            stats[0]++
                            onLog("İndirildi: ${rf.name}")
                        }
                    } catch (e: Exception) {
                        stats[2]++
                        onLog("İndirme hatası (${rf.name}): ${e.message}")
                    }
                }
                // boyut aynıysa hiçbir şey yapılmaz -> tekrar indirme yok, internet harcanmaz
            }
        }

        // Yerelde olup uzakta olmayan (telefonda yeni eklenmiş) dosya/klasörleri yukarı yükle
        val localItems = localDir.listFiles()
        for (localItem in localItems) {
            val name = localItem.name ?: continue
            if (remoteNames.contains(name)) continue
            val remoteFullPath = "$remotePath/$name"
            if (localItem.isDirectory) {
                try {
                    client.makeDirectory(remoteFullPath)
                    syncDir(context, client, remoteFullPath, localItem, stats, onLog)
                } catch (e: Exception) {
                    stats[2]++
                    onLog("Klasör oluşturma hatası ($name): ${e.message}")
                }
            } else {
                try {
                    context.contentResolver.openInputStream(localItem.uri)?.use { input ->
                        val ok = client.storeFile(remoteFullPath, input)
                        if (ok) {
                            stats[1]++
                            onLog("Yüklendi: $name")
                        } else {
                            stats[2]++
                        }
                    }
                } catch (e: Exception) {
                    stats[2]++
                    onLog("Yükleme hatası ($name): ${e.message}")
                }
            }
        }
    }

    private fun guessMime(name: String): String = when {
        name.endsWith(".jpg", true) || name.endsWith(".jpeg", true) -> "image/jpeg"
        name.endsWith(".png", true) -> "image/png"
        name.endsWith(".mp4", true) -> "video/mp4"
        name.endsWith(".pdf", true) -> "application/pdf"
        name.endsWith(".txt", true) -> "text/plain"
        else -> "application/octet-stream"
    }
}
