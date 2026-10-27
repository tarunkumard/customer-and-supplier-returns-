package com.tarun.managemyshop.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tarun.managemyshop.ui.components.InfoCard
import com.tarun.managemyshop.ui.components.SubScreenTopBar

@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    Scaffold(topBar = { SubScreenTopBar("Privacy Policy", onBack) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            InfoCard {
                Text(
                    "Manage My Shop stores shop data only on this device. It does not collect, transmit, " +
                        "sell, or share personal data. Uninstalling the app or using Delete All App Data " +
                        "permanently removes locally stored information.",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            InfoCard(title = "In plain words") {
                Bullet("Your products, sales, returns, expenses and settings live in the app's private storage on this phone.")
                Bullet("The app has no account, no advertising, no analytics and no internet access.")
                Bullet("It asks for no permissions such as contacts, location, camera or files.")
                Bullet("A PDF invoice or receipt leaves the app only when you choose Share or Download and pick where it goes.")
                Bullet("Cloud backup is switched off for this app, so nothing is copied to a cloud account automatically.")
                Bullet("There is no copy of your data anywhere else. If you delete the app data or uninstall, it cannot be recovered.")
            }
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Text("•  $text", style = MaterialTheme.typography.bodyMedium)
}
