package com.luread.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY lastOpenedAt DESC, addedAt DESC")
    fun observeAll(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    fun observeById(id: Int): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun byId(id: Int): BookEntity?

    @Query("SELECT * FROM books WHERE localPath IS NOT NULL")
    suspend fun downloaded(): List<BookEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(book: BookEntity)

    @Query(
        "UPDATE books SET progress = :progress, locationJson = :locationJson, " +
            "lastOpenedAt = :openedAt, finished = :finished WHERE id = :id",
    )
    suspend fun updateReadingState(
        id: Int,
        progress: Float,
        locationJson: String?,
        openedAt: Long,
        finished: Boolean,
    )

    @Query("UPDATE books SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Int, favorite: Boolean)

    @Query("UPDATE books SET downloadState = :state, downloadedBytes = :done, totalBytes = :total WHERE id = :id")
    suspend fun updateDownload(id: Int, state: String, done: Long, total: Long)

    @Query("UPDATE books SET localPath = :path, contentType = :mime, downloadState = :state WHERE id = :id")
    suspend fun setLocalFile(id: Int, path: String?, mime: String?, state: String)

    @Query("UPDATE books SET localPath = NULL, downloadState = :state WHERE id = :id")
    suspend fun clearLocalFile(id: Int, state: String)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun delete(id: Int)

    @Query("DELETE FROM books WHERE localPath = :path")
    suspend fun deleteByPath(path: String)

    @Query("SELECT COUNT(*) FROM books WHERE localPath IS NOT NULL")
    fun observeDownloadedCount(): Flow<Int>

    @Query("SELECT * FROM books WHERE localPath IS NOT NULL ORDER BY lastOpenedAt DESC")
    fun observeDownloaded(): Flow<List<BookEntity>>
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY startedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 200): Flow<List<SessionEntity>>

    @Query(
        "INSERT INTO sessions (bookId, bookTitle, startedAt, endedAt, progressBefore, progressAfter) " +
            "VALUES (:bookId, :title, :startedAt, :endedAt, :before, :after)",
    )
    suspend fun insert(
        bookId: Int,
        title: String,
        startedAt: Long,
        endedAt: Long,
        before: Float,
        after: Float,
    )

    @Query(
        "SELECT COALESCE(SUM(endedAt - startedAt), 0) FROM sessions " +
            "WHERE startedAt >= :since",
    )
    fun observeMillisSince(since: Long): Flow<Long>

    @Query("DELETE FROM sessions")
    suspend fun clear()
}

@Dao
interface AnnotationDao {
    @Query("SELECT * FROM annotations WHERE bookId = :bookId ORDER BY createdAt DESC")
    fun observeForBook(bookId: Int): Flow<List<AnnotationEntity>>

    @Query("SELECT * FROM annotations ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<AnnotationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(annotation: AnnotationEntity): Long

    @Query("DELETE FROM annotations WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM annotations WHERE bookId = :bookId")
    suspend fun deleteForBook(bookId: Int)

    @Query("DELETE FROM annotations")
    suspend fun clear()
}
