package com.tarun.managemyshop.data

import com.tarun.managemyshop.data.local.AppDatabase
import com.tarun.managemyshop.domain.repository.ShopSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Wipes everything the app stores on the device: database rows and saved settings. */
class AppDataManager(
    private val database: AppDatabase,
    private val settings: ShopSettingsRepository
) {
    suspend fun deleteAllData() = withContext(Dispatchers.IO) {
        database.clearAllTables()
        // Restart auto-increment ids so a wiped shop begins again at invoice 0001.
        runCatching { database.openHelper.writableDatabase.execSQL("DELETE FROM sqlite_sequence") }
        settings.resetAll()
    }
}
