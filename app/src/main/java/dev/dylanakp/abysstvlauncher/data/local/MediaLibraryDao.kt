package dev.dylanakp.abysstvlauncher.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface MediaLibraryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<MediaItemEntity>)

    @Query("SELECT * FROM cached_media_items WHERE librarySection = :section ORDER BY sortIndex ASC")
    suspend fun getItemsBySection(section: String): List<MediaItemEntity>

    @Query("DELETE FROM cached_media_items WHERE librarySection = :section")
    suspend fun deleteSection(section: String)

    @Query("DELETE FROM cached_media_items")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceSection(section: String, items: List<MediaItemEntity>) {
        deleteSection(section)
        if (items.isNotEmpty()) {
            insertAll(items)
        }
    }
}
