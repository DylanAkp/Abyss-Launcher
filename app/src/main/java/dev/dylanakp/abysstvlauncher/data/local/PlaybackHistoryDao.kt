package dev.dylanakp.abysstvlauncher.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaybackHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: PlaybackHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<PlaybackHistoryEntity>)

    @Query("SELECT * FROM playback_history WHERE itemId = :itemId LIMIT 1")
    suspend fun getEntry(itemId: String): PlaybackHistoryEntity?

    @Query("SELECT * FROM playback_history ORDER BY lastPlayedTimestamp DESC")
    suspend fun getAll(): List<PlaybackHistoryEntity>

    @Query("SELECT * FROM playback_history ORDER BY lastPlayedTimestamp DESC")
    fun getAllFlow(): Flow<List<PlaybackHistoryEntity>>

    @Query("DELETE FROM playback_history WHERE itemId = :itemId")
    suspend fun delete(itemId: String)

    @Query("DELETE FROM playback_history")
    suspend fun clear()
}
