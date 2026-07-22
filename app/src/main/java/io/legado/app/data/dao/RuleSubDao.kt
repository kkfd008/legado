package io.legado.app.data.dao

import androidx.room.*
import io.legado.app.data.entities.RuleSub
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleSubDao {

    @Query("select * from rule_subs order by customOrder")
    fun flowAll(): Flow<List<RuleSub>>

    @Query("select * from rule_subs order by customOrder")
    fun getAll(): List<RuleSub>

    @Query("select * from rule_subs where url = :url limit 1")
    fun findByUrl(url: String): RuleSub?

    @Query("select coalesce(max(customOrder), 0) from rule_subs")
    fun getMaxOrder(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(ruleSub: RuleSub)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg ruleSubs: RuleSub)

    @Update
    fun update(vararg ruleSub: RuleSub)

    @Delete
    fun delete(ruleSub: RuleSub)
}