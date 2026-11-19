package io.legado.app.data.entities

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = "book_tags")
data class BookTag(
    @PrimaryKey
    val tagId: Long = 0b1,
    var name: String = "",
    var order: Int = 0,
    @ColumnInfo(defaultValue = "0")
    var hot: Boolean = false,
    /** 所属标签分组 ID，0 表示未分组 */
    @ColumnInfo(defaultValue = "0")
    var groupId: Long = 0L
) : Parcelable {

    override fun hashCode(): Int {
        return tagId.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other is BookTag) {
            return other.tagId == tagId
                    && other.name == name
                    && other.order == order
                    && other.hot == hot
                    && other.groupId == groupId
        }
        return false
    }
}
