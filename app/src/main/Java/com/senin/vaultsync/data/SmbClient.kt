package com.senin.vaultsync.data

import jcifs.CIFSContext
import jcifs.context.SingletonContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import java.io.File

/**
 * Modemdeki USB diske (Samba paylaşımı üzerinden) bağlanan istemci.
 * Örn. tam adres: smb://192.168.1.1/usb1_1/vault/
 */
class SmbClient(
    private val host: String,
    private val share: String,
    private val username: String,
    private val password: String
) {
    private val baseUrl: String
        get() = "smb://$host/$share/vault/"

    private fun context(): CIFSContext {
        val base = SingletonContext.getInstance()
        val auth = NtlmPasswordAuthenticator("", username, password)
        return base.withCredentials(auth)
    }

    /** Uzak "vault" klasöründeki tüm dosyaları (alt klasörler dahil) göreli yollarıyla listeler */
    fun listRemoteFiles(): Map<String, RemoteFileInfo> {
        val result = mutableMapOf<String, RemoteFileInfo>()
        val root = SmbFile(baseUrl, context())
        if (!root.exists()) {
            root.mkdirs()
            return result
        }
        walk(root, "", result)
        return result
    }

    private fun walk(dir: SmbFile, prefix: String, out: MutableMap<String, RemoteFileInfo>) {
        val children = dir.listFiles() ?: return
        for (child in children) {
            val relPath = if (prefix.isEmpty()) child.name.trimEnd('/') else "$prefix/${child.name.trimEnd('/')}"
            if (child.isDirectory) {
                walk(child, relPath, out)
            } else {
                out[relPath] = RemoteFileInfo(
                    relativePath = relPath,
                    size = child.length(),
                    lastModified = child.lastModified()
                )
            }
        }
    }

    fun download(relativePath: String, destination: File) {
        val remote = SmbFile(baseUrl + relativePath, context())
        destination.parentFile?.mkdirs()
        remote.inputStream.use { input ->
            destination.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    fun upload(relativePath: String, source: File) {
        val remote = SmbFile(baseUrl + relativePath, context())
        remote.parent?.let { SmbFile(it, context()).mkdirs() }
        source.inputStream().use { input ->
            remote.outputStream.use { output ->
                input.copyTo(output)
            }
        }
    }

    fun delete(relativePath: String) {
        val remote = SmbFile(baseUrl + relativePath, context())
        if (remote.exists()) remote.delete()
    }

    data class RemoteFileInfo(
        val relativePath: String,
        val size: Long,
        val lastModified: Long
    )
}
