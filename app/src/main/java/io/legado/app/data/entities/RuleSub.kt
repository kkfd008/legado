package io.legado.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rule_subs")
data class RuleSub(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0,
    var type: Int = 0,
    var name: String = "",
    var url: String = "",
    var customOrder: Int = 0
)