package com.tarun.managemyshop.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tarun.managemyshop.domain.model.PaymentMethod

@Entity(tableName = "expenses", indices = [Index("expenseDate")])
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val category: String = "",
    val amountMinor: Long,
    val expenseDate: Long,
    val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    val notes: String? = null
)
