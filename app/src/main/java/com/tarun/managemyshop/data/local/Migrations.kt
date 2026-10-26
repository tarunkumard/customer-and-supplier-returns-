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

    /** v4: customer / supplier returns and the stock movement ledger (existing sales are back-filled). */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `customer_returns` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `returnNumber` TEXT NOT NULL, " +
                    "`originalSaleId` INTEGER NOT NULL, `originalInvoiceNumber` TEXT NOT NULL, " +
                    "`returnDate` INTEGER NOT NULL, `customerName` TEXT, `refundMethod` TEXT NOT NULL, " +
                    "`reason` TEXT NOT NULL, `notes` TEXT, `subtotalRefundMinor` INTEGER NOT NULL, " +
                    "`taxRefundMinor` INTEGER NOT NULL, `totalRefundMinor` INTEGER NOT NULL)"
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_customer_returns_returnNumber` ON `customer_returns` (`returnNumber`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_customer_returns_originalSaleId` ON `customer_returns` (`originalSaleId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_customer_returns_returnDate` ON `customer_returns` (`returnDate`)")

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `customer_return_items` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `customerReturnId` INTEGER NOT NULL, " +
                    "`originalSaleItemId` INTEGER NOT NULL, `productId` INTEGER NOT NULL, " +
                    "`productNameSnapshot` TEXT NOT NULL, `quantityReturned` INTEGER NOT NULL, " +
                    "`unitPriceMinor` INTEGER NOT NULL, `unitCostMinor` INTEGER NOT NULL, " +
                    "`lineRefundMinor` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`customerReturnId`) REFERENCES `customer_returns`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_customer_return_items_customerReturnId` ON `customer_return_items` (`customerReturnId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_customer_return_items_originalSaleItemId` ON `customer_return_items` (`originalSaleItemId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_customer_return_items_productId` ON `customer_return_items` (`productId`)")

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `supplier_returns` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `returnNumber` TEXT NOT NULL, " +
                    "`returnDate` INTEGER NOT NULL, `supplierName` TEXT, `reason` TEXT NOT NULL, " +
                    "`notes` TEXT, `totalSupplierCreditMinor` INTEGER NOT NULL)"
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_supplier_returns_returnNumber` ON `supplier_returns` (`returnNumber`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_supplier_returns_returnDate` ON `supplier_returns` (`returnDate`)")

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `supplier_return_items` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `supplierReturnId` INTEGER NOT NULL, " +
                    "`productId` INTEGER NOT NULL, `productNameSnapshot` TEXT NOT NULL, " +
                    "`quantityReturned` INTEGER NOT NULL, `unitCostMinor` INTEGER NOT NULL, " +
                    "`lineSupplierCreditMinor` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`supplierReturnId`) REFERENCES `supplier_returns`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_supplier_return_items_supplierReturnId` ON `supplier_return_items` (`supplierReturnId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_supplier_return_items_productId` ON `supplier_return_items` (`productId`)")

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `stock_movements` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `productId` INTEGER NOT NULL, " +
                    "`movementType` TEXT NOT NULL, `quantityChange` INTEGER NOT NULL, `referenceId` INTEGER, " +
                    "`note` TEXT, `createdAt` INTEGER NOT NULL)"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_movements_productId` ON `stock_movements` (`productId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_movements_createdAt` ON `stock_movements` (`createdAt`)")

            // Back-fill the ledger with past sales and manual adjustments so it is complete from day one.
            db.execSQL(
                "INSERT INTO `stock_movements` (`productId`, `movementType`, `quantityChange`, `referenceId`, `note`, `createdAt`) " +
                    "SELECT i.`productId`, 'SALE', -i.`quantity`, s.`id`, 'Sale ' || s.`invoiceNumber`, s.`saleDate` " +
                    "FROM `sale_items` i JOIN `sales` s ON s.`id` = i.`saleId`"
            )
            db.execSQL(
                "INSERT INTO `stock_movements` (`productId`, `movementType`, `quantityChange`, `referenceId`, `note`, `createdAt`) " +
                    "SELECT `productId`, CASE WHEN `delta` >= 0 THEN 'STOCK_INCREASE' ELSE 'STOCK_DECREASE' END, " +
                    "`delta`, `id`, `reason`, `createdAt` FROM `stock_adjustments`"
            )
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
}
