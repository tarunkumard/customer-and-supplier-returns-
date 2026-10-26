package com.tarun.managemyshop.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tarun.managemyshop.domain.model.Currency
import com.tarun.managemyshop.domain.model.SaveResult
import com.tarun.managemyshop.domain.model.ShopSettings
import com.tarun.managemyshop.domain.repository.ShopSettingsRepository
import com.tarun.managemyshop.domain.validation.SettingsValidator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Shop settings and small app flags, persisted with Preferences DataStore. */
class ShopSettingsRepositoryImpl(context: Context) : ShopSettingsRepository {

    private val store = context.applicationContext.settingsDataStore

    private val preferences: Flow<Preferences> = store.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    override fun observe(): Flow<ShopSettings> = preferences.map { it.toSettings() }

    override suspend fun get(): ShopSettings = observe().first()

    override suspend fun save(settings: ShopSettings): SaveResult<Unit> {
        val cleaned = settings.copy(
            shopName = settings.shopName.trim(),
            shopAddress = settings.shopAddress?.trim()?.ifEmpty { null },
            shopPhone = settings.shopPhone?.trim()?.ifEmpty { null },
            invoicePrefix = settings.invoicePrefix.trim()
        )
        val errors = SettingsValidator.validate(cleaned)
        if (errors.isNotEmpty()) return SaveResult.Invalid(errors)

        store.edit { prefs ->
            prefs[Keys.ShopName] = cleaned.shopName
            cleaned.shopAddress?.let { prefs[Keys.ShopAddress] = it } ?: prefs.remove(Keys.ShopAddress)
            cleaned.shopPhone?.let { prefs[Keys.ShopPhone] = it } ?: prefs.remove(Keys.ShopPhone)
            prefs[Keys.CurrencyCode] = cleaned.currency.code
            prefs[Keys.TaxBasisPoints] = cleaned.taxBasisPoints
            prefs[Keys.InvoicePrefix] = cleaned.invoicePrefix
        }
        return SaveResult.Success(Unit)
    }

    override suspend fun setCurrency(currency: Currency) {
        store.edit { it[Keys.CurrencyCode] = currency.code }
    }

    override suspend fun isSampleDataHandled(): Boolean = preferences.first()[Keys.SampleDataHandled] == true

    override suspend fun markSampleDataHandled() {
        store.edit { it[Keys.SampleDataHandled] = true }
    }

    override suspend fun resetAll() {
        store.edit {
            it.clear()
            // Keep this set so sample products do not come back after the owner wipes everything.
            it[Keys.SampleDataHandled] = true
        }
    }

    private fun Preferences.toSettings(): ShopSettings {
        val defaults = ShopSettings()
        return ShopSettings(
            shopName = this[Keys.ShopName] ?: defaults.shopName,
            shopAddress = this[Keys.ShopAddress],
            shopPhone = this[Keys.ShopPhone],
            currency = Currency.fromCode(this[Keys.CurrencyCode]),
            taxBasisPoints = this[Keys.TaxBasisPoints] ?: defaults.taxBasisPoints,
            invoicePrefix = this[Keys.InvoicePrefix] ?: defaults.invoicePrefix
        )
    }

    private object Keys {
        val ShopName = stringPreferencesKey("shop_name")
        val ShopAddress = stringPreferencesKey("shop_address")
        val ShopPhone = stringPreferencesKey("shop_phone")
        val CurrencyCode = stringPreferencesKey("currency_code")
        val TaxBasisPoints = intPreferencesKey("tax_basis_points")
        val InvoicePrefix = stringPreferencesKey("invoice_prefix")
        val SampleDataHandled = booleanPreferencesKey("sample_data_handled")
    }
}
