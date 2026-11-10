package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.legado.app.data.entities.BookTag
import kotlinx.coroutines.flow.Flow

@Dao
interface BookTagDao {
    @Query("select * from book_tags order by `order`")
    fun flowSelect(): Flow<List<BookTag>>

    @Query("select * from book_tags order by `order`")
    fun all(): List<BookTag>

    @Query("select * from book_tags where hot = 1 order by `order` desc")
    fun flowHot(): Flow<List<BookTag>>

    @Query("select name from book_tags order by `order`")
    fun getTagNames(): List<String>

    @Query("select name from book_tags where tagId & :ids > 0 order by `order`")
    fun getTagNames(ids: Long): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(bookTag: BookTag)

    @Update
    fun update(bookTag: BookTag)

    @Query("update book_tags set hot = :hot where tagId in (:ids)")
    fun updateHot(ids: List<Long>, hot: Boolean)

    @Delete
    fun delete(bookTag: BookTag)

    @Query("delete from book_tags where tagId = :tagId")
    fun delete(tagId: Long)

    @Query("select max(`order`) from book_tags")
    fun maxOrder(): Int?

    @Query("select min(`order`) from book_tags")
    fun minOrder(): Int?

    @Query("select max(tagId) from book_tags")
    fun maxTagId(): Long?

    @Query("select * from book_tags where name = :name limit 1")
    fun getByName(name: String): BookTag?

    @get:Query("SELECT sum(tagId) FROM book_tags")
    val idsSum: Long
}