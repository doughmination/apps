package gay.doughmination.mail.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import java.util.concurrent.TimeUnit

/** The server said no; `message` is fit to show. `draftId` is set when a failed send left a draft. */
class ApiException(message: String, val status: Int, val draftId: String? = null) : Exception(message)

/** The session is gone (expired, signed out elsewhere, account removed). */
class SignedOutException : Exception("Signed out")

/** The Worker's /api and /auth endpoints, signed in with the bearer token. */
class Api(val baseUrl: String, private val tokens: TokenStore, private val onSignedOut: () -> Unit) {
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val jsonType = "application/json".toMediaType()

    fun url(path: String): HttpUrl = "$baseUrl$path".toHttpUrl()

    /** Adds the session token; for requests made outside this class (the WebView, downloads, the socket). */
    fun authorize(builder: Request.Builder): Request.Builder =
        tokens.token?.let { builder.header("Authorization", "Bearer $it") } ?: builder

    private suspend fun send(request: Request): String = withContext(Dispatchers.IO) {
        if (tokens.token == null) throw SignedOutException()
        http.newCall(authorize(request.newBuilder()).build()).execute().use { res ->
            val body = res.body?.string().orEmpty()
            if (res.code == 401) {
                tokens.token = null
                onSignedOut()
                throw SignedOutException()
            }
            if (!res.isSuccessful) {
                val obj = runCatching { json.decodeFromString<JsonObject>(body) }.getOrNull()
                throw ApiException(
                    obj?.get("error")?.jsonPrimitive?.contentOrNull ?: "Request failed (${res.code})",
                    res.code,
                    obj?.get("draftId")?.jsonPrimitive?.contentOrNull,
                )
            }
            body
        }
    }

    private suspend inline fun <reified T> get(path: String): T =
        json.decodeFromString(send(Request.Builder().url(url(path)).build()))

    private suspend inline fun <reified T, reified B> call(method: String, path: String, body: B): T {
        val payload = json.encodeToString(body).toRequestBody(jsonType)
        return json.decodeFromString(send(Request.Builder().url(url(path)).method(method, payload).build()))
    }

    private suspend inline fun <reified T> call(method: String, path: String): T {
        val payload = if (method == "DELETE") null else "{}".toRequestBody(jsonType)
        return json.decodeFromString(send(Request.Builder().url(url(path)).method(method, payload).build()))
    }

    @Serializable data class Unread(val unread: Map<String, Int>)
    @Serializable data class Count(val count: Int)
    @Serializable data class Id(val id: String)
    @Serializable data class Sent(val id: String, val threadId: String)
    @Serializable private data class Read(val read: Boolean)
    @Serializable private data class Star(val starred: Boolean)
    @Serializable private data class Name(val name: String)
    @Serializable data class PushResult(val sent: Int, val pruned: Int = 0)
    @Serializable private data class FcmToken(val token: String)
    @Serializable private data class CodeSwap(val code: String, val verifier: String)
    @Serializable private data class Token(val token: String)

    suspend fun me(): Me = get("/api/me")
    suspend fun unread(): Map<String, Int> = get<Unread>("/api/unread").unread
    suspend fun setName(name: String) { call<JsonObject, Name>("PATCH", "/api/me", Name(name)) }

    suspend fun threads(folder: ListFolder, mailbox: String?, cursor: String?): Page<ThreadSummary> {
        val q = url("/api/threads").newBuilder().addQueryParameter("folder", folder.path)
        mailbox?.let { q.addQueryParameter("mailbox", it) }
        cursor?.let { q.addQueryParameter("cursor", it) }
        return json.decodeFromString(send(Request.Builder().url(q.build()).build()))
    }

    /** With `bin`, only the part of the thread that's in the Bin. */
    suspend fun thread(id: String, bin: Boolean): MailThread = get("/api/threads/$id${if (bin) "?bin=1" else ""}")
    suspend fun message(id: String): Message = get("/api/messages/$id")
    suspend fun markThread(id: String, read: Boolean): Map<String, Int> =
        call<Unread, Read>("POST", "/api/threads/$id/read", Read(read)).unread
    suspend fun starThread(id: String, starred: Boolean) { call<JsonObject, Star>("POST", "/api/threads/$id/star", Star(starred)) }
    /** Moves it to the Bin; `forever` deletes what of it is already in the Bin. */
    suspend fun deleteThread(id: String, forever: Boolean = false): Int =
        call<Count>("DELETE", "/api/threads/$id${if (forever) "?forever=1" else ""}").count
    suspend fun restoreThread(id: String): Int = call<Count>("POST", "/api/threads/$id/restore").count
    suspend fun emptyBin(): Int = call<Count>("DELETE", "/api/bin").count
    /** Moves it to the Bin; `forever` deletes it outright, wherever it is. */
    suspend fun deleteMessage(id: String, forever: Boolean = false): Int =
        call<Count>("DELETE", "/api/messages/$id${if (forever) "?forever=1" else ""}").count
    suspend fun restoreMessage(id: String): Int = call<Count>("POST", "/api/messages/$id/restore").count

    suspend fun createDraft(input: SendRequest): String = call<Id, SendRequest>("POST", "/api/drafts", input).id
    suspend fun updateDraft(id: String, input: SendRequest): String = call<Id, SendRequest>("PUT", "/api/drafts/$id", input).id
    suspend fun send(input: SendRequest): Sent = call("POST", "/api/send", input)

    suspend fun registerFcm(token: String) { call<JsonObject, FcmToken>("POST", "/api/push/fcm", FcmToken(token)) }
    suspend fun unregisterFcm(token: String) { call<JsonObject, FcmToken>("POST", "/api/push/fcm/unregister", FcmToken(token)) }
    /** Sends a test notification to every device; returns how many it reached. */
    suspend fun pushTest(): Int = call<PushResult>("POST", "/api/push/test").sent

    fun attachmentUrl(id: String, inline: Boolean = false): String =
        "$baseUrl/api/attachments/$id${if (inline) "?inline=1" else ""}"

    /** Streams a picked file up as an unclaimed attachment. */
    suspend fun upload(resolver: ContentResolver, uri: Uri): AttachmentInfo {
        var name = "attachment"
        var size = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getString(0)?.let { name = it }
                if (!c.isNull(1)) size = c.getLong(1)
            }
        }
        val type = resolver.getType(uri)?.toMediaTypeOrNull() ?: "application/octet-stream".toMediaType()
        val file = object : RequestBody() {
            override fun contentType(): MediaType = type
            override fun contentLength(): Long = size
            override fun writeTo(sink: BufferedSink) {
                resolver.openInputStream(uri)?.source()?.use { sink.writeAll(it) }
                    ?: throw java.io.IOException("Couldn't read $name")
            }
        }
        val form = MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("file", name, file).build()
        return json.decodeFromString(send(Request.Builder().url(url("/api/uploads")).post(form).build()))
    }

    /** Ends the session on the server. The token is dropped locally whatever happens. */
    suspend fun logout() {
        runCatching { call<JsonObject>("POST", "/auth/logout") }
        tokens.token = null
    }

    /** Swaps the one-time code from the sign-in redirect for a session token. */
    suspend fun exchangeCode(code: String, verifier: String): String = withContext(Dispatchers.IO) {
        val payload = json.encodeToString(CodeSwap(code, verifier)).toRequestBody(jsonType)
        http.newCall(Request.Builder().url(url("/auth/app/token")).post(payload).build()).execute().use { res ->
            val body = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw ApiException("That sign-in didn't work. Try again.", res.code)
            json.decodeFromString<Token>(body).token
        }
    }
}
