package com.tarun.managemyshop.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.tarun.managemyshop.data.AppContainer

class ViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(AppViewModel::class.java) ->
            AppViewModel(container.settingsRepository) as T
        modelClass.isAssignableFrom(ProductViewModel::class.java) ->
            ProductViewModel(container.productRepository) as T
        modelClass.isAssignableFrom(DashboardViewModel::class.java) ->
            DashboardViewModel(
                container.settingsRepository,
                container.saleRepository,
                container.expenseRepository,
                container.productRepository
            ) as T
        modelClass.isAssignableFrom(ReportsViewModel::class.java) ->
            ReportsViewModel(container.saleRepository, container.expenseRepository, container.productRepository) as T
        modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
            SettingsViewModel(container.settingsRepository, container.dataManager) as T
        modelClass.isAssignableFrom(NewSaleViewModel::class.java) ->
            NewSaleViewModel(container.productRepository, container.saleRepository, container.settingsRepository) as T
        modelClass.isAssignableFrom(SalesViewModel::class.java) ->
            SalesViewModel(container.saleRepository) as T
        modelClass.isAssignableFrom(ExpenseViewModel::class.java) ->
            ExpenseViewModel(container.expenseRepository) as T
        else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    }
}
