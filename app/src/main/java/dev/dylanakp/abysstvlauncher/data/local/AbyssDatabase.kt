package dev.dylanakp.abysstvlauncher.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PlaybackHistoryEntity::class,
        MediaItemEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AbyssDatabase : RoomDatabase() {
    abstract fun playbackHistoryDao(): PlaybackHistoryDao
    abstract fun mediaLibraryDao(): MediaLibraryDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) = Unit
        }

        @Volatile
        private var instance: AbyssDatabase? = null

        fun getInstance(context: Context): AbyssDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AbyssDatabase::class.java,
                    "abyss_tv.db"
                ).addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true).build().also { instance = it }
            }
    }
}
