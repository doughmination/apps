package gay.doughmination.mail.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import gay.doughmination.mail.InboxApp
import gay.doughmination.mail.data.ApiException
import gay.doughmination.mail.data.AttachmentInfo
import gay.doughmination.mail.data.ListFolder
import gay.doughmination.mail.data.MailThread
import gay.doughmination.mail.data.Me
import gay.doughmination.mail.data.Message
import gay.doughmination.mail.data.SendRequest
import gay.doughmination.mail.data.SignedOutException
import gay.doughmination.mail.data.ThreadSummary
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.io.IOException

sealed interface Screen {
    data object List : Screen
    data class Thread(val id: String, val bin: Boolean) : Screen
    data object Compose : Screen
    data object Settings : Screen
}

/** The composer's contents. Recipients stay as typed until they're sent or saved. */
data class Draft(
    val title: String,
    val draftId: String? = null,
    val replyTo: String? = null,
    val from: String,
    val to: String = "",
    val cc: String = "",
    val bcc: String = "",
    val subject: String = "",
    val text: String = "",
    val attachments: List<AttachmentInfo> = emptyList(),
) {
    val isBlank get() = draftId == null && to.isBlank() && cc.isBlank() && bcc.isBlank() &&
        subject.isBlank() && text.isBlank() && attachments.isEmpty()
}

data class Snack(val text: String, val action: String? = null, val run: (() -> Unit)? = null)

class MailViewModel(application: Application) : AndroidViewModel(application) {
    private val inbox = application as InboxApp
    private val api = inbox.api

    var signedIn by mutableStateOf(inbox.signedIn.value)
        private set
    var signingIn by mutableStateOf(false)
        private set
    var loginError by mutableStateOf<String?>(null)
        private set

    var me by mutableStateOf<Me?>(null)
        private set
    /** Unread inbox mail per receiving address. */
    var unread by mutableStateOf<Map<String, Int>>(emptyMap())
        private set

    var folder by mutableStateOf(ListFolder.Inbox)
        private set
    /** One address, "@domain" for a whole domain, or null for everything. */
    var mailbox by mutableStateOf<String?>(null)
        private set
    val threads = mutableStateListOf<ThreadSummary>()
    var nextCursor by mutableStateOf<String?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var refreshing by mutableStateOf(false)
        private set
    var listError by mutableStateOf<String?>(null)
        private set

    val stack = mutableStateListOf<Screen>(Screen.List)
    val screen: Screen get() = stack.last()

    var thread by mutableStateOf<MailThread?>(null)
        private set
    var threadError by mutableStateOf<String?>(null)
        private set
    /** Bumped when the open thread should reload. */
    var threadVersion by mutableIntStateOf(0)
        private set

    var draft by mutableStateOf<Draft?>(null)
    var uploading by mutableIntStateOf(0)
        private set
    var sending by mutableStateOf(false)
        private set

    private val snacks = Channel<Snack>(Channel.BUFFERED)
    val snackbar = snacks.receiveAsFlow()

    private var listJob: Job? = null

    init {
        viewModelScope.launch {
            inbox.signedIn.collect { value ->
                signedIn = value
                if (value) loadSession() else signedOutReset()
            }
        }
        viewModelScope.launch {
            inbox.live.events.collect { threadId ->
                refreshUnread()
                reload(quiet = true)
                val open = screen
                if (open is Screen.Thread && (threadId == null || threadId == open.id)) threadVersion++
            }
        }
    }

    // ---- errors

    private fun fail(e: Throwable) {
        val text = when (e) {
            is SignedOutException -> return
            is kotlinx.coroutines.CancellationException -> throw e
            is ApiException -> e.message ?: "Something went wrong."
            is IOException -> "Couldn't reach the server."
            else -> e.message ?: "Something went wrong."
        }
        snacks.trySend(Snack(text))
    }

    private fun say(text: String, action: String? = null, run: (() -> Unit)? = null) {
        snacks.trySend(Snack(text, action, run))
    }

    // ---- signing in and out

    fun startLogin(context: Context) {
        loginError = null
        runCatching { inbox.auth.start(context) }.onFailure { loginError = "No browser to sign in with." }
    }

    fun finishLogin(code: String) = viewModelScope.launch {
        signingIn = true
        loginError = null
        try {
            inbox.auth.finish(code)
            inbox.setSignedIn(true)
        } catch (e: Exception) {
            loginError = (e as? ApiException)?.message ?: "Couldn't reach the server."
        } finally {
            signingIn = false
        }
    }

    fun signOut() = viewModelScope.launch {
        api.logout()
        inbox.setSignedIn(false)
    }

    private fun signedOutReset() {
        inbox.live.stop()
        me = null
        unread = emptyMap()
        threads.clear()
        nextCursor = null
        thread = null
        draft = null
        stack.clear()
        stack.add(Screen.List)
    }

    private fun loadSession() = viewModelScope.launch {
        try {
            me = api.me()
            refreshUnread()
        } catch (e: Exception) {
            fail(e)
        }
        reload()
    }

    private suspend fun refreshUnread() {
        runCatching { api.unread() }.onSuccess { unread = it }
    }

    // ---- the list

    fun show(folder: ListFolder, mailbox: String? = this.mailbox) {
        this.folder = folder
        this.mailbox = mailbox
        stack.clear()
        stack.add(Screen.List)
        threads.clear()
        nextCursor = null
        reload()
    }

    /** Fetches the first page again. `quiet` keeps the spinner off (live updates). */
    fun reload(quiet: Boolean = false, pulled: Boolean = false) {
        listJob?.cancel()
        listJob = viewModelScope.launch {
            if (pulled) refreshing = true else if (!quiet) loading = true
            try {
                val page = api.threads(folder, mailbox, null)
                threads.clear()
                threads.addAll(page.items)
                nextCursor = page.nextCursor
                listError = null
                if (pulled) refreshUnread()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (threads.isEmpty()) listError = "Couldn't load your mail." else fail(e)
            } finally {
                loading = false
                refreshing = false
            }
        }
    }

    fun loadMore() {
        val cursor = nextCursor ?: return
        if (loading || listJob?.isActive == true) return
        listJob = viewModelScope.launch {
            loading = true
            try {
                val page = api.threads(folder, mailbox, cursor)
                val seen = threads.map { it.id }.toSet()
                threads.addAll(page.items.filter { it.id !in seen })
                nextCursor = page.nextCursor
            } catch (e: Exception) {
                fail(e)
            } finally {
                loading = false
            }
        }
    }

    private fun replace(id: String, change: (ThreadSummary) -> ThreadSummary) {
        val i = threads.indexOfFirst { it.id == id }
        if (i >= 0) threads[i] = change(threads[i])
    }

    private fun takeOut(id: String) {
        threads.removeAll { it.id == id }
    }

    fun toggleRead(t: ThreadSummary) {
        val read = t.unread > 0
        replace(t.id) { it.copy(unread = if (read) 0 else 1) }
        viewModelScope.launch {
            try {
                unread = api.markThread(t.id, read)
            } catch (e: Exception) {
                fail(e)
                reload(quiet = true)
            }
        }
    }

    fun toggleStar(threadId: String, starred: Boolean) {
        if (folder == ListFolder.Starred && !starred) takeOut(threadId) else replace(threadId) { it.copy(starred = starred) }
        thread?.takeIf { it.id == threadId }?.let { thread = it.copy(starred = starred) }
        viewModelScope.launch {
            try {
                api.starThread(threadId, starred)
            } catch (e: Exception) {
                fail(e)
                reload(quiet = true)
            }
        }
    }

    fun moveToBin(threadId: String) {
        takeOut(threadId)
        if ((screen as? Screen.Thread)?.id == threadId) stack.removeAt(stack.lastIndex)
        viewModelScope.launch {
            try {
                api.deleteThread(threadId)
                refreshUnread()
                say("Moved to Bin", "Undo") { restore(threadId, undo = true) }
            } catch (e: Exception) {
                fail(e)
                reload(quiet = true)
            }
        }
    }

    fun restore(threadId: String, undo: Boolean = false) {
        if (!undo) takeOut(threadId)
        if ((screen as? Screen.Thread)?.id == threadId) stack.removeAt(stack.lastIndex)
        viewModelScope.launch {
            try {
                api.restoreThread(threadId)
                refreshUnread()
                if (undo) reload(quiet = true) else say("Restored")
            } catch (e: Exception) {
                fail(e)
                reload(quiet = true)
            }
        }
    }

    fun deleteForever(threadId: String) {
        takeOut(threadId)
        if ((screen as? Screen.Thread)?.id == threadId) stack.removeAt(stack.lastIndex)
        viewModelScope.launch {
            try {
                api.deleteThread(threadId, forever = true)
            } catch (e: Exception) {
                fail(e)
                reload(quiet = true)
            }
        }
    }

    fun emptyBin() = viewModelScope.launch {
        try {
            val n = api.emptyBin()
            threads.clear()
            nextCursor = null
            say(if (n == 1) "Deleted 1 message" else "Deleted $n messages")
        } catch (e: Exception) {
            fail(e)
        }
    }

    /** Drafts open straight in the composer; everything else in the thread view. */
    fun open(t: ThreadSummary) {
        if (folder == ListFolder.Drafts) {
            editDraft(t.latestId)
            return
        }
        replace(t.id) { it.copy(unread = 0) }
        openThread(t.id, folder == ListFolder.Bin)
    }

    fun openThread(id: String, bin: Boolean) {
        thread = null
        threadError = null
        stack.add(Screen.Thread(id, bin))
    }

    /** For links to the site: /inbox/<thread id> and the like. */
    fun openPath(path: String) {
        val parts = path.split("/").filter { it.isNotEmpty() }
        val target = ListFolder.fromPath(parts.firstOrNull()) ?: ListFolder.Inbox
        if (target != folder || mailbox != null) show(target, null)
        parts.getOrNull(1)?.let { openThread(it, target == ListFolder.Bin) }
    }

    // ---- the thread view

    fun loadThread(id: String, bin: Boolean) = viewModelScope.launch {
        try {
            val loaded = api.thread(id, bin)
            thread = loaded
            threadError = null
            if (!bin && loaded.messages.any { it.folder == "inbox" && !it.read }) {
                unread = api.markThread(id, true)
                replace(id) { it.copy(unread = 0) }
            }
        } catch (e: Exception) {
            if (e is ApiException && e.status == 404) {
                threadError = "This conversation is gone."
            } else if (thread == null) {
                threadError = "Couldn't load this conversation."
            } else {
                fail(e)
            }
        }
    }

    fun markUnread(threadId: String) {
        stack.removeAt(stack.lastIndex)
        replace(threadId) { it.copy(unread = 1) }
        viewModelScope.launch {
            try {
                unread = api.markThread(threadId, false)
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    /** In the Bin a message is deleted for good; elsewhere it moves to the Bin. */
    fun deleteMessage(m: Message, bin: Boolean) = viewModelScope.launch {
        try {
            api.deleteMessage(m.id, forever = bin || m.folder == "drafts")
            afterMessageChange(m.threadId)
            if (!bin && m.folder != "drafts") say("Moved to Bin", "Undo") { restoreMessage(m, undo = true) }
        } catch (e: Exception) {
            fail(e)
        }
    }

    fun restoreMessage(m: Message, undo: Boolean = false) = viewModelScope.launch {
        try {
            api.restoreMessage(m.id)
            afterMessageChange(m.threadId)
            if (!undo) say("Restored")
        } catch (e: Exception) {
            fail(e)
        }
    }

    private suspend fun afterMessageChange(threadId: String) {
        val open = screen as? Screen.Thread
        if (open?.id == threadId) {
            val left = runCatching { api.thread(threadId, open.bin) }.getOrNull()
            if (left == null) stack.removeAt(stack.lastIndex) else thread = left
        }
        refreshUnread()
        reload(quiet = true)
    }

    // ---- composing

    private fun defaultFrom(): String = me?.addresses?.firstOrNull() ?: ""

    /** The address of ours a message was sent to, so a reply goes out from it. */
    private fun ourAddress(m: Message): String =
        if (me?.addresses?.contains(m.mailbox) == true) m.mailbox else defaultFrom()

    private fun prefixed(prefix: String, subject: String): String =
        if (Regex("^\\s*$prefix\\s*:", RegexOption.IGNORE_CASE).containsMatchIn(subject)) subject else "$prefix: $subject"

    private fun quote(m: Message): String {
        val body = (m.text ?: "").lines().joinToString("\n") { "> $it" }
        return "\n\nOn ${fmtDateTime(m.sentAt)}, ${m.from} wrote:\n$body"
    }

    private fun openComposer(d: Draft) {
        draft = d
        stack.add(Screen.Compose)
    }

    fun composeNew() = openComposer(Draft(title = "New message", from = defaultFrom()))

    fun composeReply(m: Message) = openComposer(
        Draft(
            title = "Reply",
            replyTo = m.id,
            from = ourAddress(m),
            to = (if (m.folder != "inbox") m.to else listOf(m.from)).joinToString(", "),
            subject = prefixed("Re", m.subject),
            text = quote(m),
        ),
    )

    fun composeForward(m: Message) = openComposer(
        Draft(
            title = "Forward",
            from = ourAddress(m),
            subject = prefixed("Fwd", m.subject),
            text = "\n\n---------- Forwarded message ----------\nFrom: ${m.from}\nTo: ${m.to.joinToString(", ")}\n" +
                "Subject: ${m.subject}\n\n${m.text ?: ""}",
            attachments = m.attachments.filter { it.contentId == null },
        ),
    )

    fun composeDraft(m: Message) = openComposer(
        Draft(
            title = "Draft",
            draftId = m.id,
            from = m.from,
            to = m.to.joinToString(", "),
            cc = m.cc.joinToString(", "),
            bcc = m.bcc.joinToString(", "),
            subject = m.subject,
            text = m.text ?: "",
            attachments = m.attachments,
        ),
    )

    fun editDraft(messageId: String) = viewModelScope.launch {
        try {
            composeDraft(api.message(messageId))
        } catch (e: Exception) {
            fail(e)
        }
    }

    fun attach(context: Context, uris: List<Uri>) {
        for (uri in uris) {
            viewModelScope.launch {
                uploading++
                try {
                    val info = api.upload(context.contentResolver, uri)
                    draft = draft?.let { it.copy(attachments = it.attachments + info) }
                } catch (e: Exception) {
                    fail(e)
                } finally {
                    uploading--
                }
            }
        }
    }

    fun removeAttachment(a: AttachmentInfo) {
        draft = draft?.let { it.copy(attachments = it.attachments - a) }
    }

    private fun request(d: Draft, keep: (String) -> Boolean): SendRequest = SendRequest(
        from = d.from,
        to = splitList(d.to).filter(keep),
        cc = splitList(d.cc).filter(keep),
        bcc = splitList(d.bcc).filter(keep),
        subject = d.subject,
        text = d.text,
        attachmentIds = d.attachments.map { it.id },
        replyTo = d.replyTo,
    )

    fun send() {
        val d = draft ?: return
        if (uploading > 0 || sending) return
        val bad = (splitList(d.to) + splitList(d.cc) + splitList(d.bcc)).firstOrNull { !isAddress(it) }
        if (bad != null) return say("$bad isn't an email address.")
        if (splitList(d.to).isEmpty()) return say("Add at least one recipient.")
        viewModelScope.launch {
            sending = true
            try {
                api.send(request(d) { true }.copy(draftId = d.draftId))
                closeComposer()
                say("Sent")
                reload(quiet = true)
                threadVersion++
            } catch (e: Exception) {
                // The server keeps it as a draft when Resend refuses it.
                (e as? ApiException)?.draftId?.let { id -> draft = draft?.copy(draftId = id) }
                fail(e)
            } finally {
                sending = false
            }
        }
    }

    /** Back out of the composer: anything written is kept in Drafts. */
    fun closeAndSave() {
        val d = draft ?: return
        if (d.isBlank) return closeComposer()
        if (uploading > 0) return say("Wait for attachments to finish uploading.")
        viewModelScope.launch {
            // Half-typed addresses would be refused, so the draft keeps only the whole ones.
            val all = splitList(d.to) + splitList(d.cc) + splitList(d.bcc)
            val req = request(d, ::isAddress)
            try {
                if (d.draftId != null) api.updateDraft(d.draftId, req) else api.createDraft(req)
                closeComposer()
                val dropped = all.count { !isAddress(it) }
                say(if (dropped > 0) "Saved to Drafts, without $dropped unfinished address(es)" else "Saved to Drafts")
                reload(quiet = true)
                threadVersion++
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    fun discard() {
        val id = draft?.draftId
        closeComposer()
        if (id == null) return
        viewModelScope.launch {
            try {
                api.deleteMessage(id, forever = true)
                say("Draft discarded")
                reload(quiet = true)
                threadVersion++
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    private fun closeComposer() {
        if (screen == Screen.Compose) stack.removeAt(stack.lastIndex)
        draft = null
    }

    // ---- settings and navigation

    fun openSettings() {
        stack.add(Screen.Settings)
    }

    fun saveName(name: String) = viewModelScope.launch {
        try {
            api.setName(name.trim())
            me = me?.copy(name = name.trim().ifEmpty { null })
            say("Saved")
        } catch (e: Exception) {
            fail(e)
        }
    }

    /** Returns false when there's nowhere to go back to. */
    fun back(): Boolean {
        when {
            screen == Screen.Compose -> closeAndSave()
            stack.size > 1 -> stack.removeAt(stack.lastIndex)
            else -> return false
        }
        return true
    }
}
