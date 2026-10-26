package com.tarun.managemyshop.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.tarun.managemyshop.data.local.entity.ExpenseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Query(
        "SELECT * FROM expenses WHERE (:query = '' OR title LIKE '%' || :query || '%' " +
            "OR category LIKE '%' || :query || '%' OR notes LIKE '%' || :query || '%') " +
            "AND expenseDate >= :start AND expenseDate < :end ORDER BY expenseDate DESC, id DESC"
    )
    fun observeFiltered(query: String, start: Long, end: Long): Flow<List<ExpenseEntity>>

    @Query("SELECT COALESCE(SUM(amountMinor), 0) FROM expenses WHERE expenseDate >= :start AND expenseDate < :end")
    fun observeTotalBetween(start: Long, end: Long): Flow<Long>

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getById(id: Long): ExpenseEntity?

    @Insert
    suspend fun insert(expense: ExpenseEntity): Long

    @Update
    suspend fun update(expense: ExpenseEntity)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun delete(id: Long)
}
