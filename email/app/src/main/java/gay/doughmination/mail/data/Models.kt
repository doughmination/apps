package gay.doughmination.mail.data

import kotlinx.serialization.Serializable

// Mirrors email/src/shared/types.ts. Keep the two in step.

@Serializable
data class Me(
    val username: String,
    /** Display name put on outgoing mail; null sends from the bare address. */
    val name: String? = null,
    val isAdmin: Boolean,
    /** Every address this user can send from. */
    val addresses: List<String>,
    val domains: List<String>,
)

@Serializable
data class Page<T>(val items: List<T>, val nextCursor: String? = null)

@Serializable
data class ThreadSummary(
    val id: String,
    val latestId: String,
    val mailbox: String,
    val subject: String,
    val from: String,
    val to: List<String>,
    val snippet: String,
    val lastAt: Long,
    val count: Int,
    val unread: Int,
    val hasAttachments: Boolean,
    val starred: Boolean,
)

@Serializable
data class AttachmentInfo(
    val id: String,
    val filename: String,
    val contentType: String,
    val size: Long,
    val contentId: String? = null,
)

@Serializable
data class Message(
    val id: String,
    val threadId: String,
    /** "inbox", "sent" or "drafts". */
    val folder: String,
    val mailbox: String,
    val from: String,
    val to: List<String>,
    val cc: List<String>,
    val bcc: List<String>,
    val subject: String,
    val snippet: String,
    /** Email HTML. Only ever render it in EmailView. */
    val html: String? = null,
    val text: String? = null,
    val sentAt: Long,
    val read: Boolean,
    val attachments: List<AttachmentInfo>,
)

@Serializable
data class MailThread(
    val id: String,
    val subject: String,
    val starred: Boolean,
    val messages: List<Message>,
)

@Serializable
data class SendRequest(
    val from: String,
    val to: List<String>,
    val cc: List<String>,
    val bcc: List<String>,
    val subject: String,
    /** Plain text; the server renders the HTML part from it. */
    val text: String,
    val attachmentIds: List<String>,
    /** Message being replied to; threads the new one onto it. */
    val replyTo: String? = null,
    /** Only for /send: the draft to update and send. */
    val draftId: String? = null,
)

/** What the thread list can show: a folder, Starred, or the Bin. */
enum class ListFolder(val path: String, val label: String) {
    Inbox("inbox", "Inbox"),
    Starred("starred", "Starred"),
    Sent("sent", "Sent"),
    Drafts("drafts", "Drafts"),
    Bin("bin", "Bin");

    companion object {
        fun fromPath(path: String?): ListFolder? = entries.find { it.path == path }
    }
}
