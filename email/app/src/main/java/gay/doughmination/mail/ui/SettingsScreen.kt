package gay.doughmination.mail.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Switch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import gay.doughmination.mail.BuildConfig
import gay.doughmination.mail.Push
import gay.doughmination.mail.data.SwipeAction

/** Laid out like the web's settings page: one card per topic, under a small pink heading. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Section("Your name") {
                Note("People see this as the sender of mail you send. Leave it empty to send from just the address.")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { if (it.length <= 100) name = it },
                        placeholder = { Text("Your name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { vm.saveName(name) }, enabled = name.trim() != me?.name.orEmpty()) { Text("Save") }
                }
            }

            Section("Profile picture") {
                val picture = me?.picture
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AccountPicture(me, 56.dp)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Use your account's picture")
                        Note(
                            if (picture != null) "The one on your Doughmination account. Off shows your initial instead."
                            else "Your Doughmination account has no picture, so your initial shows.",
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = me?.shownPicture != null,
                        onCheckedChange = { on -> me?.let { vm.setPreferences(it.preferences.copy(usePicture = on)) } },
                        enabled = picture != null,
                    )
                }
            }

            Section("Swipe actions") {
                Note(
                    "What swiping a conversation in the list does, here and on the web. Read and unread only " +
                        "applies to received mail, and in the Bin a swipe right always restores.",
                )
                me?.let {
                    SwipePicker("Swipe right", it.swipeRight) { choice -> vm.setPreferences(it.preferences.copy(swipeRight = choice)) }
                    SwipePicker("Swipe left", it.swipeLeft) { choice -> vm.setPreferences(it.preferences.copy(swipeLeft = choice)) }
                }
            }

            Section("Your addresses") {
                Note("You receive and can send from each of these.")
                for (addr in me?.addresses.orEmpty()) Text("•  $addr")
                if (me?.addresses.isNullOrEmpty()) Text("None yet. Ask an admin to switch on a domain.")
            }

            Section("Notifications", Icons.Outlined.Notifications) {
                if (Push.available(context)) {
                    Note("Turn new-mail notifications on or off in Android's settings for the app.")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                            )
                        }) { Text("Notification settings") }
                        OutlinedButton(onClick = vm::testPush) { Text("Send a test") }
                    }
                } else {
                    Note("This build has no Firebase setup, so notifications are off.")
                }
            }

            Section("Privacy and terms", Icons.Outlined.PrivacyTip) {
                Note("What's stored about you and your mail, who can see it, and the rules for using the inbox.")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { openWeb("/privacy") }) {
                        Text("Privacy policy")
                        Icon(Icons.Outlined.ChevronRight, null, Modifier.size(ButtonDefaults.IconSize))
                    }
                    OutlinedButton(onClick = { openWeb("/terms") }) {
                        Text("Terms of service")
                        Icon(Icons.Outlined.ChevronRight, null, Modifier.size(ButtonDefaults.IconSize))
                    }
                }
            }

            Section("Account") {
                me?.let { Note("Signed in as ${it.username}.") }
                OutlinedButton(
                    onClick = { vm.signOut() },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Danger),
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Logout, null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Sign out")
                }
            }

            Text(
                "Inbox ${BuildConfig.VERSION_NAME}",
                Modifier.padding(start = 4.dp, bottom = 24.dp),
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
            )
        }
    }
}

private val sectionShape = RoundedCornerShape(16.dp)

/** A card like the web's settings sections: dark glass, a hairline border, an eyebrow heading. */
@Composable
private fun Section(title: String, icon: ImageVector? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(sectionShape)
            .background(Bg.copy(alpha = 0.72f))
            .border(1.dp, Line, sectionShape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(16.dp), tint = Accent)
                Spacer(Modifier.width(6.dp))
            }
            Text(title.uppercase(), style = Eyebrow)
        }
        content()
    }
}

/** A dropdown of the swipe actions, as an outlined field. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipePicker(label: String, selected: SwipeAction, onPick: (SwipeAction) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = selected.label,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (choice in SwipeAction.entries) {
                DropdownMenuItem(text = { Text(choice.label) }, onClick = { open = false; onPick(choice) })
            }
        }
    }
}

@Composable
private fun Note(text: String) = Text(text, style = MaterialTheme.typography.bodyMedium, color = Muted)
