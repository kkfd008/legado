package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import io.legado.app.data.entities.BookTagGroup
import kotlinx.coroutines.flow.Flow

@Dao
interface BookTagGroupDao {

    @Query("select * from tag_group order by `order` desc")
    fun flowAll(): Flow<List<BookTagGroup>>

    @Query("select * from tag_group order by `order` desc")
    fun all(): List<BookTagGroup>

    @Query("select * from tag_group where groupId = :groupId")
    fun getById(groupId: Long): BookTagGroup?

    @Query("select * from tag_group where name = :name limit 1")
    fun getByName(name: String): BookTagGroup?

    @Query("select max(`order`) from tag_group")
    fun maxOrder(): Int?

    @Query("select max(groupId) from tag_group")
    fun maxId(): Long?

    @Query("select count(*) from tag_group")
    fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg group: BookTagGroup)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun update(vararg group: BookTagGroup)

    @Delete
    fun delete(group: BookTagGroup)

    /** 分组删除后，其下标签回到「未分组」 */
    @Query("update book_tags set groupId = 0 where groupId = :groupId")
    fun clearGroupOfTags(groupId: Long)

    @Transaction
    fun deleteAndClearTags(group: BookTagGroup) {
        clearGroupOfTags(group.groupId)
        delete(group)
    }

    /** 下一个可用分组 ID：0 保留给未分组，从 1 开始递增 */
    fun getNextId(): Long = (maxId() ?: 0L) + 1L
}
