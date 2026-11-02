package gay.doughmination.manager.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gay.doughmination.manager.BuildConfig
import gay.doughmination.manager.data.AppStatus
import gay.doughmination.manager.data.Status
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagerApp(vm: ManagerViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var openPackage by rememberSaveable { mutableStateOf<String?>(null) }
    val open = state.rows.firstOrNull { it.app.packageName == openPackage }

    fun start(intent: Intent?) {
        if (intent == null) return
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            vm.messageFallback("Nothing on this phone can open that.")
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); vm.messageShown() }
    }

    if (state.needsInstallPermission) {
        AlertDialog(
            onDismissRequest = vm::permissionPromptHandled,
            title = { Text("Allow installs") },
            text = {
                Text(
                    "Android needs your permission before the Manager can install apps. " +
                        "Turn on \"Allow from this source\", then come back and tap install again.",
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.permissionPromptHandled(); start(vm.installPermissionIntent) }) {
                    Text("Open settings")
                }
            },
            dismissButton = { TextButton(onClick = vm::permissionPromptHandled) { Text("Not now") } },
        )
    }

    BackHandler(enabled = open != null) { openPackage = null }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(open?.app?.name ?: "Doughmination Manager", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (open == null) Freshness(state)
                    }
                },
                navigationIcon = {
                    if (open != null) {
                        IconButton(onClick = { openPackage = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    }
                },
                actions = {
                    if (open == null) {
                        IconButton(onClick = vm::refresh, enabled = !state.loading) {
                            Icon(Icons.Filled.Refresh, "Check for updates")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (open != null) {
                AppDetail(
                    row = open,
                    onInstall = { vm.install(open.app) },
                    onCancel = { vm.cancel(open.app) },
                    onDismiss = { vm.dismissFailure(open.app) },
                    onOpen = { start(vm.launchIntent(open.app)) },
                    onUninstall = { start(vm.uninstallIntent(open.app)) },
                    onLink = { start(Intent(Intent.ACTION_VIEW, Uri.parse(it))) },
                )
            } else {
                AppList(
                    state = state,
                    onRefresh = vm::refresh,
                    onSelect = { openPackage = it.app.packageName },
                    onInstall = { vm.install(it.app) },
                    onCancel = { vm.cancel(it.app) },
                    onOpen = { start(vm.launchIntent(it.app)) },
                    onUninstall = { start(vm.uninstallIntent(it.app)) },
                )
            }
        }
    }
}

@Composable
private fun Freshness(state: UiState) {
    val loaded = state.manifest
    val text = when {
        state.loading && loaded == null -> "Checking…"
        loaded == null -> null
        state.error != null || loaded.fromCache ->
            "Saved list from ${DateUtils.getRelativeTimeSpanString(loaded.fetchedAt)}"
        else -> "Checked ${DateUtils.getRelativeTimeSpanString(loaded.fetchedAt)}"
    }
    if (text != null) Text(text, style = MaterialTheme.typography.labelMedium, color = Muted)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppList(
    state: UiState,
    onRefresh: () -> Unit,
    onSelect: (AppRow) -> Unit,
    onInstall: (AppRow) -> Unit,
    onCancel: (AppRow) -> Unit,
    onOpen: (AppRow) -> Unit,
    onUninstall: (AppRow) -> Unit,
) {
    PullToRefreshBox(isRefreshing = state.loading && state.manifest != null, onRefresh = onRefresh) {
        when {
            state.manifest == null && state.loading -> Centered { CircularProgressIndicator() }
            state.manifest == null -> Centered {
                Icon(Icons.Filled.CloudOff, null, tint = Muted, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(12.dp))
                Text(state.error ?: "Couldn't load the app list.", color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onRefresh) { Text("Try again") }
            }
            state.rows.isEmpty() -> Centered { Text("No apps are published yet.", color = Muted) }
            else -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (state.error != null) {
                    item { Banner(state.error) }
                }
                val updates = state.rows.count { it.status == AppStatus.UpdateAvailable }
                if (updates > 0) {
                    item {
                        Text(
                            if (updates == 1) "1 update available" else "$updates updates available",
                            style = MaterialTheme.typography.titleSmall,
                            color = Accent,
                        )
                    }
                }
                items(state.rows, key = { it.app.packageName }) { row ->
                    AppCard(row, onSelect, onInstall, onCancel, onOpen, onUninstall)
                }
            }
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    // Scrollable, so pull-to-refresh works on the empty and error states too.
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) { content() }
}

@Composable
private fun Banner(text: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Warn.copy(alpha = 0.12f)),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CloudOff, null, tint = Warn)
            Spacer(Modifier.width(12.dp))
            Text("$text Showing the last list that loaded.", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun AppCard(
    row: AppRow,
    onSelect: (AppRow) -> Unit,
    onInstall: (AppRow) -> Unit,
    onCancel: (AppRow) -> Unit,
    onOpen: (AppRow) -> Unit,
    onUninstall: (AppRow) -> Unit,
) {
    Card(
        onClick = { onSelect(row) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(row)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(row.app.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        if (row.isSelf) {
                            Spacer(Modifier.width(8.dp))
                            Text("this app", style = MaterialTheme.typography.labelSmall, color = Muted)
                        }
                    }
                    Text(versionLine(row), style = MaterialTheme.typography.bodySmall, color = Muted)
                    Spacer(Modifier.height(4.dp))
                    StatusChip(row)
                }
                Spacer(Modifier.width(8.dp))
                ActionButton(row, onInstall, onCancel, onOpen, onUninstall)
            }
            TaskLine(row)
        }
    }
}

private fun versionLine(row: AppRow): String {
    val latest = row.app.latest.versionName
    val inst = row.installed ?: return "Latest $latest"
    val name = inst.versionName ?: inst.versionCode.toString()
    return if (row.status == AppStatus.UpdateAvailable) "$name → $latest" else name
}

@Composable
private fun AppIcon(row: AppRow) {
    val context = LocalContext.current
    // The installed app's own icon when there is one; otherwise its initial.
    val bitmap = remember(row.app.packageName, row.installed?.versionCode) {
        if (row.installed == null) null
        else runCatching {
            context.packageManager.getApplicationIcon(row.app.packageName).toBitmap(144, 144).asImageBitmap()
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(bitmap, null, Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)))
    } else {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(row.app.name.take(1).uppercase(), style = MaterialTheme.typography.titleLarge, color = Accent)
        }
    }
}

@Composable
private fun StatusChip(row: AppRow) {
    val (text, colour) = when (row.status) {
        AppStatus.NotInstalled -> "Not installed" to Muted
        AppStatus.UpToDate -> "Up to date" to Ok
        AppStatus.UpdateAvailable -> "Update available" to Accent
        AppStatus.InstalledNewer -> "Newer than the release" to Muted
        AppStatus.SignatureMismatch -> "Different signing key" to Bad
        AppStatus.Incompatible -> "Needs Android ${sdkName(row.app.latest.minSdk)}" to Warn
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(colour))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = colour)
    }
}

@Composable
private fun ActionButton(
    row: AppRow,
    onInstall: (AppRow) -> Unit,
    onCancel: (AppRow) -> Unit,
    onOpen: (AppRow) -> Unit,
    onUninstall: (AppRow) -> Unit,
) {
    val task = row.task
    when {
        task is Task.Downloading -> OutlinedButton(onClick = { onCancel(row) }) { Text("Cancel") }
        task == Task.Verifying || task == Task.Installing -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        Status.canInstall(row.status) -> Button(onClick = { onInstall(row) }) {
            Text(
                when {
                    task is Task.Failed -> "Retry"
                    row.status == AppStatus.UpdateAvailable -> "Update"
                    else -> "Install"
                },
            )
        }
        row.status == AppStatus.SignatureMismatch -> OutlinedButton(onClick = { onUninstall(row) }) { Text("Uninstall") }
        row.installed != null && !row.isSelf -> OutlinedButton(onClick = { onOpen(row) }) { Text("Open") }
    }
}

@Composable
private fun TaskLine(row: AppRow) {
    val context = LocalContext.current
    when (val task = row.task) {
        is Task.Downloading -> {
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(progress = { task.fraction }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(4.dp))
            Text(
                "Downloading ${Formatter.formatShortFileSize(context, task.done)} of " +
                    Formatter.formatShortFileSize(context, task.total),
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
            )
        }
        Task.Verifying -> TaskText("Checking the SHA-256 checksum and signature…")
        Task.Installing -> TaskText("Waiting for Android to install it…")
        is Task.Failed -> TaskText(task.message, Bad)
        null -> Unit
    }
}

@Composable
private fun TaskText(text: String, colour: Color = Muted) {
    Spacer(Modifier.height(10.dp))
    Text(text, style = MaterialTheme.typography.bodySmall, color = colour)
}

@Composable
private fun AppDetail(
    row: AppRow,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onUninstall: () -> Unit,
    onLink: (String) -> Unit,
) {
    val context = LocalContext.current
    val latest = row.app.latest
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(row)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(row.app.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                row.app.summary?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Muted) }
            }
        }
        Spacer(Modifier.height(16.dp))
        StatusChip(row)
        explanation(row)?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }
        TaskLine(row)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val task = row.task
            when {
                task is Task.Downloading -> OutlinedButton(onClick = onCancel) { Text("Cancel download") }
                task == Task.Verifying || task == Task.Installing -> Unit
                Status.canInstall(row.status) -> Button(onClick = onInstall) {
                    Text(if (row.status == AppStatus.UpdateAvailable) "Update to ${latest.versionName}" else "Install ${latest.versionName}")
                }
                row.status == AppStatus.SignatureMismatch -> Button(onClick = onUninstall) { Text("Uninstall the old copy") }
            }
            if (task is Task.Failed) TextButton(onClick = onDismiss) { Text("Dismiss") }
            if (row.installed != null && !row.isSelf) OutlinedButton(onClick = onOpen) { Text("Open") }
        }

        Section("Versions")
        Field("Installed", row.installed?.let { "${it.versionName ?: "?"} (${it.versionCode})" } ?: "Not installed")
        Field("Latest", "${latest.versionName} (${latest.versionCode})")
        latest.releasedAt?.let { Field("Built", relative(it)) }
        Field("Download", Formatter.formatShortFileSize(context, latest.sizeBytes))
        latest.minSdk?.let { Field("Needs", "Android ${sdkName(it)} or newer") }

        Section("What's new")
        Text(
            latest.releaseNotes?.takeIf { it.isNotBlank() }?.let(::plainNotes) ?: "No notes for this build.",
            style = MaterialTheme.typography.bodyMedium,
        )
        latest.releaseUrl?.let { url ->
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { onLink(url) }, contentPadding = PaddingValues(0.dp)) {
                Text("View the release")
                Spacer(Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(16.dp))
            }
        }

        Section("Integrity")
        Text(
            "Every download is checked against this SHA-256 and the signing certificate before Android is asked to install it.",
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
        )
        Spacer(Modifier.height(8.dp))
        SelectionContainer {
            Column {
                Field("Package", row.app.packageName, mono = true)
                Field("SHA-256", latest.sha256.lowercase(), mono = true)
                row.app.signingCertSha256.forEach { Field("Signing key", it, mono = true) }
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "Manager ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            style = MaterialTheme.typography.labelSmall,
            color = Muted,
        )
    }
}

private fun explanation(row: AppRow): String? = when (row.status) {
    AppStatus.SignatureMismatch ->
        "The installed copy was signed with a different key (probably from before the apps moved to new keys), " +
            "so Android won't update it in place. Uninstall it, then install the new build. Its data on this phone will be lost."
    AppStatus.Incompatible -> "This build needs Android ${sdkName(row.app.latest.minSdk)} or newer."
    AppStatus.InstalledNewer -> "The installed build is newer than the published one, so there's nothing to update."
    else -> null
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(24.dp))
    Text(title, style = MaterialTheme.typography.titleSmall, color = Accent)
    Spacer(Modifier.height(4.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun Field(label: String, value: String, mono: Boolean = false) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Muted)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = if (mono) FontFamily.Monospace else null,
        )
    }
}

private fun relative(iso: String): String = runCatching {
    DateUtils.getRelativeTimeSpanString(Instant.parse(iso).toEpochMilli()).toString()
}.getOrDefault(iso)

/** Release notes are a short Markdown list; show them as plain text. */
private fun plainNotes(md: String): String = md.lines().joinToString("\n") { line ->
    line.replaceFirst(Regex("^\\s*[-*] "), "• ").replace("**", "").replace("`", "")
}.trim()

private fun sdkName(sdk: Int?): String = when (sdk) {
    null -> "?"
    26 -> "8.0"; 27 -> "8.1"; 28 -> "9"; 29 -> "10"; 30 -> "11"; 31 -> "12"; 32 -> "12L"
    33 -> "13"; 34 -> "14"; 35 -> "15"; 36 -> "16"
    else -> "API $sdk"
}

