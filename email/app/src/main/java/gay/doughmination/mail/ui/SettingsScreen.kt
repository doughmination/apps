package gay.doughmination.mail.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import gay.doughmination.mail.BuildConfig
import gay.doughmination.mail.Push

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MailViewModel) {
    val me = vm.me
    val context = LocalContext.current
    var name by rememberSaveable(me?.name) { mutableStateOf(me?.name.orEmpty()) }
    val openWeb = { path: String ->
        CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(BuildConfig.INBOX_URL + path))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
                },
            )
        },
        snackbarHost = { SnackbarHost(LocalSnackbar.current) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            Column(Modifier.padding(16.dp)) {
                Text("Your name", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Shown on mail you send. Leave it empty to send from the bare address.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 100) name = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = { vm.saveName(name) }, enabled = name.trim() != me?.name.orEmpty()) { Text("Save") }
            }
            HorizontalDivider()
            Column(Modifier.padding(16.dp)) {
                Text("Your addresses", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                for (addr in me?.addresses.orEmpty()) Text(addr, color = Muted)
                if (me?.addresses.isNullOrEmpty()) Text("None yet. Ask an admin to switch on a domain.", color = Muted)
            }
            HorizontalDivider()
            if (Push.available(context)) {
                ListItem(
                    headlineContent = { Text("Notification settings") },
                    supportingContent = { Text("Turn new-mail notifications on or off") },
                    leadingContent = { Icon(Icons.Outlined.Notifications, null) },
                    modifier = Modifier.clickable {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                        )
                    },
                )
                ListItem(
                    headlineContent = { Text("Send a test notification") },
                    leadingContent = { Icon(Icons.Outlined.NotificationsActive, null) },
                    modifier = Modifier.clickable { vm.testPush() },
                )
            } else {
                ListItem(
                    headlineContent = { Text("Notifications") },
                    supportingContent = { Text("This build has no Firebase setup, so notifications are off.") },
                    leadingContent = { Icon(Icons.Outlined.NotificationsOff, null) },
                )
            }
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Privacy policy") },
                leadingContent = { Icon(Icons.Outlined.PrivacyTip, null) },
                modifier = Modifier.clickable { openWeb("/privacy") },
            )
            ListItem(
                headlineContent = { Text("Terms of service") },
                leadingContent = { Icon(Icons.Outlined.Description, null) },
                modifier = Modifier.clickable { openWeb("/terms") },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Sign out", color = Danger) },
                supportingContent = { me?.let { Text("Signed in as ${it.username}") } },
                leadingContent = { Icon(Icons.AutoMirrored.Outlined.Logout, null, tint = Danger) },
                modifier = Modifier.clickable { vm.signOut() },
            )
            Text(
                "Inbox ${BuildConfig.VERSION_NAME}",
                Modifier.padding(16.dp),
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
            )
        }
    }
}
