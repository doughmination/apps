package gay.doughmination.mail.ui

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Forward
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import gay.doughmination.mail.InboxApp
import gay.doughmination.mail.data.Api
import gay.doughmination.mail.data.AttachmentInfo
import gay.doughmination.mail.data.Message

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadScreen(vm: MailViewModel, screen: Screen.Thread) {
    LaunchedEffect(screen, vm.threadVersion) { vm.loadThread(screen.id, screen.bin) }
    val thread = vm.thread?.takeIf { it.id == screen.id }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
                },
                actions = {
                    if (thread == null) return@TopAppBar
                    if (screen.bin) {
                        IconButton(onClick = { vm.restore(thread.id) }) {
                            Icon(Icons.Outlined.RestoreFromTrash, "Restore")
                        }
                        IconButton(onClick = { if (confirmDelete) vm.deleteForever(thread.id) else confirmDelete = true }) {
                            Icon(Icons.Outlined.DeleteForever, if (confirmDelete) "Tap again to delete forever" else "Delete forever", tint = if (confirmDelete) Danger else Muted)
                        }
                    } else {
                        IconButton(onClick = { vm.toggleStar(thread.id, !thread.starred) }) {
                            if (thread.starred) Icon(Icons.Filled.Star, "Unstar", tint = Accent)
                            else Icon(Icons.Outlined.StarOutline, "Star")
                        }
                        if (thread.messages.any { it.folder == "inbox" }) {
                            IconButton(onClick = { vm.markUnread(thread.id) }) {
                                Icon(Icons.Outlined.MarkEmailUnread, "Mark as unread")
                            }
                        }
                        IconButton(onClick = { vm.moveToBin(thread.id) }) { Icon(Icons.Outlined.Delete, "Move to Bin") }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(LocalSnackbar.current) },
    ) { padding ->
        when {
            thread != null -> LazyColumn(
                Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text(
                        thread.subject.ifEmpty { "(no subject)" },
                        Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
                val newest = thread.messages.lastOrNull()?.id
                items(thread.messages, key = { it.id }) { m ->
                    MessageCard(vm, m, screen.bin, startOpen = m.id == newest || !m.read)
                }
            }
            vm.threadError != null -> Column(
                Modifier.padding(padding).fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(vm.threadError!!, color = Muted)
                TextButton(onClick = { vm.back() }) { Text("Go back") }
            }
            else -> Column(
                Modifier.padding(padding).fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { CircularProgressIndicator() }
        }
    }
}

@Composable
private fun MessageCard(vm: MailViewModel, m: Message, bin: Boolean, startOpen: Boolean) {
    val api = (LocalContext.current.applicationContext as InboxApp).api
    var open by rememberSaveable(m.id) { mutableStateOf(startOpen) }
    var showRemote by rememberSaveable(m.id) { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val isDraft = m.folder == "drafts"

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.clickable { open = !open }.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (isDraft) "Draft" else displayName(m.from),
                    Modifier.weight(1f),
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDraft) Danger else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(fmtTime(m.sentAt), style = MaterialTheme.typography.labelMedium, color = Muted)
            }
            if (!open) {
                Text(m.snippet, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
                Text(bareAddress(m.from), style = MaterialTheme.typography.labelMedium, color = Muted)
                if (m.to.isNotEmpty()) Text("To: ${m.to.joinToString(", ")}", style = MaterialTheme.typography.labelMedium, color = Muted)
                if (m.cc.isNotEmpty()) Text("Cc: ${m.cc.joinToString(", ")}", style = MaterialTheme.typography.labelMedium, color = Muted)
                if (m.bcc.isNotEmpty()) Text("Bcc: ${m.bcc.joinToString(", ")}", style = MaterialTheme.typography.labelMedium, color = Muted)
            }
        }
        if (!open) return@Card

        val html = m.html
        if (html != null) {
            if (hasRemoteContent(html) && !showRemote) {
                OutlinedButton(onClick = { showRemote = true }, Modifier.padding(horizontal = 14.dp)) {
                    Icon(Icons.Outlined.Image, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Load remote images")
                }
                Spacer(Modifier.height(8.dp))
            }
            EmailView(api, html, m.attachments, showRemote, Modifier.fillMaxWidth())
        } else {
            SelectionContainer {
                Text(m.text.orEmpty(), Modifier.padding(horizontal = 14.dp))
            }
        }

        val files = m.attachments.filter { it.contentId == null || html == null }
        if (files.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 8.dp)) {
                val context = LocalContext.current
                for (a in files) {
                    AssistChip(
                        onClick = { download(context, api, a) },
                        label = { Text("${a.filename} · ${fmtSize(a.size)}", maxLines = 1) },
                        leadingIcon = { Icon(Icons.Outlined.AttachFile, null) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }
        }

        Row(Modifier.padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (isDraft && !bin) {
                TextButton(onClick = { vm.composeDraft(m) }) {
                    Icon(Icons.Outlined.Edit, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Edit")
                }
            } else if (!bin) {
                TextButton(onClick = { vm.composeReply(m) }) {
                    Icon(Icons.AutoMirrored.Outlined.Reply, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Reply")
                }
                TextButton(onClick = { vm.composeForward(m) }) {
                    Icon(Icons.AutoMirrored.Outlined.Forward, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Forward")
                }
            }
            Spacer(Modifier.weight(1f))
            if (bin) {
                IconButton(onClick = { vm.restoreMessage(m) }) { Icon(Icons.Outlined.RestoreFromTrash, "Restore this message") }
            }
            IconButton(onClick = {
                if (!bin && !isDraft) vm.deleteMessage(m, bin)
                else if (confirmDelete) vm.deleteMessage(m, bin)
                else confirmDelete = true
            }) {
                Icon(
                    Icons.Outlined.Delete,
                    when {
                        confirmDelete -> "Tap again to delete forever"
                        bin || isDraft -> "Delete forever"
                        else -> "Move to Bin"
                    },
                    tint = if (confirmDelete) Danger else Muted,
                )
            }
        }
    }
}

/** Saves an attachment to Downloads, with the system's own progress notification. */
private fun download(context: Context, api: Api, a: AttachmentInfo) {
    val name = a.filename.replace('/', '_')
    val request = DownloadManager.Request(Uri.parse(api.attachmentUrl(a.id)))
        .setTitle(a.filename)
        .setMimeType(a.contentType)
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
    // Public Downloads needs a storage permission before Android 10; earlier
    // versions keep it in the app's own folder, still opened from the notification.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
    } else {
        request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, name)
    }
    api.authorize(okhttp3.Request.Builder().url(api.attachmentUrl(a.id))).build()
        .header("Authorization")?.let { request.addRequestHeader("Authorization", it) }
    (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
    Toast.makeText(context, "Downloading ${a.filename}", Toast.LENGTH_SHORT).show()
}
