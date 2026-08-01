package com.senin.vaultsync.sync

import com.senin.vaultsync.data.SettingsStore
import com.senin.vaultsync.data.SmbClient
import com.senin.vaultsync.data.SyncManifestDao
import com.senin.vaultsync.data.SyncManifestEntity
import java.io.File
import java.security.MessageDigest

/**
 * Kural: USB disk her zaman "gerçek kaynak" gibi davranılır.
 *
 * - Yerel manifest (Room) BOŞSA -> bu ilk kurulum/yeniden yükleme demektir.
 *   Silme kararı ASLA verilmez, sadece uzaktaki her şey yerele indirilir (kurtarma modu).
 * - Yerel manifest DOLUYSA -> normal 3 yönlü senkron çalışır:
 *   son bilinen durum + şu anki yerel durum + şu anki uzak durum karşılaştırılır.
 */
class SyncEngine(
    private val vaultDir: File,
    private val dao: SyncManifestDao
) {
    sealed class Progress {
        data class Info(val message: String) : Progress()
        data class FileDone(val relativePath: String, val action: String) : Progress()
        data class Error(val relativePath: String?, val message: String) : Progress()
        data object Finished : Progress()
    }

    suspend fun run(config: SettingsStore.Config, onProgress: suspend (Progress) -> Unit) {
        val client = SmbClient(config.host, config.share, config.username, config.password)
        vaultDir.mkdirs()

        val knownState = dao.getAll().associateBy { it.relativePath }
        val isFirstRun = knownState.isEmpty()

        val remoteFiles = try {
            client.listRemoteFiles()
        } catch (e: Exception) {
            onProgress(Progress.Error(null, "USB'ye bağlanılamadı: ${e.message}"))
            return
        }

        val localFiles = vaultDir.walkTopDown()
            .filter { it.isFile }
            .associateBy { it.relativeTo(vaultDir).path.replace(File.separatorChar, '/') }

        if (isFirstRun) {
            // --- KURTARMA MODU: sadece indir, hiçbir şey silme ---
            onProgress(Progress.Info("İlk kurulum tespit edildi, USB'deki dosyalar geri yükleniyor..."))
            for ((path, info) in remoteFiles) {
                val dest = File(vaultDir, path)
                try {
                    client.download(path, dest)
                    dao.upsert(
                        SyncManifestEntity(
                            relativePath = path,
                            size = info.size,
                            lastModified = info.lastModified,
                            contentHash = hashOf(dest)
                        )
                    )
                    onProgress(Progress.FileDone(path, "geri yüklendi"))
                } catch (e: Exception) {
                    onProgress(Progress.Error(path, e.message ?: "bilinmeyen hata"))
                }
            }
            onProgress(Progress.Finished)
            return
        }

        // --- NORMAL 2 YÖNLÜ SENKRON ---
        val allPaths = (knownState.keys + localFiles.keys + remoteFiles.keys).toSet()

        for (path in allPaths) {
            val known = knownState[path]
            val local = localFiles[path]
            val remote = remoteFiles[path]

            try {
                when {
                    // Değişmemiş, atla
                    known != null && local != null && remote != null &&
                        local.length() == known.size && local.lastModified() == known.lastModified &&
                        remote.size == known.size && remote.lastModified == known.lastModified -> continue

                    // Sadece yerelde yeni dosya -> yükle
                    known == null && local != null && remote == null -> {
                        client.upload(path, local)
                        dao.upsert(SyncManifestEntity(path, local.length(), local.lastModified(), hashOf(local)))
                        onProgress(Progress.FileDone(path, "USB'ye yüklendi"))
                    }

                    // Sadece uzakta yeni dosya -> indir
                    known == null && local == null && remote != null -> {
                        val dest = File(vaultDir, path)
                        client.download(path, dest)
                        dao.upsert(SyncManifestEntity(path, remote.size, remote.lastModified, hashOf(dest)))
                        onProgress(Progress.FileDone(path, "telefona indirildi"))
                    }

                    // Yerelde silinmiş, uzakta hâlâ var -> uzaktan da sil
                    known != null && local == null && remote != null -> {
                        client.delete(path)
                        dao.delete(path)
                        onProgress(Progress.FileDone(path, "USB'den silindi"))
                    }

                    // Uzakta silinmiş, yerelde hâlâ var -> yerelden de sil
                    known != null && local != null && remote == null -> {
                        local.delete()
                        dao.delete(path)
                        onProgress(Progress.FileDone(path, "telefondan silindi"))
                    }

                    // İki taraf da silmiş
                    known != null && local == null && remote == null -> {
                        dao.delete(path)
                    }

                    // Her iki taraf da değiştirmiş -> ÇAKIŞMA, veri kaybetmeden ikisini de sakla
                    known != null && local != null && remote != null &&
                        (local.length() != known.size || local.lastModified() != known.lastModified) &&
                        (remote.size != known.size || remote.lastModified != known.lastModified) -> {
                        val conflictName = conflictPath(path)
                        client.upload(conflictName, local) // yerel sürümü çakışma kopyası olarak yükle
                        val dest = File(vaultDir, path)
                        client.download(path, dest) // uzaktaki sürümü ana dosya olarak indir
                        dao.upsert(SyncManifestEntity(path, dest.length(), dest.lastModified(), hashOf(dest)))
                        onProgress(Progress.FileDone(path, "ÇAKIŞMA - iki kopya da saklandı ($conflictName)"))
                    }

                    // Sadece yerel değişmiş -> yükle
                    known != null && local != null && remote != null &&
                        (local.length() != known.size || local.lastModified() != known.lastModified) -> {
                        client.upload(path, local)
                        dao.upsert(SyncManifestEntity(path, local.length(), local.lastModified(), hashOf(local)))
                        onProgress(Progress.FileDone(path, "güncelleme USB'ye yüklendi"))
                    }

                    // Sadece uzak değişmiş -> indir
                    known != null && local != null && remote != null -> {
                        val dest = File(vaultDir, path)
                        client.download(path, dest)
                        dao.upsert(SyncManifestEntity(path, remote.size, remote.lastModified, hashOf(dest)))
                        onProgress(Progress.FileDone(path, "güncelleme telefona indirildi"))
                    }
                }
            } catch (e: Exception) {
                onProgress(Progress.Error(path, e.message ?: "bilinmeyen hata"))
            }
        }
        onProgress(Progress.Finished)
    }

    private fun conflictPath(path: String): String {
        val dot = path.lastIndexOf('.')
        val stamp = System.currentTimeMillis()
        return if (dot > 0) {
            path.substring(0, dot) + " (telefon-çakışma-$stamp)" + path.substring(dot)
        } else {
            "$path (telefon-çakışma-$stamp)"
        }
    }

    private fun hashOf(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } > 0) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
