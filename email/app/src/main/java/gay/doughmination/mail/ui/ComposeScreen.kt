package gay.doughmination.mail.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeScreen(vm: MailViewModel) {
    val d = vm.draft ?: return
    val context = LocalContext.current
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) vm.attach(context, uris)
    }
    var showCopies by remember { mutableStateOf(d.cc.isNotBlank() || d.bcc.isNotBlank()) }
    var menu by remember { mutableStateOf(false) }
    val busy = vm.sending || vm.uploading > 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(d.title) },
                navigationIcon = {
                    IconButton(onClick = vm::closeAndSave) { Icon(Icons.Outlined.Close, "Close and save") }
                },
                actions = {
                    IconButton(onClick = { pick.launch("*/*") }) { Icon(Icons.Outlined.AttachFile, "Attach") }
                    if (busy) {
                        CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp))
                    } else {
                        IconButton(onClick = vm::send) { Icon(Icons.AutoMirrored.Outlined.Send, "Send", tint = Accent) }
                    }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Save draft") }, onClick = { menu = false; vm.closeAndSave() })
                        DropdownMenuItem(
                            text = { Text(if (d.draftId != null) "Delete draft" else "Discard", color = Danger) },
                            onClick = { menu = false; vm.discard() },
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(LocalSnackbar.current) },
    ) { padding ->
        Column(
            Modifier.padding(padding).imePadding().fillMaxSize().verticalScroll(rememberScrollState()),
        ) {
            FromPicker(vm.me?.addresses.orEmpty(), d.from) { vm.draft = d.copy(from = it) }
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Field("To", d.to, Modifier.weight(1f), KeyboardType.Email) { vm.draft = d.copy(to = it) }
                if (!showCopies) TextButton(onClick = { showCopies = true }) { Text("Cc/Bcc") }
            }
            HorizontalDivider()
            if (showCopies) {
                Field("Cc", d.cc, Modifier.fillMaxWidth(), KeyboardType.Email) { vm.draft = d.copy(cc = it) }
                HorizontalDivider()
                Field("Bcc", d.bcc, Modifier.fillMaxWidth(), KeyboardType.Email) { vm.draft = d.copy(bcc = it) }
                HorizontalDivider()
            }
            Field("Subject", d.subject, Modifier.fillMaxWidth(), KeyboardType.Text) { vm.draft = d.copy(subject = it) }
            HorizontalDivider()

            if (d.attachments.isNotEmpty() || vm.uploading > 0) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    for (a in d.attachments) {
                        InputChip(
                            selected = false,
                            onClick = { vm.removeAttachment(a) },
                            label = { Text("${a.filename} · ${fmtSize(a.size)}", maxLines = 1) },
                            trailingIcon = { Icon(Icons.Outlined.Close, "Remove", Modifier.size(18.dp)) },
                        )
                    }
                    if (vm.uploading > 0) {
                        Text("Uploading ${vm.uploading}…", style = MaterialTheme.typography.labelMedium, color = Muted)
                    }
                }
                HorizontalDivider()
            }

            TextField(
                value = d.text,
                onValueChange = { vm.draft = d.copy(text = it) },
                placeholder = { Text("Write your message") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 280.dp),
                colors = plainField(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
        }
    }
}

@Composable
private fun plainField() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
)

@Composable
private fun Field(label: String, value: String, modifier: Modifier, type: KeyboardType, onChange: (String) -> Unit) {
    TextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier,
        prefix = { Text("$label  ", color = Muted) },
        singleLine = type == KeyboardType.Text,
        colors = plainField(),
        keyboardOptions = KeyboardOptions(keyboardType = type, imeAction = ImeAction.Next),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FromPicker(addresses: List<String>, selected: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it && addresses.size > 1 }) {
        TextField(
            value = selected.ifEmpty { "No address to send from" },
            onValueChange = {},
            readOnly = true,
            prefix = { Text("From  ", color = Muted) },
            trailingIcon = { if (addresses.size > 1) ExposedDropdownMenuDefaults.TrailingIcon(open) },
            colors = plainField(),
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (addr in addresses) {
                DropdownMenuItem(text = { Text(addr) }, onClick = { open = false; onPick(addr) })
            }
        }
    }
}
