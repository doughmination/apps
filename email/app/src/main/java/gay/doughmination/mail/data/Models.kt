package gay.doughmination.mail.data

import kotlinx.serialization.SerialName
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
    /** The account's picture on the SSO (https), whether or not it's in use. */
    val picture: String? = null,
    val usePicture: Boolean = true,
    val swipeLeft: SwipeAction = SwipeAction.Bin,
    val swipeRight: SwipeAction = SwipeAction.Read,
) {
    val preferences get() = Preferences(usePicture, swipeLeft, swipeRight)

    /** The picture to show for the account, or null for the default (an initial). */
    val shownPicture get() = picture?.takeIf { usePicture }
}

/** What swiping a row does. An unknown value (a newer server) reads as None. */
@Serializable
enum class SwipeAction(val label: String) {
    @SerialName("none") None("Nothing"),
    @SerialName("read") Read("Mark as read or unread"),
    @SerialName("star") Star("Star or unstar"),
    @SerialName("bin") Bin("Move to Bin"),
}

/** Settings that follow the user between the web and the app. */
@Serializable
data class Preferences(val usePicture: Boolean, val swipeLeft: SwipeAction, val swipeRight: SwipeAction)

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
