package com.tarun.managemyshop.data

import android.content.Context
import com.tarun.managemyshop.data.local.AppDatabase
import com.tarun.managemyshop.data.repository.ExpenseRepositoryImpl
import com.tarun.managemyshop.data.repository.ProductRepositoryImpl
import com.tarun.managemyshop.data.repository.SaleRepositoryImpl
import com.tarun.managemyshop.data.preferences.ShopSettingsRepositoryImpl
import com.tarun.managemyshop.domain.repository.ExpenseRepository
import com.tarun.managemyshop.domain.repository.ProductRepository
import com.tarun.managemyshop.domain.repository.SaleRepository
import com.tarun.managemyshop.domain.repository.ShopSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Manual dependency container; keeps the app free of DI frameworks. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: AppDatabase by lazy { AppDatabase.create(appContext) }

    val settingsRepository: ShopSettingsRepository by lazy { ShopSettingsRepositoryImpl(appContext) }
    val productRepository: ProductRepository by lazy { ProductRepositoryImpl(database) }
    val saleRepository: SaleRepository by lazy { SaleRepositoryImpl(database, settingsRepository) }
    val expenseRepository: ExpenseRepository by lazy { ExpenseRepositoryImpl(database.expenseDao()) }

    val dataManager: AppDataManager by lazy { AppDataManager(database, settingsRepository) }

    fun initialize() {
        appScope.launch { DatabaseSeeder.seedIfNeeded(database, settingsRepository) }
    }
}
