@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package gay.doughmination.plural.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gay.doughmination.plural.data.Api
import gay.doughmination.plural.data.Member
import gay.doughmination.plural.data.MentalLevel
import gay.doughmination.plural.data.PrideFlags
import gay.doughmination.plural.data.Relationship
import gay.doughmination.plural.data.RelationshipTypes
import gay.doughmination.plural.data.User
import gay.doughmination.plural.data.find
import gay.doughmination.plural.data.memberColour

/** An admin page: a back bar over a scrolling column. */
@Composable
private fun AdminPage(vm: SystemViewModel, title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        BackBar(title, onBack = vm::back)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

/** A member in a list to pick from, like the website's .pick rows. */
@Composable
private fun PickRow(member: Member, selected: Boolean, note: String? = null, trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) AccentDim else Raised)
            .border(1.dp, if (selected) AccentLine else Line, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Avatar(member.avatarUrl, 36.dp, ring = memberColour(member.color))
        Column(Modifier.weight(1f)) {
            Text(member.shownName, maxLines = 1, overflow = TextOverflow.Ellipsis)
            note?.let { Text(it, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        trailing?.invoke()
    }
}

/** The website's MemberSelect: a dropdown of members. */
@Composable
private fun MemberSelect(label: String, members: List<Member>, selected: Member?, note: (Member) -> String = { "" }, onSelect: (Member) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = selected?.shownName ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            placeholder = { Text("Choose a member...") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            members.forEach { member ->
                DropdownMenuItem(
                    text = { Text(member.shownName + note(member)) },
                    leadingIcon = { Avatar(member.avatarUrl, 24.dp) },
                    onClick = {
                        onSelect(member)
                        open = false
                    },
                )
            }
        }
    }
}

// ---- Switch manager -----------------------------------------------------------

/** The website's /admin/switch: pick who's fronting now. */
@Composable
fun SwitchScreen(vm: SystemViewModel) {
    val current = vm.fronters?.members.orEmpty().map { it.id }.toSet()
    var selected by remember { mutableStateOf(current) }
    var search by rememberSaveable { mutableStateOf("") }
    // Starts from whoever is fronting; follows a switch made elsewhere until changed here.
    LaunchedEffect(current) { selected = current }

    val query = search.trim().lowercase()
    val shown = vm.members.filter { m -> m.shownName.lowercase().contains(query) || m.tags.any { it.lowercase().contains(query) } }
    val changed = selected != current

    AdminPage(vm, "Switch manager") {
        Text("Manage fronting members", color = Muted)
        Panel {
            SectionTitle("Currently fronting")
            if (current.isEmpty()) Text("No one currently fronting", color = Muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                vm.fronters?.members.orEmpty().forEach { PersonChip(vm.member(it.id) ?: it) {} }
            }
        }

        Panel {
            SectionTitle("Selected")
            Text("${selected.size} member${if (selected.size != 1) "s" else ""} selected", color = Muted, fontSize = 13.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                vm.members.filter { it.id in selected }.forEach { member ->
                    PersonChip(member, badge = "✕") { selected = selected - member.id }
                }
            }
            Button(
                onClick = {
                    if (selected.isEmpty()) vm.show("Please select at least one member", error = true)
                    else vm.switchTo(vm.members.filter { it.id in selected }.map { it.id })
                },
                enabled = !vm.busy && selected.isNotEmpty() && changed,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (vm.busy) "Switching..." else if (changed) "Apply switch" else "No changes") }
        }

        OutlinedTextField(search, { search = it }, placeholder = { Text("Search members...") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { selected = selected + shown.map { it.id } }, enabled = shown.isNotEmpty()) { Text("Select all") }
            OutlinedButton(onClick = { selected = emptySet() }, enabled = selected.isNotEmpty()) { Text("Clear all") }
        }
        if (shown.isEmpty()) Text(if (search.isNotBlank()) "No members found matching your search" else "No members available", color = Muted)
        shown.forEach { member ->
            PickRow(
                member,
                selected = member.id in selected,
                note = member.pronouns,
                trailing = {
                    if (member.id in current) Badge("fronting")
                    Checkbox(checked = member.id in selected, onCheckedChange = null)
                },
            ) { selected = if (member.id in selected) selected - member.id else selected + member.id }
        }
    }
}

// ---- Mental health --------------------------------------------------------------

/** The website's /admin/mental. */
@Composable
fun MentalScreen(vm: SystemViewModel) {
    val current = vm.mental
    var level by rememberSaveable { mutableStateOf(current?.level ?: "") }
    var notes by rememberSaveable { mutableStateOf(current?.notes ?: "") }

    AdminPage(vm, "Mental health") {
        Text("Update system mental state", color = Muted)
        current?.let { MentalBanner(it) }
        Panel {
            Field("Mental state level") {
                MentalLevel.entries.forEach { option ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { level = option.value }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = level == option.value, onClick = { level = option.value })
                        Icon(mentalIcon(option.value), null, tint = mentalColour(option.value), modifier = Modifier.size(20.dp))
                        HGap()
                        Text(option.label)
                    }
                }
            }
            Field("Notes (optional)") {
                OutlinedTextField(
                    notes, { notes = it },
                    placeholder = { Text("Add any additional notes about the current mental state...") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Button(onClick = { vm.setMental(level, notes) }, enabled = !vm.busy && level.isNotBlank()) {
                Text(if (vm.busy) "Updating..." else "Update mental state")
            }
        }
    }
}

// ---- Status manager -------------------------------------------------------------

private val EmojiSuggestions = listOf("💤", "🎮", "📚", "🎨", "🎵", "💻", "🌙", "☀️", "🍕", "☕", "🎬", "✨", "💭", "😴", "🏃", "🧘")

/** The website's /admin/status. */
@Composable
fun StatusScreen(vm: SystemViewModel) {
    var chosenId by rememberSaveable { mutableStateOf<String?>(null) }
    val chosen = chosenId?.let(vm::member)
    var text by rememberSaveable { mutableStateOf("") }
    var emoji by rememberSaveable { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    // Picking a member fills the form with their current status.
    LaunchedEffect(chosenId) {
        text = chosen?.status?.text.orEmpty()
        emoji = chosen?.status?.emoji.orEmpty()
    }
    val withStatus = vm.members.filter { it.status != null }

    AdminPage(vm, "Status manager") {
        Text("Update member status messages", color = Muted)
        Panel {
            MemberSelect("Select member", vm.members, chosen, note = { if (it.status != null) " (has status)" else "" }) { chosenId = it.id }
            if (chosen != null) {
                Field("Emoji (optional)") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(emoji, { if (it.length <= 4) emoji = it }, placeholder = { Text("😊") }, singleLine = true, modifier = Modifier.size(width = 80.dp, height = 56.dp))
                        if (emoji.isNotEmpty()) IconButton(onClick = { emoji = "" }) { Icon(Icons.Outlined.Close, "Clear emoji") }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        EmojiSuggestions.forEach { suggestion -> Chip(suggestion, active = emoji == suggestion) { emoji = suggestion } }
                    }
                }
                Field("Status text", "${text.length}/100 characters") {
                    OutlinedTextField(text, { if (it.length <= 100) text = it }, placeholder = { Text("What's happening?") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.setStatus(chosen, text, emoji) }, enabled = !vm.busy && text.isNotBlank()) {
                        Text(if (vm.busy) "Updating..." else "Update status")
                    }
                    if (chosen.status != null) {
                        OutlinedButton(onClick = { confirmClear = true }, enabled = !vm.busy) { Text("Clear", color = Bad) }
                    }
                }
                chosen.status?.let { status ->
                    Notice("Current: ${status.emoji.orEmpty()} ${status.text}".trim() + (formatTime(status.updatedAt)?.let { "\nUpdated $it" } ?: ""))
                }
            }
        }

        Panel {
            SectionTitle("Members with a status")
            Text("${withStatus.size} member(s) have status set", color = Muted, fontSize = 13.sp)
            if (withStatus.isEmpty()) Text("No members have status set", color = Muted)
            withStatus.forEach { member ->
                PickRow(member, selected = member.id == chosenId, note = "${member.status?.emoji.orEmpty()} ${member.status?.text.orEmpty()}".trim()) {
                    chosenId = member.id
                }
            }
        }
    }

    if (confirmClear && chosen != null) {
        Confirm("Clear status?", "Clear status for ${chosen.shownName}?", "Clear", onDismiss = { confirmClear = false }) {
            confirmClear = false
            vm.clearStatus(chosen)
        }
    }
}

// ---- Tag manager ------------------------------------------------------------------

/** The website's /admin/tags. */
@Composable
fun TagsScreen(vm: SystemViewModel) {
    var chosenId by rememberSaveable { mutableStateOf<String?>(null) }
    val chosen = chosenId?.let(vm::member)
    var newTag by rememberSaveable { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf("") }
    var removing by remember { mutableStateOf<Pair<Member, String>?>(null) }
    val allTags = vm.members.flatMap { it.tags }.distinct().sortedBy { it.lowercase() }
    val query = search.trim().lowercase()
    val shown = vm.members.filter { m -> m.shownName.lowercase().contains(query) || m.tags.any { it.lowercase().contains(query) } }

    AdminPage(vm, "Tag manager") {
        Text("Manage member tags", color = Muted)
        Panel {
            MemberSelect("Select member", vm.members, chosen) { chosenId = it.id }
            if (chosen != null) {
                Field("Tags") {
                    if (chosen.tags.isEmpty()) Text("No tags yet", color = Muted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        chosen.tags.forEach { tag -> Chip("$tag  ✕", tint = memberColour(chosen.color)) { removing = chosen to tag } }
                    }
                }
                Field("Add a tag") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(newTag, { newTag = it }, placeholder = { Text("New tag") }, singleLine = true, modifier = Modifier.weight(1f))
                        Button(onClick = { vm.addTag(chosen, newTag); newTag = "" }, enabled = !vm.busy && newTag.isNotBlank()) { Text("Add") }
                    }
                    val suggestions = allTags.filter { it !in chosen.tags && it.contains(newTag.trim(), ignoreCase = true) }
                    if (suggestions.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            suggestions.take(12).forEach { tag -> Chip(tag) { newTag = tag } }
                        }
                    }
                }
            }
        }

        OutlinedTextField(search, { search = it }, placeholder = { Text("Search members or tags...") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        shown.forEach { member ->
            PickRow(member, selected = member.id == chosenId, note = member.tags.joinToString(", ").ifBlank { "No tags" }) { chosenId = member.id }
        }
    }

    removing?.let { (member, tag) ->
        Confirm("Remove tag?", "Remove tag \"$tag\" from ${member.shownName}?", "Remove", onDismiss = { removing = null }) {
            removing = null
            vm.removeTag(member, tag)
        }
    }
}

// ---- Pride manager (owner) ----------------------------------------------------------

/** The website's /admin/pride: tap a flag to give or take it. */
@Composable
fun PrideScreen(vm: SystemViewModel) {
    var chosenId by rememberSaveable { mutableStateOf<String?>(null) }
    val chosen = chosenId?.let(vm::member)

    AdminPage(vm, "Pride manager") {
        Text("Set member pride identities", color = Muted)
        Panel {
            MemberSelect("Select member", vm.members, chosen) { chosenId = it.id }
            if (chosen != null) {
                Field("Identities", "Tap a flag to add it; tap a highlighted one to remove it.") {
                    // The catalogue, plus anything already set that isn't in it.
                    val labels = PrideFlags.map { it.label } + chosen.pride.filter { label -> PrideFlags.none { it.label.equals(label, true) } }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        labels.forEach { label ->
                            val has = chosen.pride.any { it.equals(label, ignoreCase = true) }
                            Chip(label, active = has, leading = { PrideSwatch(label) }) {
                                if (vm.busy) return@Chip
                                if (has) vm.removePride(chosen, chosen.pride.first { it.equals(label, true) }) else vm.addPride(chosen, label)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---- Relationships (owner) --------------------------------------------------------

/** The website's /admin/relationships. */
@Composable
fun ManageRelationshipsScreen(vm: SystemViewModel) {
    var aId by rememberSaveable { mutableStateOf<String?>(null) }
    var bId by rememberSaveable { mutableStateOf<String?>(null) }
    var type by rememberSaveable { mutableStateOf("partner") }
    var since by rememberSaveable { mutableStateOf("") }
    var removing by remember { mutableStateOf<Relationship?>(null) }
    val a = aId?.let(vm::member)
    val b = bId?.let(vm::member)

    fun label(identifier: String) = vm.members.find(identifier)?.shownName ?: identifier

    AdminPage(vm, "Relationships") {
        Text("Map who is dating who. A crush goes one way: the first member has a crush on the second.", color = Muted)
        Panel {
            MemberSelect("First member", vm.members, a) { aId = it.id }
            Field("Type") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RelationshipTypes.forEach { option -> Chip(option, active = type == option) { type = option } }
                }
            }
            MemberSelect("Second member", vm.members, b) { bId = it.id }
            Field("Since (optional)", "Free text, e.g. 2026-02-14") {
                OutlinedTextField(since, { since = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            Button(
                onClick = {
                    when {
                        a == null || b == null -> vm.show("Pick two members", error = true)
                        a.id == b.id -> vm.show("Pick two different members", error = true)
                        else -> vm.addRelationship(a, b, type, since) { aId = null; bId = null; since = ""; type = "partner" }
                    }
                },
                enabled = !vm.busy,
            ) { Text(if (vm.busy) "Saving..." else "Add relationship") }
        }

        Panel {
            SectionTitle("Current relationships")
            if (vm.relationships.isEmpty()) Text("None yet", color = Muted)
            vm.relationships.forEach { edge ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${label(edge.members[0])} ${if (edge.type == "crush") "→" else "&"} ${label(edge.members[1])}")
                        Text(edge.type + (edge.since?.let { " · since $it" } ?: ""), color = Muted, fontSize = 12.sp)
                    }
                    IconButton(onClick = { removing = edge }, enabled = !vm.busy) { Icon(Icons.Outlined.Delete, "Remove", tint = Bad) }
                }
            }
        }
    }

    removing?.let { edge ->
        val pair = "${label(edge.members[0])} ${if (edge.type == "crush") "→" else "&"} ${label(edge.members[1])}"
        Confirm("Remove relationship?", "Remove the relationship between $pair?", "Remove", onDismiss = { removing = null }) {
            removing = null
            vm.removeRelationship(edge)
        }
    }
}

// ---- User management (owner) ------------------------------------------------------

/** The website's /owner/users. */
@Composable
fun UsersScreen(vm: SystemViewModel) {
    var creating by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<User?>(null) }

    AdminPage(vm, "User management") {
        Text("Accounts sign in with SSO; this controls their profile and roles here.", color = Muted)
        if (creating) {
            CreateUser(vm, onDone = { creating = false })
        } else {
            Button(onClick = { creating = true }) { Text("Add user") }
        }
        vm.users.forEach { user ->
            if (editingId == user.id) {
                EditUser(vm, user, onDone = { editingId = null })
            } else {
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Avatar(user.avatarUrl, 44.dp)
                        Column(Modifier.weight(1f)) {
                            Text(user.shownName)
                            Text("@${user.username}" + (user.email?.let { " · $it" } ?: ""), color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (user.isOwner) Badge("Owner")
                                if (user.isAdmin) Badge("Admin", accent = false)
                                if (user.isPet) Badge("Pet", accent = false)
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { editingId = user.id }) { Text("Edit") }
                        if (user.id != vm.user?.id) {
                            OutlinedButton(onClick = { deleting = user }, enabled = !vm.busy) { Text("Delete", color = Bad) }
                        }
                    }
                }
            }
        }
    }

    deleting?.let { user ->
        Confirm("Delete user?", "Delete user \"${user.username}\"? This action cannot be undone.", "Delete", onDismiss = { deleting = null }) {
            deleting = null
            vm.deleteUser(user)
        }
    }
}

@Composable
private fun CreateUser(vm: SystemViewModel, onDone: () -> Unit) {
    var username by rememberSaveable { mutableStateOf("") }
    var displayName by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var admin by rememberSaveable { mutableStateOf(false) }
    var pet by rememberSaveable { mutableStateOf(false) }
    Panel {
        SectionTitle("New user")
        OutlinedTextField(username, { username = it }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(displayName, { displayName = it }, label = { Text("Display name (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(email, { email = it }, label = { Text("Email (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Toggle("Admin", admin) { admin = it }
        Toggle("Pet", pet) { pet = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    if (username.isBlank()) {
                        vm.show("Username is required", error = true)
                    } else {
                        vm.createUser(
                            Api.UserCreate(username.trim(), displayName.trim().ifBlank { null }, email.trim().ifBlank { null }, admin, pet),
                            then = onDone,
                        )
                    }
                },
                enabled = !vm.busy,
            ) { Text("Create") }
            OutlinedButton(onClick = onDone) { Text("Cancel") }
        }
    }
}

@Composable
private fun EditUser(vm: SystemViewModel, user: User, onDone: () -> Unit) {
    var displayName by rememberSaveable(user.id) { mutableStateOf(user.displayName.orEmpty()) }
    var email by rememberSaveable(user.id) { mutableStateOf(user.email.orEmpty()) }
    var avatarUrl by rememberSaveable(user.id) { mutableStateOf(user.avatarUrl.orEmpty()) }
    var admin by rememberSaveable(user.id) { mutableStateOf(user.isAdmin) }
    var pet by rememberSaveable(user.id) { mutableStateOf(user.isPet) }
    Panel(Modifier.border(1.dp, AccentLine, RoundedCornerShape(16.dp))) {
        SectionTitle("Editing @${user.username}")
        OutlinedTextField(displayName, { displayName = it }, label = { Text("Display name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            email, { email = it },
            label = { Text("Email") },
            singleLine = true,
            // Only the owner may change someone's email, as on the website.
            enabled = vm.isOwner,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(avatarUrl, { avatarUrl = it }, label = { Text("Avatar URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Toggle("Admin", admin) { admin = it }
        Toggle("Pet", pet) { pet = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.updateUser(user, displayName, email, avatarUrl, admin, pet, then = onDone) }, enabled = !vm.busy) { Text("Save") }
            OutlinedButton(onClick = onDone) { Text("Cancel") }
        }
    }
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

