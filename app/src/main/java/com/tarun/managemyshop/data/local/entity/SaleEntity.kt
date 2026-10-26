package com.tarun.managemyshop.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tarun.managemyshop.domain.model.PaymentMethod

@Entity(tableName = "sales", indices = [Index(value = ["invoiceNumber"], unique = true), Index("saleDate")])
data class SaleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceNumber: String,
    val customerName: String? = null,
    val saleDate: Long,
    val subtotalMinor: Long,
    val discountMinor: Long,
    val taxMinor: Long,
    val totalMinor: Long,
    val paymentMethod: PaymentMethod,
    val notes: String? = null
)
