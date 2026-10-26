package com.tarun.managemyshop.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.tarun.managemyshop.data.local.dao.ExpenseDao
import com.tarun.managemyshop.data.local.dao.ProductDao
import com.tarun.managemyshop.data.local.dao.ReturnDao
import com.tarun.managemyshop.data.local.dao.SaleDao
import com.tarun.managemyshop.data.local.dao.StockAdjustmentDao
import com.tarun.managemyshop.data.local.dao.StockMovementDao
import com.tarun.managemyshop.data.local.entity.CustomerReturnEntity
import com.tarun.managemyshop.data.local.entity.CustomerReturnItemEntity
import com.tarun.managemyshop.data.local.entity.ExpenseEntity
import com.tarun.managemyshop.data.local.entity.ProductEntity
import com.tarun.managemyshop.data.local.entity.SaleEntity
import com.tarun.managemyshop.data.local.entity.SaleItemEntity
import com.tarun.managemyshop.data.local.entity.StockAdjustmentEntity
import com.tarun.managemyshop.data.local.entity.StockMovementEntity
import com.tarun.managemyshop.data.local.entity.SupplierReturnEntity
import com.tarun.managemyshop.data.local.entity.SupplierReturnItemEntity

@Database(
    entities = [
        ProductEntity::class,
        SaleEntity::class,
        SaleItemEntity::class,
        ExpenseEntity::class,
        StockAdjustmentEntity::class,
        CustomerReturnEntity::class,
        CustomerReturnItemEntity::class,
        SupplierReturnEntity::class,
        SupplierReturnItemEntity::class,
        StockMovementEntity::class
    ],
    version = 4,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun saleDao(): SaleDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun stockAdjustmentDao(): StockAdjustmentDao
    abstract fun returnDao(): ReturnDao
    abstract fun stockMovementDao(): StockMovementDao

    companion object {
        const val NAME = "manage_my_shop.db"

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .addMigrations(*Migrations.ALL)
                .build()
    }
}
