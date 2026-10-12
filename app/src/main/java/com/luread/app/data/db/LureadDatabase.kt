package com.luread.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Database lokal Luread.
 *
 * Skema masih versi 1 tanpa migrasi. Ekspor skema aktif (`room.schemaLocation`)
 * supaya perubahan skema berikutnya bisa dibandingkan oleh Room dan diverifikasi
 * di CI (docs/testing.md).
 */
@Database(
    entities = [BookEntity::class, SessionEntity::class, AnnotationEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class LureadDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun sessionDao(): SessionDao
    abstract fun annotationDao(): AnnotationDao

    companion object {
        const val NAME = "luread.db"

        @Volatile
        private var instance: LureadDatabase? = null

        fun get(context: Context): LureadDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    LureadDatabase::class.java,
                    NAME,
                ).build().also { instance = it }
            }
    }
}
