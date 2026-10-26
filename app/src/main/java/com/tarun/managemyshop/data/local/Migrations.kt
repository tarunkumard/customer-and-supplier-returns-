package com.tarun.managemyshop.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration plan for future schema changes. Never use fallbackToDestructiveMigration:
 * this is the shop owner's only copy of their data.
 *
 * To change the schema:
 *  1. Bump `version` in [AppDatabase].
 *  2. Add a `Migration(from, to)` here and append it to [ALL]. Example:
 *       val MIGRATION_1_2 = object : Migration(1, 2) {
 *           override fun migrate(db: SupportSQLiteDatabase) {
 *               db.execSQL("ALTER TABLE products ADD COLUMN unit TEXT NOT NULL DEFAULT 'pcs'")
 *           }
 *       }
 *  3. Build, then commit the generated JSON in app/schemas/ (one file per version) so the
 *     migration can be verified against the real before/after schemas.
 */
object Migrations {

    /** v2: cost snapshot on sale items (for profit) and the stock adjustment audit trail. */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Rows that predate cost tracking get 0 (unknown).
            db.execSQL("ALTER TABLE `sale_items` ADD COLUMN `unitCostMinor` INTEGER NOT NULL DEFAULT 0")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `stock_adjustments` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `productId` INTEGER NOT NULL, " +
                    "`delta` INTEGER NOT NULL, `quantityAfter` INTEGER NOT NULL, `reason` TEXT NOT NULL, " +
                    "`note` TEXT, `createdAt` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`productId`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_stock_adjustments_productId` ON `stock_adjustments` (`productId`)"
            )
        }
    }

    /** v3: shop settings moved from Room to DataStore, so the old table is dropped. */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS `shop_settings`")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
}
