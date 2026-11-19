package io.legado.app.data.entities

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

/**
 * 标签分组。groupId 为普通递增整数（允许 0），但 0 保留给「未分组」语义，
 * 因此实际新建分组从 1 开始分配。
 */
@Parcelize
@Entity(tableName = "tag_group")
data class BookTagGroup(
    @PrimaryKey
    val groupId: Long = 0,
    var name: String = "",
    var order: Int = 0
) : Parcelable {

    override fun hashCode(): Int {
        return groupId.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other is BookTagGroup) {
            return other.groupId == groupId
                    && other.name == name
                    && other.order == order
        }
        return false
    }
}
