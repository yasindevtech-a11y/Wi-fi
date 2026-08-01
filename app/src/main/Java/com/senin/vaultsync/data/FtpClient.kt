package com.senin.vaultsync.data

import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import java.io.File

/**
 * "Bulut sürücüsü" gibi çalışan basit FTP istemcisi.
 * Yerel bir kopya/manifest tutmuyor: her işlem doğrudan USB üzerinde,
 * anlık olarak yapılır. USB her zaman tek doğru kaynaktır.
 */
class FtpClient(
    private val host: String,
    private val username: String,
    private val password: String,
    private val port: Int = 21
) {
    data class Entry(
        val name: String,
        val isDirectory: Boolean,
        val size: Long,
        val lastModified: Long
    )

    private fun connect(): FTPClient {
        val client = FTPClient()
        client.connect(host, port)
        if (!client.login(username, password)) {
            client.disconnect()
            throw IllegalStateException("FTP girişi başarısız (kullanıcı adı/parola hatalı olabilir)")
        }
        client.enterLocalPassiveMode()
        client.setFileType(FTP.BINARY_FILE_TYPE)
        return client
    }

    /** path boşsa FTP kökünü listeler, örn. "usb1_1" veya "usb1_1/faturalar" */
    fun list(path: String): List<Entry> {
        val client = connect()
        try {
            if (path.isNotEmpty() && !client.changeWorkingDirectory(path)) {
                throw IllegalStateException("Klasör bulunamadı: $path")
            }
            val files: Array<FTPFile> = client.listFiles() ?: emptyArray()
            return files
                .filter { it.name != "." && it.name != ".." }
                .map {
                    Entry(
                        name = it.name,
                        isDirectory = it.isDirectory,
                        size = it.size,
                        lastModified = it.timestamp?.timeInMillis ?: 0L
                    )
                }
                .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        } finally {
            client.logout()
            client.disconnect()
        }
    }

    fun makeDirectory(path: String, name: String) {
        val client = connect()
        try {
            if (path.isNotEmpty()) client.changeWorkingDirectory(path)
            client.makeDirectory(name)
        } finally {
            client.logout()
            client.disconnect()
        }
    }

    fun upload(path: String, fileName: String, source: File) {
        val client = connect()
        try {
            if (path.isNotEmpty()) client.changeWorkingDirectory(path)
            source.inputStream().use { input ->
                if (!client.storeFile(fileName, input)) {
                    throw IllegalStateException("Yükleme başarısız: $fileName")
                }
            }
        } finally {
            client.logout()
            client.disconnect()
        }
    }

    fun download(path: String, fileName: String, destination: File) {
        val client = connect()
        try {
            if (path.isNotEmpty()) client.changeWorkingDirectory(path)
            destination.parentFile?.mkdirs()
            destination.outputStream().use { output ->
                if (!client.retrieveFile(fileName, output)) {
                    throw IllegalStateException("İndirme başarısız: $fileName")
                }
            }
        } finally {
            client.logout()
            client.disconnect()
        }
    }

    fun delete(path: String, name: String, isDirectory: Boolean) {
        val client = connect()
        try {
            if (path.isNotEmpty()) client.changeWorkingDirectory(path)
            if (isDirectory) {
                client.removeDirectory(name)
            } else {
                client.deleteFile(name)
            }
        } finally {
            client.logout()
            client.disconnect()
        }
    }
}
