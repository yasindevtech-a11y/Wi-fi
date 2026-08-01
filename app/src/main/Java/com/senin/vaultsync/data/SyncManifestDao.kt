package com.senin.vaultsync.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SyncManifestDao {

    @Query("SELECT * FROM sync_manifest")
    suspend fun getAll(): List<SyncManifestEntity>

    @Query("SELECT * FROM sync_manifest WHERE relativePath = :path")
    suspend fun get(path: String): SyncManifestEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SyncManifestEntity)

    @Query("DELETE FROM sync_manifest WHERE relativePath = :path")
    suspend fun delete(path: String)
}
