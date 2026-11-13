package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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

    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun update(vararg bookTag: BookTag)

    @Query("update book_tags set hot = :hot where tagId in (:ids)")
    fun updateHot(ids: List<Long>, hot: Boolean)

    @Delete
    fun delete(bookTag: BookTag)

    @Query("delete from book_tags where tagId = :tagId")
    fun delete(tagId: Long)

    /**
     * 删除标签时同步清除书籍上的对应标签位。
     * tagId 为 2 的幂，该位存在时减法等价于按位清除（与 BookDao.removeGroup 口径一致）。
     */
    @Query("update books set tags = tags - :tagId where tags & :tagId > 0")
    fun removeTagFromBooks(tagId: Long)

    /** 只保留标签库中仍存在的位 */
    @Query("update books set tags = tags & :validMask where tags != (tags & :validMask)")
    fun clearInvalidTagsFromBooks(validMask: Long)

    @Query("select count(*) from books where tags != (tags & :validMask)")
    fun countInvalidTagBooks(validMask: Long): Int

    /**
     * 删除标签并清理所有书籍上的该标签位，放在同一事务内。
     * 不清理的话，该位会在新建标签复用 tagId 时被旧书"继承"。
     */
    @Transaction
    fun deleteAndClearBooks(vararg bookTag: BookTag) {
        bookTag.forEach {
            removeTagFromBooks(it.tagId)
            delete(it)
        }
    }

    @Transaction
    fun deleteAndClearBooks(tagId: Long) {
        removeTagFromBooks(tagId)
        delete(tagId)
    }

    /** 清理所有书籍中已不存在于标签库的标签位，返回被清理的书籍数 */
    @Transaction
    fun clearInvalidTags(): Int {
        val validMask = all().fold(0L) { mask, tag -> mask or tag.tagId }
        val count = countInvalidTagBooks(validMask)
        clearInvalidTagsFromBooks(validMask)
        return count
    }

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