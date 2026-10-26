package com.tarun.managemyshop.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tarun.managemyshop.domain.model.ShopSettings
import com.tarun.managemyshop.domain.repository.ShopSettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** App-wide read-only view of the shop settings (currency, shop name, ...). */
class AppViewModel(settingsRepository: ShopSettingsRepository) : ViewModel() {

    val settings: StateFlow<ShopSettings> = settingsRepository.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShopSettings())
}
