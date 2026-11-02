package gay.doughmination.mail.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.AllInbox
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Drafts
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.browser.customtabs.CustomTabsIntent
import android.net.Uri
import gay.doughmination.mail.data.ListFolder
import gay.doughmination.mail.data.SwipeAction
import gay.doughmination.mail.data.ThreadSummary
import kotlinx.coroutines.launch

// CONFIRM only exists from Android 11.
private val swipeHaptic =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY

private val folderIcons = mapOf(
    ListFolder.Inbox to Icons.Outlined.Inbox,
    ListFolder.Starred to Icons.Outlined.StarOutline,
    ListFolder.Sent to Icons.AutoMirrored.Outlined.Send,
    ListFolder.Drafts to Icons.Outlined.Drafts,
    ListFolder.Bin to Icons.Outlined.Delete,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadListScreen(vm: MailViewModel) {
    val drawer = rememberDrawerState(androidx.compose.material3.DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var menuFor by remember { mutableStateOf<ThreadSummary?>(null) }
    var confirmEmpty by remember { mutableStateOf(false) }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            Drawer(vm) { scope.launch { drawer.close() } }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(vm.folder.label)
                            vm.mailbox?.let {
                                Text(it, style = MaterialTheme.typography.labelMedium, color = Muted)
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawer.open() } }) {
                            Icon(Icons.Outlined.Menu, "Folders")
                        }
                    },
                    actions = {
                        if (vm.folder == ListFolder.Bin && vm.threads.isNotEmpty()) {
                            TextButton(onClick = { confirmEmpty = true }) { Text("Empty Bin") }
                        }
                    },
                )
            },
            floatingActionButton = {
                if (vm.folder != ListFolder.Bin) {
                    ExtendedFloatingActionButton(
                        onClick = vm::composeNew,
                        icon = { Icon(Icons.Outlined.Edit, null) },
                        text = { Text("Compose") },
                    )
                }
            },
            snackbarHost = { SnackbarHost(LocalSnackbar.current) },
        ) { padding ->
            PullToRefreshBox(
                isRefreshing = vm.refreshing,
                onRefresh = { vm.reload(pulled = true) },
                modifier = Modifier.padding(padding).fillMaxSize(),
            ) {
                ThreadList(vm, onHold = { menuFor = it })
            }
        }
    }

    menuFor?.let { t ->
        ThreadMenu(vm, t) { menuFor = null }
    }

    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text("Empty the Bin?") },
            text = { Text("Everything in the Bin is deleted for good.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmEmpty = false
                    vm.emptyBin()
                }) { Text("Delete forever", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ThreadList(vm: MailViewModel, onHold: (ThreadSummary) -> Unit) {
    val state = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val last = state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= state.layoutInfo.totalItemsCount - 5
        }
    }
    LaunchedEffect(nearEnd, vm.nextCursor) {
        if (nearEnd && vm.nextCursor != null) vm.loadMore()
    }

    when {
        vm.threads.isEmpty() && vm.loading -> Centered { CircularProgressIndicator() }
        vm.threads.isEmpty() && vm.listError != null -> Centered {
            Text(vm.listError!!, color = Muted)
            TextButton(onClick = { vm.reload() }) { Text("Try again") }
        }
        vm.threads.isEmpty() -> Centered {
            Text(if (vm.folder == ListFolder.Bin) "The Bin is empty." else "Nothing here.", color = Muted)
        }
        else -> LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
            items(vm.threads, key = { it.id }) { t ->
                SwipeRow(vm, t, onHold)
            }
            if (vm.loading) {
                item { Centered(Modifier.padding(16.dp)) { CircularProgressIndicator(Modifier.size(24.dp)) } }
            }
        }
    }
}

@Composable
private fun Centered(modifier: Modifier = Modifier.fillMaxSize(), content: @Composable () -> Unit) {
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

/** One way to swipe a row: what it shows underneath, and what it does. */
private class Swipe(val label: String, val icon: ImageVector, val color: Color, val dismiss: Boolean, val run: () -> Unit)

/** What a swipe set up in Settings does to this row; null where it can't apply. Same as the web. */
private fun swipeFor(choice: SwipeAction, vm: MailViewModel, t: ThreadSummary): Swipe? = when (choice) {
    // Only received mail has a read state.
    SwipeAction.Read -> if (vm.folder != ListFolder.Inbox && vm.folder != ListFolder.Starred) null else Swipe(
        if (t.unread > 0) "Read" else "Unread",
        if (t.unread > 0) Icons.Outlined.MarkEmailRead else Icons.Outlined.MarkEmailUnread,
        Accent,
        dismiss = false,
    ) { vm.toggleRead(t) }
    SwipeAction.Star -> Swipe(
        if (t.starred) "Unstar" else "Star",
        if (t.starred) Icons.Outlined.StarOutline else Icons.Filled.Star,
        Accent,
        // Unstarring takes it out of Starred.
        dismiss = vm.folder == ListFolder.Starred && t.starred,
    ) { vm.toggleStar(t.id, !t.starred) }
    SwipeAction.Bin -> Swipe("Bin", Icons.Outlined.Delete, Danger, dismiss = true) { vm.moveToBin(t.id) }
    SwipeAction.None -> null
}

/**
 * Swipes do what Settings says (toggle read and bin it, unless changed). In
 * the Bin, swiping right restores and left does nothing. Same as the web app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeRow(vm: MailViewModel, t: ThreadSummary, onHold: (ThreadSummary) -> Unit) {
    val inBin = vm.folder == ListFolder.Bin
    val right = if (inBin) Swipe("Restore", Icons.Outlined.RestoreFromTrash, Accent, dismiss = true) { vm.restore(t.id) }
    else swipeFor(vm.me?.swipeRight ?: SwipeAction.Read, vm, t)
    val left = if (inBin) null else swipeFor(vm.me?.swipeLeft ?: SwipeAction.Bin, vm, t)
    val view = LocalView.current
    // Material can ask more than once for one swipe; an action must only run once.
    var firedAt by remember { mutableStateOf(0L) }
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            val now = System.currentTimeMillis()
            if (value != SwipeToDismissBoxValue.Settled && now - firedAt < 600) return@rememberSwipeToDismissBoxState false
            if (value != SwipeToDismissBoxValue.Settled) firedAt = now
            val swipe = when (value) {
                SwipeToDismissBoxValue.StartToEnd -> right
                SwipeToDismissBoxValue.EndToStart -> left
                SwipeToDismissBoxValue.Settled -> return@rememberSwipeToDismissBoxState true
            } ?: return@rememberSwipeToDismissBoxState false
            view.performHapticFeedback(swipeHaptic)
            swipe.run()
            // Not dismissed: the row springs back, showing its new state.
            swipe.dismiss
        },
        positionalThreshold = { it * 0.35f },
    )

    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = right != null,
        enableDismissFromEndToStart = left != null,
        backgroundContent = {
            val direction = state.dismissDirection
            val swipe = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> right
                SwipeToDismissBoxValue.EndToStart -> left
                SwipeToDismissBoxValue.Settled -> null
            }
            Row(
                Modifier.fillMaxSize().background(swipe?.color ?: Color.Transparent).padding(horizontal = 24.dp),
                horizontalArrangement = if (direction == SwipeToDismissBoxValue.EndToStart) Arrangement.End else Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (swipe != null) {
                    Icon(swipe.icon, null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text(swipe.label, color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        },
    ) {
        val moving = state.dismissDirection != SwipeToDismissBoxValue.Settled
        ThreadRow(vm, t, opaque = moving, onClick = { vm.open(t) }, onLongClick = {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            onHold(t)
        })
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ThreadRow(vm: MailViewModel, t: ThreadSummary, opaque: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val unread = t.unread > 0
    // The Bin and Starred mix folders; whoever it's from reads best there.
    val fromSide = vm.folder != ListFolder.Sent && vm.folder != ListFolder.Drafts
    val who = if (fromSide) displayName(t.from)
    else "To: " + (t.to.joinToString(", ") { displayName(it) }.ifEmpty { "(nobody yet)" })
    // Laid out like the web's rows on a phone, sizes and all.
    Row(
        Modifier
            .fillMaxWidth()
            // Opaque only while it's being swiped, so the wallpaper shows through at rest.
            .background(if (opaque) Bg else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Avatar(if (fromSide) displayName(t.from) else displayName(t.to.firstOrNull() ?: "?"))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(Modifier.padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    who,
                    Modifier.weight(1f, fill = false),
                    fontSize = 15.sp,
                    fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal,
                    color = if (unread) MaterialTheme.colorScheme.onSurface else Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (t.count > 1) {
                    Spacer(Modifier.width(6.dp))
                    Text("${t.count}", fontSize = 12.sp, color = Muted)
                }
                if (t.hasAttachments) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Outlined.AttachFile, "Has attachments", Modifier.size(14.dp), tint = Muted)
                }
                Spacer(Modifier.weight(1f).widthIn(min = 8.dp))
                Text(
                    fmtTime(t.lastAt),
                    fontSize = 12.sp,
                    fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal,
                    color = if (unread) MaterialTheme.colorScheme.onSurface else Muted,
                )
            }
            Text(
                t.subject.ifEmpty { "(no subject)" },
                Modifier.padding(end = 8.dp),
                fontSize = 14.sp,
                fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    t.snippet,
                    Modifier.weight(1f),
                    fontSize = 13.5.sp,
                    color = Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // A star on every row, as on the web; the Bin has none.
                if (vm.folder != ListFolder.Bin) {
                    IconButton(onClick = { vm.toggleStar(t.id, !t.starred) }, Modifier.size(32.dp)) {
                        if (t.starred) Icon(Icons.Filled.Star, "Unstar", Modifier.size(18.dp), tint = StarYellow)
                        else Icon(Icons.Outlined.StarOutline, "Star", Modifier.size(18.dp), tint = Muted)
                    }
                }
            }
        }
    }
}

/** A coloured initial, as the web draws it: the same hue from the same name. */
@Composable
private fun Avatar(name: String) {
    val initial = name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar() ?: '?'
    // The web's hash runs over code points, taking each one's first UTF-16 unit.
    var hue = 0
    var i = 0
    while (i < name.length) {
        hue = (hue * 31 + name[i].code) % 360
        i += Character.charCount(name.codePointAt(i))
    }
    Box(
        Modifier.padding(top = 2.dp).size(40.dp).clip(CircleShape).background(Color.hsl(hue.toFloat(), 0.55f, 0.42f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(initial.toString(), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** The long-press menu, as a bottom sheet. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ThreadMenu(vm: MailViewModel, t: ThreadSummary, onClose: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var confirmDelete by remember { mutableStateOf(false) }

    @Composable
    fun item(label: String, icon: ImageVector, danger: Boolean = false, run: () -> Unit) {
        ListItem(
            headlineContent = { Text(label, color = if (danger) Danger else Color.Unspecified) },
            leadingContent = { Icon(icon, null, tint = if (danger) Danger else Muted) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.combinedClickable(onClick = run),
        )
    }

    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheet) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
            Text(
                t.subject.ifEmpty { "(no subject)" },
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            HorizontalDivider()
            when (vm.folder) {
                ListFolder.Bin -> {
                    item("Open", Icons.Outlined.OpenInNew) { onClose(); vm.open(t) }
                    item("Restore", Icons.Outlined.RestoreFromTrash) { onClose(); vm.restore(t.id) }
                    item(
                        if (confirmDelete) "Tap again to delete forever" else "Delete forever",
                        Icons.Outlined.DeleteForever,
                        danger = true,
                    ) {
                        if (!confirmDelete) confirmDelete = true else { onClose(); vm.deleteForever(t.id) }
                    }
                }
                else -> {
                    if (vm.folder == ListFolder.Drafts) {
                        item("Edit", Icons.Outlined.Edit) { onClose(); vm.editDraft(t.latestId) }
                        item("Open conversation", Icons.Outlined.OpenInNew) { onClose(); vm.openThread(t.id, false) }
                    } else {
                        item("Open", Icons.Outlined.OpenInNew) { onClose(); vm.open(t) }
                    }
                    if (t.starred) {
                        item("Unstar", Icons.Outlined.StarOutline) { onClose(); vm.toggleStar(t.id, false) }
                    } else {
                        item("Star", Icons.Outlined.Star) { onClose(); vm.toggleStar(t.id, true) }
                    }
                    if (vm.folder == ListFolder.Inbox || vm.folder == ListFolder.Starred) {
                        if (t.unread > 0) {
                            item("Mark as read", Icons.Outlined.MarkEmailRead) { onClose(); vm.toggleRead(t) }
                        } else {
                            item("Mark as unread", Icons.Outlined.MarkEmailUnread) { onClose(); vm.toggleRead(t) }
                        }
                    }
                    item("Move to Bin", Icons.Outlined.Delete, danger = true) { onClose(); vm.moveToBin(t.id) }
                }
            }
        }
    }
}

@Composable
private fun Drawer(vm: MailViewModel, close: () -> Unit) {
    val context = LocalContext.current
    val me = vm.me
    val inboxUnread = vm.unread.values.sum()

    @Composable
    fun entry(label: String, icon: ImageVector, selected: Boolean, badge: Int = 0, indent: Boolean = false, onClick: () -> Unit) {
        NavigationDrawerItem(
            label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            icon = { Icon(icon, null) },
            selected = selected,
            badge = if (badge > 0) ({ Badge { Text("$badge") } }) else null,
            onClick = { close(); onClick() },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding).padding(start = if (indent) 24.dp else 0.dp),
        )
    }

    ModalDrawerSheet {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 12.dp)) {
            Row(Modifier.padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                AccountPicture(me, 40.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(me?.name ?: me?.username ?: "Inbox", style = MaterialTheme.typography.titleMedium)
                    me?.addresses?.firstOrNull()?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = Muted) }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            for (f in ListFolder.entries) {
                entry(
                    f.label,
                    folderIcons.getValue(f),
                    selected = vm.folder == f,
                    badge = if (f == ListFolder.Inbox) inboxUnread else 0,
                ) { vm.show(f) }
            }

            if (me != null && me.addresses.size > 1) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                entry("All addresses", Icons.Outlined.AllInbox, selected = vm.mailbox == null) { vm.show(vm.folder, null) }
                for (domain in me.domains) {
                    val addrs = me.addresses.filter { it.endsWith("@$domain") }
                    entry(
                        domain,
                        Icons.Outlined.Language,
                        selected = vm.mailbox == "@$domain",
                        badge = addrs.sumOf { vm.unread[it] ?: 0 },
                    ) { vm.show(vm.folder, "@$domain") }
                    if (addrs.size > 1) {
                        for (addr in addrs) {
                            entry(
                                addr.substringBefore("@"),
                                Icons.Outlined.AlternateEmail,
                                selected = vm.mailbox == addr,
                                badge = vm.unread[addr] ?: 0,
                                indent = true,
                            ) { vm.show(vm.folder, addr) }
                        }
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            entry("Settings", Icons.Outlined.Settings, selected = false) { vm.openSettings() }
            if (me?.isAdmin == true) {
                // Admin stays on the web; it's rarely needed and not worth a second copy.
                entry("Admin (web)", Icons.Outlined.Shield, selected = false) {
                    CustomTabsIntent.Builder().build()
                        .launchUrl(context, Uri.parse("${gay.doughmination.mail.BuildConfig.INBOX_URL}/admin"))
                }
            }
        }
    }
}
