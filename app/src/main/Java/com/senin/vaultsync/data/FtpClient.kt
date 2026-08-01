package com.senin.vaultsync.data

import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import java.io.File

/**
 * Modemdeki USB diske FTP üzerinden bağlanan istemci.
 * ZXHN H3600'de Samba yok, sadece FTP servisi var (Yerel Ağ > FTP sayfası).
 *
 * Router panelindeki "Kullanıcı Adı" / "Parola" ile giriş yapılır.
 * FTP'ye bağlanınca kullanıcının kök dizini genelde USB diskin köküdür,
 * biz altında bir "vault" klasörü açıp onunla çalışıyoruz.
 */
class FtpClient(
    private val host: String,
    private val username: String,
    private val password: String,
    private val port: Int = 21
) {
    private val vaultFolder = "vault"

    private fun connect(): FTPClient {
        val client = FTPClient()
        client.connect(host, port)
        if (!client.login(username, password)) {
            client.disconnect()
            throw IllegalStateException("FTP girişi başarısız oldu (kullanıcı adı/parola hatalı olabilir)")
        }
        client.enterLocalPassiveMode()
        client.setFileType(FTP.BINARY_FILE_TYPE)

        // vault klasörü yoksa oluştur
        if (!client.changeWorkingDirectory(vaultFolder)) {
            client.makeDirectory(vaultFolder)
            client.changeWorkingDirectory(vaultFolder)
        }
        return client
    }

    fun listRemoteFiles(): Map<String, RemoteFileInfo> {
        val client = connect()
        val result = mutableMapOf<String, RemoteFileInfo>()
        try {
            walk(client, "", result)
        } finally {
            client.logout()
            client.disconnect()
        }
        return result
    }

    private fun walk(client: FTPClient, prefix: String, out: MutableMap<String, RemoteFileInfo>) {
        val entries: Array<FTPFile> = client.listFiles() ?: return
        for (entry in entries) {
            if (entry.name == "." || entry.name == "..") continue
            val relPath = if (prefix.isEmpty()) entry.name else "$prefix/${entry.name}"
            if (entry.isDirectory) {
                if (client.changeWorkingDirectory(entry.name)) {
                    walk(client, relPath, out)
                    client.changeToParentDirectory()
                }
            } else {
                out[relPath] = RemoteFileInfo(
                    relativePath = relPath,
                    size = entry.size,
                    lastModified = entry.timestamp?.timeInMillis ?: 0L
                )
            }
        }
    }

    fun download(relativePath: String, destination: File) {
        val client = connect()
        try {
            destination.parentFile?.mkdirs()
            navigateToParent(client, relativePath)
            val fileName = relativePath.substringAfterLast('/')
            destination.outputStream().use { output ->
                if (!client.retrieveFile(fileName, output)) {
                    throw IllegalStateException("İndirme başarısız: $relativePath")
                }
            }
        } finally {
            client.logout()
            client.disconnect()
        }
    }

    fun upload(relativePath: String, source: File) {
        val client = connect()
        try {
            createParentDirs(client, relativePath)
            navigateToParent(client, relativePath)
            val fileName = relativePath.substringAfterLast('/')
            source.inputStream().use { input ->
                if (!client.storeFile(fileName, input)) {
                    throw IllegalStateException("Yükleme başarısız: $relativePath")
                }
            }
        } finally {
            client.logout()
            client.disconnect()
        }
    }

    fun delete(relativePath: String) {
        val client = connect()
        try {
            navigateToParent(client, relativePath)
            val fileName = relativePath.substringAfterLast('/')
            client.deleteFile(fileName)
        } finally {
            client.logout()
            client.disconnect()
        }
    }

    /** relativePath "a/b/c.jpg" ise vault içinde a/b klasörüne kadar iner */
    private fun navigateToParent(client: FTPClient, relativePath: String) {
        val parts = relativePath.split("/").dropLast(1)
        for (part in parts) {
            client.changeWorkingDirectory(part)
        }
    }

    private fun createParentDirs(client: FTPClient, relativePath: String) {
        val parts = relativePath.split("/").dropLast(1)
        for (part in parts) {
            if (!client.changeWorkingDirectory(part)) {
                client.makeDirectory(part)
                client.changeWorkingDirectory(part)
            }
        }
        // pointer'ı vault köküne geri al, navigateToParent zaten tekrar inecek
        repeat(parts.size) { client.changeToParentDirectory() }
    }

    data class RemoteFileInfo(
        val relativePath: String,
        val size: Long,
        val lastModified: Long
    )
}
