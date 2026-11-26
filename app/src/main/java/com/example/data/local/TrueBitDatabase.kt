package com.example.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackCacheDao {
    @Query("SELECT * FROM cached_tracks ORDER BY title ASC")
    fun observeAllTracks(): Flow<List<CachedTrackEntity>>

    @Query("SELECT * FROM cached_tracks WHERE fileUri = :uri AND lastModified = :lastModified AND fileSize = :fileSize LIMIT 1")
    suspend fun findCachedTrack(uri: String, lastModified: Long, fileSize: Long): CachedTrackEntity?

    @Query("SELECT * FROM cached_tracks WHERE fileUri = :uri LIMIT 1")
    suspend fun getTrackByUri(uri: String): CachedTrackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTrack(entity: CachedTrackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTracks(entities: List<CachedTrackEntity>)

    @Query("UPDATE cached_tracks SET isQuarantined = :quarantined WHERE fileUri = :uri")
    suspend fun setQuarantinedState(uri: String, quarantined: Boolean)

    @Query("DELETE FROM cached_tracks WHERE fileUri = :uri")
    suspend fun deleteTrackByUri(uri: String)

    @Query("DELETE FROM cached_tracks")
    suspend fun clearAllTracks()
}

@Dao
interface TrashAuditDao {
    @Query("SELECT * FROM trash_audit_entries ORDER BY quarantinedAtMs DESC")
    fun observeAllTrashEntries(): Flow<List<TrashAuditEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrashEntry(entry: TrashAuditEntity)

    @Query("UPDATE trash_audit_entries SET isPermanentlyDeleted = 1 WHERE trashId = :trashId")
    suspend fun markPermanentlyDeleted(trashId: String)

    @Query("DELETE FROM trash_audit_entries WHERE trashId = :trashId")
    suspend fun removeTrashEntry(trashId: String)

    @Query("DELETE FROM trash_audit_entries WHERE isPermanentlyDeleted = 1")
    suspend fun clearDeletedHistory()
}

@Database(
    entities = [CachedTrackEntity::class, TrashAuditEntity::class],
    version = 1,
    exportSchema = false
)
abstract class TrueBitDatabase : RoomDatabase() {
    abstract fun trackCacheDao(): TrackCacheDao
    abstract fun trashAuditDao(): TrashAuditDao
}
