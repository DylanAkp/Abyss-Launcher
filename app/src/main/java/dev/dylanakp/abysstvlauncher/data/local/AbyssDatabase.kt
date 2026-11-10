package dev.dylanakp.abysstvlauncher.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        PlaybackHistoryEntity::class,
        MediaItemEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AbyssDatabase : RoomDatabase() {
    abstract fun playbackHistoryDao(): PlaybackHistoryDao
    abstract fun mediaLibraryDao(): MediaLibraryDao

    companion object {
        @Volatile
        private var instance: AbyssDatabase? = null

        fun getInstance(context: Context): AbyssDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AbyssDatabase::class.java,
                    "abyss_tv.db"
                ).fallbackToDestructiveMigration(dropAllTables = true).build().also { instance = it }
            }
    }
}
