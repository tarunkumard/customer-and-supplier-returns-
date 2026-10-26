package com.tarun.managemyshop

import android.app.Application
import com.tarun.managemyshop.data.AppContainer

class ManageMyShopApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this).also { it.initialize() }
    }
}
