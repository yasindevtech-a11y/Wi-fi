package com.senin.vaultsync.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Bir dosyanın EN SON başarılı senkrondan sonraki durumu.
 * Yeni senkronda: şu anki yerel durum + şu anki uzak durum, bu kayıtla
 * karşılaştırılarak "ne değişmiş" tespit edilir (3 yönlü karşılaştırma).
 *
 * relativePath: kasa klasörüne göre yol, örn: "faturalar/eylul.pdf"
 */
@Entity(tableName = "sync_manifest")
data class SyncManifestEntity(
    @PrimaryKey val relativePath: String,
    val size: Long,
    val lastModified: Long,   // epoch millis
    val contentHash: String   // dosya içeriğinin kısa hash'i (CRC32/MD5)
)
