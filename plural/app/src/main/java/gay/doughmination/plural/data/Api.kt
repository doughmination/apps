package gay.doughmination.plural.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** The API said no, or couldn't be reached; `message` is fit to show. */
class ApiException(message: String, val status: Int) : Exception(message)

/**
 * The Doughmination API's plural system routes (/v2/plural/…). Reads are
 * public, exactly as the website makes them; writes carry the signed-in
 * user's token. Bodies come back bare, and errors as { detail }.
 */
class Api(val baseUrl: String, private val tokens: TokenStore, private val onSignedOut: () -> Unit) {
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @OptIn(ExperimentalSerializationApi::class)
    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        encodeDefaults = true
    }

    /** The realtime socket: https://…/v2 -> wss://…/v2/ws, as react-api derives it. */
    val socketUrl: String get() = baseUrl.replaceFirst("http", "ws") + "/ws"

    private val jsonType = "application/json".toMediaType()

    /** A URL under /plural, each segment encoded (member names can hold anything). */
    private fun plural(vararg segments: String): HttpUrl.Builder =
        "$baseUrl/plural".toHttpUrl().newBuilder().apply { segments.forEach { addPathSegment(it) } }

    private suspend fun send(request: Request, signedIn: Boolean): String = withContext(Dispatchers.IO) {
        val builder = request.newBuilder()
        if (signedIn) {
            val token = tokens.token ?: throw ApiException("Please sign in again.", 401)
            builder.header("Authorization", "Bearer $token")
        }
        val response = try {
            http.newCall(builder.build()).execute()
        } catch (e: IOException) {
            throw ApiException("Couldn't reach the server. Check your connection.", 0)
        }
        response.use { res ->
            val body = res.body?.string().orEmpty()
            if (signedIn && res.code == 401) {
                tokens.token = null
                onSignedOut()
                throw ApiException(errorMessage(body) ?: "Your session ended. Please sign in again.", 401)
            }
            if (!res.isSuccessful) throw ApiException(errorMessage(body) ?: "Request failed (${res.code})", res.code)
            body
        }
    }

    /** The API's own message: { detail } (a string, or zod issues), { error: { message } } or { message }. */
    private fun errorMessage(body: String): String? {
        val obj = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        fun text(element: JsonElement?): String? = when (element) {
            is JsonPrimitive -> element.contentOrNull
            is JsonArray -> element.mapNotNull { (it as? JsonObject)?.get("message")?.let(::text) }
                .joinToString("; ").ifBlank { null }
            is JsonObject -> text(element["message"])
            else -> null
        }
        return text(obj["detail"]) ?: text(obj["error"]) ?: text(obj["message"])
    }

    private suspend inline fun <reified T> get(url: HttpUrl, signedIn: Boolean = false): T =
        json.decodeFromString(send(Request.Builder().url(url).build(), signedIn))

    private suspend inline fun <reified B> write(method: String, url: HttpUrl, body: B): String =
        send(Request.Builder().url(url).method(method, json.encodeToString(body).toRequestBody(jsonType)).build(), true)

    private suspend fun delete(url: HttpUrl): String = send(Request.Builder().url(url).delete().build(), true)

    // ---- Public reads ---------------------------------------------------------

    suspend fun members(): List<Member> = get(plural("members").build())
    suspend fun member(identifier: String): Member = get(plural("member", identifier).build())
    suspend fun memberStatus(identifier: String): MemberStatus? =
        get<MemberStatusResponse>(plural("members", identifier, "status").build()).status
    suspend fun fronters(): Fronters = get(plural("fronters").build())
    suspend fun system(): PluralSystem = get(plural("system").build())
    suspend fun mentalState(): MentalState = get(plural("mental-state").build())
    suspend fun relationships(): List<Relationship> =
        get<RelationshipsResponse>(plural("relationships").build()).relationships
    suspend fun frontingMetrics(days: Int = 30): FrontingMetrics =
        get(plural("metrics", "fronting-time").addQueryParameter("days", "$days").build())
    suspend fun switchMetrics(days: Int = 30): SwitchMetrics =
        get(plural("metrics", "switch-frequency").addQueryParameter("days", "$days").build())

    // ---- Sign-in ----------------------------------------------------------------

    /** Where a native sign-in starts (see the API's routes/auth.ts). */
    fun appLoginUrl(challenge: String): String =
        plural("auth", "sso", "login").addQueryParameter("app", challenge).build().toString()

    @Serializable private data class CodeSwap(val code: String, val verifier: String)
    @Serializable private data class AccessToken(val access_token: String)

    /** Swaps the one-time code from the sign-in redirect, plus our PKCE verifier, for the token. */
    suspend fun exchangeCode(code: String, verifier: String): String {
        val request = Request.Builder().url(plural("auth", "app", "token").build())
            .post(json.encodeToString(CodeSwap(code, verifier)).toRequestBody(jsonType)).build()
        return json.decodeFromString<AccessToken>(send(request, false)).access_token
    }

    /** Ends the session on the API too. Best effort: signing out here stands either way. */
    suspend fun logout(token: String) {
        runCatching {
            withContext(Dispatchers.IO) {
                http.newCall(
                    Request.Builder().url(plural("auth", "logout").build())
                        .header("Authorization", "Bearer $token")
                        .post("{}".toRequestBody(jsonType)).build(),
                ).execute().close()
            }
        }
    }

    suspend fun userInfo(): User = get(plural("user_info").build(), signedIn = true)

    // ---- Admin writes (the website's /admin pages) --------------------------------

    @Serializable private data class MemberIds(val member_ids: List<String>)
    @Serializable private data class MentalInput(val level: String, val notes: String?)
    @Serializable private data class StatusInput(val text: String, val emoji: String?)
    @Serializable private data class TagInput(val tag: String)
    @Serializable private data class PrideInput(val identity: String)
    @Serializable private data class RelationshipInput(
        val memberA: String,
        val memberB: String,
        val type: String,
        val since: String?,
    )

    suspend fun multiSwitch(memberIds: List<String>) { write("POST", plural("multi_switch").build(), MemberIds(memberIds)) }
    suspend fun setMentalState(level: String, notes: String?) {
        write("POST", plural("mental-state").build(), MentalInput(level, notes))
    }
    suspend fun setStatus(member: String, text: String, emoji: String?) {
        write("POST", plural("members", member, "status").build(), StatusInput(text, emoji))
    }
    suspend fun clearStatus(member: String) { delete(plural("members", member, "status").build()) }
    suspend fun addTag(member: String, tag: String) { write("POST", plural("member-tags", member, "add").build(), TagInput(tag)) }
    suspend fun removeTag(member: String, tag: String) { delete(plural("member-tags", member, tag).build()) }
    suspend fun addPride(member: String, identity: String) {
        write("POST", plural("member-pride", member, "add").build(), PrideInput(identity))
    }
    suspend fun removePride(member: String, identity: String) { delete(plural("member-pride", member, identity).build()) }
    suspend fun addRelationship(memberA: String, memberB: String, type: String, since: String?) {
        write("POST", plural("relationships").build(), RelationshipInput(memberA, memberB, type, since))
    }
    suspend fun removeRelationship(id: String) { delete(plural("relationships", id).build()) }
    suspend fun forceRefresh() { write("POST", plural("admin", "refresh").build(), JsonObject(emptyMap())) }

    // ---- Users (profile, and the owner's user management) ---------------------------

    /**
     * A user update as the website sends it: blank text clears a field with an
     * explicit null, and fields left out (email, unless it changed; the role
     * switches, except for admins) are untouched.
     */
    class UserUpdate(
        val displayName: String?,
        val avatarUrl: String?,
        val email: Optional<String?> = Optional.Unset,
        val isAdmin: Boolean? = null,
        val isPet: Boolean? = null,
    ) {
        fun toJson(): JsonObject = buildJsonObject {
            put("display_name", displayName)
            put("avatar_url", avatarUrl)
            if (email is Optional.Set) put("email", email.value)
            isAdmin?.let { put("is_admin", it) }
            isPet?.let { put("is_pet", it) }
        }
    }

    /** A field that is either left out of a request or sent, possibly as null. */
    sealed interface Optional<out T> {
        data object Unset : Optional<Nothing>
        data class Set<T>(val value: T) : Optional<T>
    }

    @Serializable
    data class UserCreate(
        val username: String,
        val display_name: String?,
        val email: String?,
        val is_admin: Boolean,
        val is_pet: Boolean,
    )

    suspend fun users(): List<User> = get(plural("users").build(), signedIn = true)
    suspend fun updateUser(id: String, update: UserUpdate): User =
        json.decodeFromString(write("PUT", plural("users", id).build(), update.toJson()))
    suspend fun createUser(input: UserCreate) { write("POST", plural("users").build(), input) }
    suspend fun deleteUser(id: String) { delete(plural("users", id).build()) }

    /** Reads a socket event's type, or null when it isn't one. */
    fun eventType(text: String): String? =
        runCatching { json.parseToJsonElement(text).jsonObject["type"]?.jsonPrimitive?.contentOrNull }.getOrNull()
}
