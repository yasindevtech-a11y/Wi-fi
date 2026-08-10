package com.userapp.ftpmanager

import android.content.Context
import org.apache.commons.net.ftp.FTPClient
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object FtpOps {

    /** FTP'de yerleşik "kopyala" komutu yok; dosyayı geçici olarak indirip hedefe yükleyerek kopyalar. */
    fun copyFile(context: Context, client: FTPClient, srcPath: String, destPath: String): Boolean {
        val tmp = File.createTempFile("cp_", ".tmp", context.cacheDir)
        return try {
            FileOutputStream(tmp).use { out -> client.retrieveFile(srcPath, out) }
            FileInputStream(tmp).use { input -> client.storeFile(destPath, input) }
        } catch (e: Exception) {
            false
        } finally {
            tmp.delete()
        }
    }

    /** Klasörü ve içindeki her şeyi hedefe (yeni yol) özyinelemeli olarak kopyalar. */
    fun copyDir(context: Context, client: FTPClient, srcPath: String, destPath: String, onLog: (String) -> Unit): Boolean {
        try {
            client.makeDirectory(destPath)
            val items = client.listFiles(srcPath) ?: emptyArray()
            for (it in items) {
                if (it.name == "." || it.name == "..") continue
                val s = "$srcPath/${it.name}"
                val d = "$destPath/${it.name}"
                val ok = if (it.isDirectory) copyDir(context, client, s, d, onLog) else copyFile(context, client, s, d)
                onLog(if (ok) "Kopyalandı: ${it.name}" else "Kopyalama hatası: ${it.name}")
            }
            return true
        } catch (e: Exception) {
            onLog("Klasör kopyalama hatası: ${e.message}")
            return false
        }
    }

    /** Taşıma = yeniden adlandırma; FTP sunucularının çoğunda aynı disk içinde klasörler arası da çalışır. */
    fun move(client: FTPClient, srcPath: String, destPath: String): Boolean {
        return try {
            client.rename(srcPath, destPath)
        } catch (e: Exception) {
            false
        }
    }
}
