package com.tarun.managemyshop

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.graphics.Color
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tarun.managemyshop.navigation.AppNavHost
import com.tarun.managemyshop.ui.theme.ManageMyShopTheme
import com.tarun.managemyshop.util.CurrencyFormatter
import com.tarun.managemyshop.util.LocalCurrencyFormatter
import com.tarun.managemyshop.viewmodel.AppViewModel
import com.tarun.managemyshop.viewmodel.ViewModelFactory

class MainActivity : ComponentActivity() {

    private val viewModelFactory by lazy { ViewModelFactory((application as ManageMyShopApp).container) }
    private val appViewModel: AppViewModel by viewModels { viewModelFactory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        setContent {
            val settings by appViewModel.settings.collectAsStateWithLifecycle()
            val formatter = remember(settings.currency) { CurrencyFormatter(settings.currency) }

            ManageMyShopTheme {
                CompositionLocalProvider(LocalCurrencyFormatter provides formatter) {
                    AppNavHost(
                        settings = settings,
                        viewModelFactory = viewModelFactory
                    )
                }
            }
        }
    }
}
