@file:OptIn(ExperimentalLayoutApi::class)

package gay.doughmination.plural.ui

import gay.doughmination.design.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/** Sign-in, the profile, and the way into the admin and owner tools. */
@Composable
fun AccountScreen(vm: SystemViewModel) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (!vm.signedIn) {
            SignIn(vm)
            return@Column
        }
        val user = vm.user
        if (user == null) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }

        Panel {
            Text("ACCOUNT", style = Eyebrow)
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Avatar(user.avatarUrl, 96.dp, ring = Accent)
                Text(user.shownName, style = MaterialTheme.typography.headlineSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (user.isOwner) Badge("Owner")
                    if (user.isAdmin) Badge("Admin", accent = false)
                    if (user.isPet) Badge("Pet", accent = false)
                }
                Text("@${user.username}", color = Muted)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.open(Screen.EditProfile) }) {
                    Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp)); HGap(6); Text("Edit profile")
                }
                OutlinedButton(onClick = vm::signOut) {
                    Icon(Icons.AutoMirrored.Outlined.Logout, null, Modifier.size(18.dp)); HGap(6); Text("Sign out")
                }
            }
        }

        if (vm.isAdmin) AdminHub(vm)
    }
}

@Composable
private fun SignIn(vm: SystemViewModel) {
    val context = LocalContext.current
    Panel {
        Text("ACCOUNT", style = Eyebrow)
        Text("Sign in", style = MaterialTheme.typography.headlineSmall)
        Text("Use your SSO account to continue.", color = Muted)
        Button(onClick = { vm.startLogin(context) }, enabled = !vm.signingIn) {
            Icon(Icons.AutoMirrored.Outlined.Login, null, Modifier.size(18.dp))
            HGap(6)
            Text(if (vm.signingIn) "Signing in…" else "Sign in with SSO")
        }
        Text(
            "You'll be taken to your SSO provider in a browser tab, then brought right back here.",
            color = Muted,
            style = MaterialTheme.typography.bodySmall,
        )
    }
    Panel {
        Text("Everything on the Members, Relationships and Metrics tabs is public and works without signing in. Signing in adds the admin tools.", color = Muted, style = MaterialTheme.typography.bodyMedium)
    }
}

/** The website's /admin/dash and /owner/dash in one list. */
@Composable
private fun AdminHub(vm: SystemViewModel) {
    var confirmRefresh by remember { mutableStateOf(false) }

    PageHead("Admin", "Admin dashboard", "System management hub")
    Tile(Icons.Outlined.SwapHoriz, "Switch manager", "Manage fronting members") { vm.open(Screen.Switch) }
    Tile(Icons.Outlined.Psychology, "Mental health", "Update mental state") { vm.open(Screen.Mental) }
    Tile(Icons.Outlined.ChatBubbleOutline, "Status manager", "Set member statuses") { vm.open(Screen.Status) }
    Tile(Icons.Outlined.LocalOffer, "Tag manager", "Manage member tags") { vm.open(Screen.Tags) }
    if (vm.isOwner) {
        Tile(Icons.Outlined.Flag, "Pride manager", "Set member pride identities") { vm.open(Screen.Pride) }
        Tile(Icons.Outlined.FavoriteBorder, "Relationships", "Map who is dating who") { vm.open(Screen.ManageRelationships) }
        Tile(Icons.Outlined.Group, "User management", "Manage all users") { vm.open(Screen.Users) }
    }

    Panel(Modifier.border(1.dp, Danger.copy(alpha = 0.4f), RoundedCornerShape(16.dp))) {
        Text("[ danger ] Force refresh all clients", style = MaterialTheme.typography.titleMedium)
        Text(
            "Send a refresh command to everyone viewing the website or this app. Their pages reload immediately.",
            color = Muted,
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(
            onClick = { confirmRefresh = true },
            enabled = !vm.busy,
            colors = ButtonDefaults.buttonColors(containerColor = Danger),
        ) {
            Icon(Icons.Outlined.Refresh, null, Modifier.size(18.dp)); HGap(6); Text("Refresh all")
        }
    }

    if (confirmRefresh) {
        Confirm(
            title = "Refresh everyone?",
            text = "This will reload the website for ALL connected users. Are you sure?",
            action = "Refresh all",
            onDismiss = { confirmRefresh = false },
        ) {
            confirmRefresh = false
            vm.forceRefresh()
        }
    }
}

@Composable
private fun Tile(icon: ImageVector, title: String, description: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Raised)
            .border(1.dp, Line, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(AccentDim), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Accent)
        }
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** A yes/no question before something that can't be undone. */
@Composable
fun Confirm(title: String, text: String, action: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(action, color = Danger) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private val EmailPattern = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

/** The website's /user/profile/edit. */
@Composable
fun EditProfileScreen(vm: SystemViewModel) {
    val user = vm.user
    var displayName by rememberSaveable { mutableStateOf(user?.displayName.orEmpty()) }
    var avatarUrl by rememberSaveable { mutableStateOf(user?.avatarUrl.orEmpty()) }
    var email by rememberSaveable { mutableStateOf(user?.email.orEmpty()) }
    val emailOk = email.isBlank() || EmailPattern.matches(email.trim())

    Column(Modifier.fillMaxSize()) {
        BackBar("Edit profile", onBack = vm::back)
        if (user == null) return@Column
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Notice("Sign-in is handled by SSO. Your username and password are managed by your SSO provider — this page only controls your display profile.")
            Panel {
                Field("Avatar", "Paste a direct link to an image (square recommended)") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Avatar(avatarUrl.trim().ifBlank { null }, 56.dp, ring = Accent)
                        OutlinedTextField(
                            avatarUrl, { avatarUrl = it },
                            placeholder = { Text("https://example.com/avatar.png") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Field("Display name", "Leave blank to use username: @${user.username}") {
                    OutlinedTextField(displayName, { displayName = it }, placeholder = { Text("Enter display name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                Field("Contact email", "Optional contact address. Sign-in email lives with your SSO provider, not here.") {
                    OutlinedTextField(
                        email, { email = it },
                        placeholder = { Text("you@example.com") },
                        singleLine = true,
                        isError = !emailOk,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Button(onClick = { vm.saveProfile(displayName, avatarUrl, email) }, enabled = !vm.busy && emailOk) {
                    Text(if (vm.busy) "Saving..." else "Save changes")
                }
            }
        }
    }
}
