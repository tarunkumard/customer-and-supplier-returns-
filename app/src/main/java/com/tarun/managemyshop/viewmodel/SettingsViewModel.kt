package com.tarun.managemyshop.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tarun.managemyshop.data.AppDataManager
import com.tarun.managemyshop.domain.model.Currency
import com.tarun.managemyshop.domain.model.SaveResult
import com.tarun.managemyshop.domain.model.ShopSettings
import com.tarun.managemyshop.domain.repository.ShopSettingsRepository
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: ShopSettingsRepository,
    private val dataManager: AppDataManager
) : ViewModel() {

    suspend fun load(): ShopSettings = repository.get()

    fun save(settings: ShopSettings, onResult: (SaveResult<Unit>) -> Unit) {
        viewModelScope.launch { onResult(repository.save(settings)) }
    }

    /** Applied immediately; every screen reads the currency from the same flow. */
    fun setCurrency(currency: Currency) {
        viewModelScope.launch { repository.setCurrency(currency) }
    }

    fun deleteAllData(onDone: () -> Unit) {
        viewModelScope.launch {
            dataManager.deleteAllData()
            onDone()
        }
    }
}
