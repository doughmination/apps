@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package gay.doughmination.plural.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gay.doughmination.plural.data.Live
import gay.doughmination.plural.data.Member
import gay.doughmination.plural.data.memberColour
import gay.doughmination.plural.data.readableOnDark

/** "Untagged", the website's tag filter for members with no tags. */
const val UNTAGGED = "untagged"

/** The members page: mental state, who's fronting, then everyone, searchable and filterable. */
@Composable
fun HomeScreen(vm: SystemViewModel) {
    var filtersOpen by rememberSaveable { mutableStateOf(false) }

    val tags = remember(vm.members) { vm.members.flatMap { it.tags }.distinct().sortedBy { it.lowercase() } }
    val identities = remember(vm.members) { vm.members.flatMap { it.pride }.distinct().sortedBy { it.lowercase() } }
    val shown = remember(vm.members, vm.search, vm.tagFilter, vm.identityFilter) {
        val query = vm.search.trim().lowercase()
        vm.members.filter { member ->
            if (query.isNotEmpty()) {
                val named = member.shownName.lowercase().contains(query)
                val tagged = member.tags.any { it.lowercase().contains(query) }
                if (!named && !tagged) return@filter false
            }
            when (val tag = vm.tagFilter) {
                null -> {}
                UNTAGGED -> if (member.tags.isNotEmpty()) return@filter false
                else -> if (tag !in member.tags) return@filter false
            }
            vm.identityFilter?.let { if (it !in member.pride) return@filter false }
            true
        }
    }
    val fronting = vm.fronters?.members.orEmpty()
    val activeFilters = (if (vm.tagFilter != null) 1 else 0) + (if (vm.identityFilter != null) 1 else 0)

    if (vm.loading && vm.members.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    PullToRefreshBox(isRefreshing = vm.refreshing, onRefresh = { vm.refresh() }, modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            val full: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }

            if (vm.liveStatus == Live.Status.Reconnecting) {
                item(span = full) { Notice("Live updates disconnected. Reconnecting…", error = true) }
            }
            vm.loadError?.let { error ->
                item(span = full) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Notice(error, error = true)
                        OutlinedButton(onClick = { vm.refresh() }) { Text("Try again") }
                    }
                }
            }

            item(span = full) { PageHead("// system.members", "System Members") }

            vm.mental?.let { state -> item(span = full) { MentalBanner(state) } }

            if (fronting.isNotEmpty()) {
                item(span = full) { FrontingPanel(vm, fronting) }
            }

            item(span = full) {
                Controls(vm, activeFilters) { filtersOpen = true }
            }

            if (shown.isEmpty() && vm.members.isNotEmpty()) {
                item(span = full) {
                    Panel {
                        Text(
                            if (vm.search.isNotBlank() || activeFilters > 0) "No members found matching your criteria." else "No members available.",
                            color = Muted,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (vm.search.isNotBlank()) OutlinedButton(onClick = { vm.search = "" }) { Text("Clear search") }
                            if (activeFilters > 0) OutlinedButton(onClick = vm::clearFilters) { Text("Clear filters") }
                        }
                    }
                }
            }

            items(shown, key = { it.id }) { member ->
                MemberCard(member, fronting = vm.isFronting(member), activeTag = vm.tagFilter, onTag = { tag ->
                    vm.tagFilter = if (vm.tagFilter == tag) null else tag
                }) { vm.open(Screen.MemberPage(member.id)) }
            }
        }
    }

    if (filtersOpen) {
        ModalBottomSheet(onDismissRequest = { filtersOpen = false }) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Filters", style = MaterialTheme.typography.titleLarge)
                Text("Tag", color = Muted, style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("All", active = vm.tagFilter == null) { vm.tagFilter = null }
                    tags.forEach { tag -> Chip(tag, active = vm.tagFilter == tag) { vm.tagFilter = if (vm.tagFilter == tag) null else tag } }
                    Chip("Untagged", active = vm.tagFilter == UNTAGGED) {
                        vm.tagFilter = if (vm.tagFilter == UNTAGGED) null else UNTAGGED
                    }
                }
                if (identities.isNotEmpty()) {
                    Text("Identity", color = Muted, style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("All", active = vm.identityFilter == null) { vm.identityFilter = null }
                        identities.forEach { identity ->
                            Chip(identity, active = vm.identityFilter == identity, leading = { PrideSwatch(identity) }) {
                                vm.identityFilter = if (vm.identityFilter == identity) null else identity
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (activeFilters > 0) OutlinedButton(onClick = vm::clearFilters) { Text("Clear filters") }
                    Button(onClick = { filtersOpen = false }) { Text("Done") }
                }
            }
        }
    }
}

@Composable
private fun FrontingPanel(vm: SystemViewModel, fronting: List<Member>) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(if (fronting.size > 1) "Currently Co-fronting" else "Currently Fronting")
            sinceText(vm.fronters?.timestamp)?.let {
                HGap()
                Text("for $it", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
        fronting.forEach { member ->
            // The socket's members lack tags and status; the full list has them.
            val full = vm.member(member.id) ?: member
            val colour = memberColour(full.color) ?: Accent
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { vm.open(Screen.MemberPage(full.id)) }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(full.avatarUrl, 56.dp, ring = colour)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                    Text(
                        full.shownName,
                        color = readableOnDark(full.color, Accent),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    full.pronouns?.ifBlank { null }?.let { Text(it, color = Muted, fontSize = 13.sp) }
                    if (full.tags.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            full.tags.sortedBy { it.lowercase() }.forEach { tag ->
                                Chip(tag, active = vm.tagFilter == tag) { vm.tagFilter = if (vm.tagFilter == tag) null else tag }
                            }
                        }
                    }
                    full.status?.let { StatusTag(it) }
                }
            }
        }
    }
}

@Composable
private fun Controls(vm: SystemViewModel, activeFilters: Int, openFilters: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = vm.search,
                onValueChange = { vm.search = it },
                placeholder = { Text("Search members...") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (vm.search.isNotEmpty()) {
                        IconButton(onClick = { vm.search = "" }) { Icon(Icons.Outlined.Close, contentDescription = "Clear search") }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Line),
                modifier = Modifier.weight(1f),
            )
            FilledTonalButton(onClick = openFilters, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
                Icon(Icons.Outlined.FilterList, contentDescription = null, modifier = Modifier.size(18.dp))
                HGap(6)
                Text(if (activeFilters > 0) "Filters · $activeFilters" else "Filters")
            }
        }
        if (activeFilters > 0) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                vm.tagFilter?.let { tag ->
                    Chip("tag: ${if (tag == UNTAGGED) "untagged" else tag}  ✕", active = true) { vm.tagFilter = null }
                }
                vm.identityFilter?.let { identity ->
                    Chip("identity: $identity  ✕", active = true, leading = { PrideSwatch(identity) }) { vm.identityFilter = null }
                }
            }
        }
    }
}

/** A member in the grid: avatar, name in their colour, pronouns, two tags, status. */
@Composable
private fun MemberCard(member: Member, fronting: Boolean, activeTag: String?, onTag: (String) -> Unit, onClick: () -> Unit) {
    val colour = memberColour(member.color) ?: Accent
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (fronting) colour.copy(alpha = 0.10f) else Raised)
            .border(if (fronting) 2.dp else 1.dp, if (fronting) colour.copy(alpha = 0.8f) else Line, shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box {
            Avatar(member.avatarUrl, 84.dp, ring = colour.copy(alpha = 0.9f))
            if (fronting) {
                Box(Modifier.align(Alignment.BottomEnd)) { Badge("fronting") }
            }
        }
        Text(
            member.shownName,
            color = readableOnDark(member.color, TextColour),
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        member.pronouns?.ifBlank { null }?.let {
            Text(it, color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (member.tags.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                member.tags.sortedBy { it.lowercase() }.take(2).forEach { tag ->
                    Chip(tag, active = activeTag == tag) { onTag(tag) }
                }
                if (member.tags.size > 2) {
                    Text("+${member.tags.size - 2}", color = Muted, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterVertically))
                }
            }
        }
        member.status?.let { StatusTag(it) }
    }
}
