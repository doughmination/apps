package gay.doughmination.plural.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import gay.doughmination.plural.SystemApp
import gay.doughmination.plural.data.Api
import gay.doughmination.plural.data.ApiException
import gay.doughmination.plural.data.FrontingMetrics
import gay.doughmination.plural.data.Fronters
import gay.doughmination.plural.data.Live
import gay.doughmination.plural.data.Member
import gay.doughmination.plural.data.MentalState
import gay.doughmination.plural.data.Relationship
import gay.doughmination.plural.data.SwitchMetrics
import gay.doughmination.plural.data.User
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

/** Where the app is. The first four are the tabs; the rest open on top of one. */
sealed interface Screen {
    data object Members : Screen
    data object Relationships : Screen
    data object Metrics : Screen
    data object Account : Screen

    data class MemberPage(val id: String) : Screen
    data object EditProfile : Screen
    data object Switch : Screen
    data object Mental : Screen
    data object Status : Screen
    data object Tags : Screen
    data object Pride : Screen
    data object ManageRelationships : Screen
    data object Users : Screen

    val isTab: Boolean get() = this == Members || this == Relationships || this == Metrics || this == Account
}

/** A short note for the snackbar. */
data class Flash(val text: String, val error: Boolean, val id: Long = System.nanoTime())

/** Everything the screens show and do; one per activity. */
class SystemViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as SystemApp
    private val api: Api get() = app.api

    // ---- Public data, kept live by the socket ---------------------------------

    /** A–Z by the name people see, as the website sorts them. */
    var members by mutableStateOf<List<Member>>(emptyList())
        private set
    var fronters by mutableStateOf<Fronters?>(null)
        private set
    var mental by mutableStateOf<MentalState?>(null)
        private set
    var relationships by mutableStateOf<List<Relationship>>(emptyList())
        private set
    var frontingMetrics by mutableStateOf<FrontingMetrics?>(null)
        private set
    var switchMetrics by mutableStateOf<SwitchMetrics?>(null)
        private set
    var metricsError by mutableStateOf<String?>(null)
        private set
    var metricsLoading by mutableStateOf(false)
        private set

    /** True until the first load finishes, either way. */
    var loading by mutableStateOf(true)
        private set
    var refreshing by mutableStateOf(false)
        private set
    /** Set when nothing could be loaded at all. */
    var loadError by mutableStateOf<String?>(null)
        private set

    var liveStatus by mutableStateOf(Live.Status.Connecting)
        private set

    // ---- The home screen's search and filters (the website keeps these in the URL) ----

    var search by mutableStateOf("")
    var tagFilter by mutableStateOf<String?>(null)
    var identityFilter by mutableStateOf<String?>(null)

    // ---- Who's signed in ------------------------------------------------------

    var signedIn by mutableStateOf(app.tokens.token != null)
        private set
    var user by mutableStateOf<User?>(null)
        private set
    var signingIn by mutableStateOf(false)
        private set

    val isAdmin: Boolean get() = user?.let { it.isAdmin || it.isOwner } == true
    val isOwner: Boolean get() = user?.isOwner == true

    // ---- Admin pages ------------------------------------------------------------

    /** A change is being sent; buttons wait for it. */
    var busy by mutableStateOf(false)
        private set
    var users by mutableStateOf<List<User>>(emptyList())
        private set

    // ---- Navigation and messages ------------------------------------------------

    var stack by mutableStateOf<List<Screen>>(listOf(Screen.Members))
        private set
    val screen: Screen get() = stack.last()
    val tab: Screen get() = stack.first()

    var flash by mutableStateOf<Flash?>(null)
        private set

    init {
        viewModelScope.launch { app.live.status.collect { liveStatus = it } }
        viewModelScope.launch {
            app.live.events.collect { event ->
                when (event) {
                    Live.Event.FrontersChanged, Live.Event.MentalStateChanged -> refresh(quiet = true)
                    Live.Event.Reconnected -> refresh(quiet = true)
                    Live.Event.ForceRefresh -> {
                        refresh(quiet = true)
                        if (frontingMetrics != null) loadMetrics()
                        if (signedIn) loadUser()
                    }
                }
            }
        }
        viewModelScope.launch {
            app.signedIn.collect { value ->
                if (!value && signedIn) {
                    signedIn = false
                    user = null
                }
            }
        }
        refresh(quiet = true)
        if (signedIn) loadUser()
    }

    // ---- Loading --------------------------------------------------------------

    /**
     * Reloads what the home, member and relationship pages show. Each part
     * stands alone: one failing keeps what was there, as the website does.
     */
    fun refresh(quiet: Boolean = false) {
        if (!quiet) refreshing = true
        viewModelScope.launch {
            val newMembers = async { runCatching { api.members() } }
            val newFronters = async { runCatching { api.fronters() } }
            val newSystem = async { runCatching { api.system() } }
            val newRelationships = async { runCatching { api.relationships() } }

            newMembers.await().onSuccess { list -> members = list.sortedBy { it.shownName.lowercase() } }
            newFronters.await().onSuccess { fronters = it }
            newSystem.await().onSuccess { mental = it.mentalState }
            newRelationships.await().onSuccess { relationships = it }

            val failure = newMembers.await().exceptionOrNull()
            loadError = if (failure != null && members.isEmpty()) failure.message ?: "Couldn't load the system." else null
            if (failure != null && members.isNotEmpty() && !quiet) show(failure.message ?: "Couldn't refresh.", error = true)
            loading = false
            refreshing = false
        }
    }

    fun loadMetrics() {
        metricsLoading = true
        viewModelScope.launch {
            val fronting = async { runCatching { api.frontingMetrics(30) } }
            val switches = async { runCatching { api.switchMetrics(30) } }
            fronting.await().onSuccess { frontingMetrics = it }
            switches.await().onSuccess { switchMetrics = it }
            val failure = fronting.await().exceptionOrNull() ?: switches.await().exceptionOrNull()
            metricsError = when {
                failure == null -> null
                (failure as? ApiException)?.status == 401 -> "Metrics are not available publicly yet"
                else -> failure.message ?: "Failed to fetch metrics"
            }
            metricsLoading = false
        }
    }

    fun member(id: String): Member? = members.firstOrNull { it.id == id }

    fun isFronting(member: Member): Boolean =
        fronters?.members.orEmpty().any { it.id == member.id || (it.name != null && it.name == member.name) }

    // ---- Navigation -------------------------------------------------------------

    fun selectTab(tab: Screen) {
        stack = listOf(tab)
        if (tab == Screen.Metrics && frontingMetrics == null && !metricsLoading) loadMetrics()
    }

    fun open(screen: Screen) {
        if (screen == Screen.Users) loadUsers()
        stack = stack + screen
    }

    fun back() {
        if (stack.size > 1) stack = stack.dropLast(1)
    }

    /** From a member page's tag or pride chip: the home screen, filtered by it. */
    fun filterBy(tag: String? = null, identity: String? = null) {
        tagFilter = tag
        identityFilter = identity
        search = ""
        stack = listOf(Screen.Members)
    }

    fun clearFilters() {
        tagFilter = null
        identityFilter = null
    }

    fun show(text: String, error: Boolean = false) {
        flash = Flash(text, error)
    }

    // ---- Sign-in ------------------------------------------------------------------

    fun startLogin(context: Context) {
        signingIn = true
        app.auth.start(context)
    }

    /** The browser tab came back with gay.doughmination.plural://auth?code=… */
    fun finishLogin(code: String) {
        signingIn = true
        viewModelScope.launch {
            try {
                app.auth.finish(code)
                signedIn = true
                app.setSignedIn(true)
                loadUser()
                selectTab(Screen.Account)
            } catch (e: Exception) {
                show(e.message ?: "Sign-in failed. Please try again.", error = true)
            } finally {
                signingIn = false
            }
        }
    }

    /** …or with ?error=… */
    fun loginFailed(message: String) {
        signingIn = false
        app.tokens.pendingVerifier = null
        show(message, error = true)
    }

    /** The browser tab was closed without finishing. */
    fun loginAbandoned() {
        signingIn = false
    }

    private fun loadUser() {
        viewModelScope.launch {
            try {
                user = api.userInfo()
            } catch (e: ApiException) {
                if (e.status == 401) {
                    signedIn = false
                    user = null
                }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            app.auth.signOut()
            app.setSignedIn(false)
            signedIn = false
            user = null
            users = emptyList()
            stack = listOf(Screen.Account)
            show("Signed out")
        }
    }

    // ---- Changes ----------------------------------------------------------------

    /** Runs a change, says how it went, then reloads. Returns whether it worked. */
    private fun change(done: String, after: suspend () -> Unit = { refresh(quiet = true) }, then: () -> Unit = {}, action: suspend () -> Unit) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                action()
                show(done)
                after()
                then()
            } catch (e: Exception) {
                show(e.message ?: "Something went wrong", error = true)
            } finally {
                busy = false
            }
        }
    }

    fun switchTo(memberIds: List<String>) {
        val count = memberIds.size
        change("Switched to $count member${if (count != 1) "s" else ""}") { api.multiSwitch(memberIds) }
    }

    fun setMental(level: String, notes: String) =
        change("Mental state updated") { api.setMentalState(level, notes.trim().ifBlank { null }) }

    fun setStatus(member: Member, text: String, emoji: String) =
        change("Status updated") { api.setStatus(member.key, text.trim(), emoji.ifBlank { null }) }

    fun clearStatus(member: Member) = change("Status cleared") { api.clearStatus(member.key) }

    fun addTag(member: Member, tag: String) = change("Tag added") { api.addTag(member.key, tag.trim()) }

    fun removeTag(member: Member, tag: String) = change("Tag removed") { api.removeTag(member.key, tag) }

    fun addPride(member: Member, identity: String) = change("Pride identity added") { api.addPride(member.key, identity) }

    fun removePride(member: Member, identity: String) =
        change("Pride identity removed") { api.removePride(member.key, identity) }

    fun addRelationship(a: Member, b: Member, type: String, since: String, then: () -> Unit) =
        change("Relationship added", then = then) { api.addRelationship(a.key, b.key, type, since.trim().ifBlank { null }) }

    fun removeRelationship(edge: Relationship) = change("Relationship removed") { api.removeRelationship(edge.id) }

    fun forceRefresh() = change("Refresh sent to everyone connected", after = {}) { api.forceRefresh() }

    fun saveProfile(displayName: String, avatarUrl: String, email: String) {
        val me = user ?: return
        change("Profile updated", after = { loadUser() }) {
            api.updateUser(
                me.id,
                Api.UserUpdate(
                    displayName = displayName.trim().ifBlank { null },
                    avatarUrl = avatarUrl.trim().ifBlank { null },
                    email = Api.Optional.Set(email.trim().ifBlank { null }),
                ),
            )
        }
    }

    // ---- Users (owner) ------------------------------------------------------------

    fun loadUsers() {
        viewModelScope.launch {
            try {
                users = api.users().sortedBy { it.username.lowercase() }
            } catch (e: Exception) {
                show(e.message ?: "Failed to fetch users", error = true)
            }
        }
    }

    fun createUser(input: Api.UserCreate, then: () -> Unit) =
        change("User created. They can sign in once they authenticate with SSO under this username.", after = { loadUsers() }, then = then) {
            api.createUser(input)
        }

    fun updateUser(target: User, displayName: String, email: String, avatarUrl: String, admin: Boolean, pet: Boolean, then: () -> Unit) {
        // Only the owner may change someone's email, and only when it changed (as on the website).
        val emailChange = if (isOwner && email.trim() != (target.email ?: "")) {
            Api.Optional.Set(email.trim().ifBlank { null })
        } else {
            Api.Optional.Unset
        }
        change("Updated @${target.username}", after = { loadUsers(); if (target.id == user?.id) loadUser() }, then = then) {
            api.updateUser(
                target.id,
                Api.UserUpdate(
                    displayName = displayName.trim().ifBlank { null },
                    avatarUrl = avatarUrl.trim().ifBlank { null },
                    email = emailChange,
                    isAdmin = admin,
                    isPet = pet,
                ),
            )
        }
    }

    fun deleteUser(target: User) = change("User deleted", after = { loadUsers() }) { api.deleteUser(target.id) }
}
