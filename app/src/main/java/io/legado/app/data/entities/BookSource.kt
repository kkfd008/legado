package io.legado.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "book_sources")
data class BookSource(
    @PrimaryKey
    var bookSourceUrl: String = "",
    var bookSourceName: String = "",
    var bookSourceGroup: String? = null,
    var bookSourceComment: String? = null,
    var customOrder: Int = 0,
    var enabled: Boolean = true,
    var concurrentRate: String? = null,
    var loginUi: String? = null,
    var loginCheckJs: String? = null,
    var respondTime: Long = 180000,
    var bookSourceType: Int = 0,
    var header: String? = null,
    var bookSourceIcon: String? = null,
    var lastUpdateTime: Long = 0,
    var weight: Int = 0
)