package com.cwynmn.books

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

@Entity(tableName = "books")
data class Book(
    @PrimaryKey val id: String,
    val title: String,
    val author: String,
    val filePath: String,
    val coverColor: Long,
    val chaptersJson: String,
    val progress: Float = 0f,
    val chapterIndex: Int = 0
)

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY title")
    fun observeAll(): kotlinx.coroutines.flow.Flow<List<Book>>

    @Query("SELECT * FROM books WHERE id=:id LIMIT 1")
    suspend fun get(id: String): Book?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(book: Book)

    @Query("UPDATE books SET progress=:progress, chapterIndex=:chapter WHERE id=:id")
    suspend fun progress(id: String, progress: Float, chapter: Int)

    @Query("DELETE FROM books WHERE id=:id")
    suspend fun delete(id: String)
}

@Database(entities = [Book::class], version = 1, exportSchema = false)
abstract class BooksDb : RoomDatabase() {
    abstract fun books(): BookDao

    companion object {
        @Volatile
        private var instance: BooksDb? = null

        fun get(context: android.content.Context) = instance
            ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    BooksDb::class.java,
                    "books.db"
                ).build().also { instance = it }
            }
    }
}
