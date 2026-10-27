package com.tarun.managemyshop.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tarun.managemyshop.domain.model.ShopSettings
import com.tarun.managemyshop.ui.components.AppTopBar
import com.tarun.managemyshop.ui.screens.CustomerReturnDetailScreen
import com.tarun.managemyshop.ui.screens.CustomerReturnsScreen
import com.tarun.managemyshop.ui.screens.DashboardScreen
import com.tarun.managemyshop.ui.screens.ExpenseFormScreen
import com.tarun.managemyshop.ui.screens.ExpensesScreen
import com.tarun.managemyshop.ui.screens.InventoryScreen
import com.tarun.managemyshop.ui.screens.MoreScreen
import com.tarun.managemyshop.ui.screens.NewCustomerReturnScreen
import com.tarun.managemyshop.ui.screens.NewSaleScreen
import com.tarun.managemyshop.ui.screens.NewSupplierReturnScreen
import com.tarun.managemyshop.ui.screens.PrivacyPolicyScreen
import com.tarun.managemyshop.ui.screens.ProductDetailScreen
import com.tarun.managemyshop.ui.screens.ProductFormScreen
import com.tarun.managemyshop.ui.screens.ReportsScreen
import com.tarun.managemyshop.ui.screens.SaleDetailScreen
import com.tarun.managemyshop.ui.screens.SalesScreen
import com.tarun.managemyshop.ui.screens.SettingsScreen
import com.tarun.managemyshop.ui.screens.SupplierReturnDetailScreen
import com.tarun.managemyshop.ui.screens.SupplierReturnsScreen

@Composable
fun AppNavHost(
    settings: ShopSettings,
    viewModelFactory: ViewModelProvider.Factory,
    navController: NavHostController = rememberNavController()
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val tab = Destination.fromRoute(backStackEntry?.destination?.route)
    // Pushed screens (product form/detail) bring their own top bar and hide the tab chrome.
    val showTabChrome = tab != null || backStackEntry == null

    Scaffold(
        topBar = { if (showTabChrome) AppTopBar(title = (tab ?: Destination.Dashboard).title) },
        bottomBar = {
            if (showTabChrome) {
                NavigationBar {
                    Destination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = destination == tab,
                            onClick = { navController.navigateToTopLevel(destination) },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(destination.title) }
                        )
                    }
                }
            }
        },
        contentWindowInsets = if (showTabChrome) ScaffoldDefaults.contentWindowInsets else WindowInsets(0)
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Dashboard.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Destination.Dashboard.route) {
                DashboardScreen(
                    viewModel = viewModel(factory = viewModelFactory),
                    onNewSale = { navController.navigate(Routes.SALE_NEW) },
                    onOpenSale = { navController.navigate(Routes.saleDetail(it)) },
                    onOpenCustomerReturn = { navController.navigate(Routes.customerReturnDetail(it)) },
                    onOpenSupplierReturn = { navController.navigate(Routes.supplierReturnDetail(it)) },
                    onAddProduct = { navController.navigate(Routes.PRODUCT_NEW) },
                    onAddExpense = { navController.navigate(Routes.EXPENSE_NEW) },
                    onOpenProduct = { navController.navigate(Routes.productDetail(it)) },
                    onOpenInventory = { navController.navigateToTopLevel(Destination.Inventory) }
                )
            }
            composable(Destination.Inventory.route) {
                InventoryScreen(
                    viewModel = viewModel(factory = viewModelFactory),
                    onAddProduct = { navController.navigate(Routes.PRODUCT_NEW) },
                    onOpenProduct = { navController.navigate(Routes.productDetail(it)) }
                )
            }
            composable(Destination.Sales.route) {
                SalesScreen(
                    viewModel = viewModel(factory = viewModelFactory),
                    onNewSale = { navController.navigate(Routes.SALE_NEW) },
                    onOpenSale = { navController.navigate(Routes.saleDetail(it)) }
                )
            }
            composable(Destination.Expenses.route) {
                ExpensesScreen(
                    viewModel = viewModel(factory = viewModelFactory),
                    onAddExpense = { navController.navigate(Routes.EXPENSE_NEW) },
                    onOpenExpense = { navController.navigate(Routes.expenseEdit(it)) }
                )
            }
            composable(Destination.More.route) {
                MoreScreen(
                    onOpenCustomerReturns = { navController.navigate(Routes.CUSTOMER_RETURNS) },
                    onOpenSupplierReturns = { navController.navigate(Routes.SUPPLIER_RETURNS) },
                    onOpenReports = { navController.navigate(Routes.REPORTS) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) }
                )
            }

            composable(Routes.EXPENSE_NEW) {
                ExpenseFormScreen(
                    expenseId = null,
                    viewModel = viewModel(factory = viewModelFactory),
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                Routes.EXPENSE_EDIT,
                arguments = listOf(navArgument(Routes.EXPENSE_ID) { type = NavType.LongType })
            ) { entry ->
                ExpenseFormScreen(
                    expenseId = entry.arguments!!.getLong(Routes.EXPENSE_ID),
                    viewModel = viewModel(factory = viewModelFactory),
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.REPORTS) {
                ReportsScreen(
                    viewModel = viewModel(factory = viewModelFactory),
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    viewModel = viewModel(factory = viewModelFactory),
                    onBack = { navController.popBackStack() },
                    onOpenPrivacy = { navController.navigate(Routes.PRIVACY) }
                )
            }
            composable(Routes.PRIVACY) {
                PrivacyPolicyScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.SALE_NEW) {
                NewSaleScreen(
                    viewModel = viewModel(factory = viewModelFactory),
                    onBack = { navController.popBackStack() },
                    onViewInvoice = { id ->
                        navController.navigate(Routes.saleDetail(id)) {
                            popUpTo(Routes.SALE_NEW) { inclusive = true }
                        }
                    }
                )
            }
            composable(
                Routes.SALE_DETAIL,
                arguments = listOf(navArgument(Routes.SALE_ID) { type = NavType.LongType })
            ) { entry ->
                SaleDetailScreen(
                    saleId = entry.arguments!!.getLong(Routes.SALE_ID),
                    shop = settings,
                    viewModel = viewModel(factory = viewModelFactory),
                    onBack = { navController.popBackStack() },
                    onCreateReturn = { navController.navigate(Routes.customerReturnNew(it)) },
                    onOpenReturn = { navController.navigate(Routes.customerReturnDetail(it)) }
                )
            }
            composable(Routes.CUSTOMER_RETURNS) {
                CustomerReturnsScreen(
                    viewModel = viewModel(factory = viewModelFactory),
                    onBack = { navController.popBackStack() },
                    onOpenReturn = { navController.navigate(Routes.customerReturnDetail(it)) },
                    onCreateReturnForSale = { navController.navigate(Routes.customerReturnNew(it)) }
                )
            }
            composable(
                Routes.CUSTOMER_RETURN_NEW,
                arguments = listOf(navArgument(Routes.SALE_ID) { type = NavType.LongType })
            ) { entry ->
                NewCustomerReturnScreen(
                    saleId = entry.arguments!!.getLong(Routes.SALE_ID),
                    viewModel = viewModel(factory = viewModelFactory),
                    onBack = { navController.popBackStack() },
                    onCreated = { id ->
                        navController.navigate(Routes.customerReturnDetail(id)) {
                            popUpTo(Routes.CUSTOMER_RETURN_NEW) { inclusive = true }
                        }
                    }
                )
            }
            composable(
                Routes.CUSTOMER_RETURN_DETAIL,
                arguments = listOf(navArgument(Routes.RETURN_ID) { type = NavType.LongType })
            ) { entry ->
                CustomerReturnDetailScreen(
                    returnId = entry.arguments!!.getLong(Routes.RETURN_ID),
                    shop = settings,
                    viewModel = viewModel(factory = viewModelFactory),
                    onBack = { navController.popBackStack() },
                    onOpenSale = { navController.navigate(Routes.saleDetail(it)) }
                )
            }
            composable(Routes.SUPPLIER_RETURNS) {
                SupplierReturnsScreen(
                    viewModel = viewModel(factory = viewModelFactory),
                    onBack = { navController.popBackStack() },
                    onOpenReturn = { navController.navigate(Routes.supplierReturnDetail(it)) },
                    onNewReturn = { navController.navigate(Routes.SUPPLIER_RETURN_NEW) }
                )
            }
            composable(Routes.SUPPLIER_RETURN_NEW) {
                NewSupplierReturnScreen(
                    viewModel = viewModel(factory = viewModelFactory),
                    onBack = { navController.popBackStack() },
                    onCreated = { id ->
                        navController.navigate(Routes.supplierReturnDetail(id)) {
                            popUpTo(Routes.SUPPLIER_RETURN_NEW) { inclusive = true }
                        }
                    }
                )
            }
            composable(
                Routes.SUPPLIER_RETURN_DETAIL,
                arguments = listOf(navArgument(Routes.RETURN_ID) { type = NavType.LongType })
            ) { entry ->
                SupplierReturnDetailScreen(
                    returnId = entry.arguments!!.getLong(Routes.RETURN_ID),
                    shop = settings,
                    viewModel = viewModel(factory = viewModelFactory),
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.PRODUCT_NEW) {
                ProductFormScreen(
                    productId = null,
                    viewModel = viewModel(factory = viewModelFactory),
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                Routes.PRODUCT_EDIT,
                arguments = listOf(navArgument(Routes.PRODUCT_ID) { type = NavType.LongType })
            ) { entry ->
                ProductFormScreen(
                    productId = entry.arguments!!.getLong(Routes.PRODUCT_ID),
                    viewModel = viewModel(factory = viewModelFactory),
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                Routes.PRODUCT_DETAIL,
                arguments = listOf(navArgument(Routes.PRODUCT_ID) { type = NavType.LongType })
            ) { entry ->
                val id = entry.arguments!!.getLong(Routes.PRODUCT_ID)
                ProductDetailScreen(
                    productId = id,
                    viewModel = viewModel(factory = viewModelFactory),
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(Routes.productEdit(id)) }
                )
            }
        }
    }
}

private fun NavHostController.navigateToTopLevel(destination: Destination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
