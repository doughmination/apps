package gay.doughmination.manager.ui

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import gay.doughmination.manager.BuildConfig
import gay.doughmination.manager.data.AppStatus
import gay.doughmination.manager.data.DownloadException
import gay.doughmination.manager.data.Downloader
import gay.doughmination.manager.data.InstalledApp
import gay.doughmination.manager.data.LoadedManifest
import gay.doughmination.manager.data.ManifestApp
import gay.doughmination.manager.data.ManifestException
import gay.doughmination.manager.data.ManifestSource
import gay.doughmination.manager.data.Packages
import gay.doughmination.manager.data.Status
import gay.doughmination.manager.data.normaliseDigest
import gay.doughmination.manager.install.InstallResult
import gay.doughmination.manager.install.Installer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/** What the Manager is doing for one app right now. */
sealed interface Task {
    data class Downloading(val done: Long, val total: Long) : Task {
        val fraction: Float get() = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
    }
    data object Verifying : Task
    /** Handed to Android; waiting for the person to confirm and Android to finish. */
    data object Installing : Task
    data class Failed(val message: String) : Task
}

/** One app's row: the manifest entry, what's installed, and what follows from both. */
data class AppRow(
    val app: ManifestApp,
    val installed: InstalledApp?,
    val status: AppStatus,
    val task: Task?,
    val isSelf: Boolean,
)

data class UiState(
    val loading: Boolean = true,
    val manifest: LoadedManifest? = null,
    /** Why the latest refresh failed; the cached list (if any) still shows. */
    val error: String? = null,
    val rows: List<AppRow> = emptyList(),
    /** Ask the person to allow installs from this app. */
    val needsInstallPermission: Boolean = false,
    /** A one-off message for the snackbar. */
    val message: String? = null,
)

class ManagerViewModel(application: Application) : AndroidViewModel(application) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val packages = Packages(application)
    private val installer = Installer(application)
    private val source = ManifestSource(http, BuildConfig.MANIFEST_URL, File(application.filesDir, "manifest.json"))
    private val downloader = Downloader(http, File(application.cacheDir, "downloads"))

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    private val tasks = mutableMapOf<String, Task>()
    private val jobs = mutableMapOf<String, Job>()
    private var installed = mapOf<String, InstalledApp?>()

    init {
        viewModelScope.launch {
            source.cached()?.let { cached -> _state.update { it.copy(manifest = cached) }; rebuild() }
            refresh()
        }
        viewModelScope.launch { Installer.results.collect(::onInstallResult) }
    }

    /** Fetches the manifest again and re-reads what's installed. */
    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            try {
                val fresh = source.fetch()
                _state.update { it.copy(manifest = fresh, error = null) }
            } catch (e: ManifestException) {
                _state.update { it.copy(error = e.message) }
            }
            refreshInstalled()
            _state.update { it.copy(loading = false) }
        }
    }

    /** Re-reads installed versions (on resume: something may have been installed or removed meanwhile). */
    fun refreshInstalled() {
        viewModelScope.launch { rebuild() }
    }

    private suspend fun rebuild() {
        val apps = _state.value.manifest?.manifest?.apps.orEmpty()
        installed = withContext(Dispatchers.IO) { apps.associate { it.packageName to packages.installed(it.packageName) } }
        publishRows()
    }

    private fun publishRows() {
        val apps = _state.value.manifest?.manifest?.apps.orEmpty()
        val self = getApplication<Application>().packageName
        val rows = apps.map { app ->
            val inst = installed[app.packageName]
            AppRow(app, inst, Status.of(app, inst, Build.VERSION.SDK_INT), tasks[app.packageName], app.packageName == self)
        }
        _state.update { it.copy(rows = rows) }
    }

    private fun setTask(packageName: String, task: Task?) {
        if (task == null) tasks.remove(packageName) else tasks[packageName] = task
        publishRows()
    }

    /** Downloads, verifies and installs the published build of [app]. */
    fun install(app: ManifestApp) {
        if (jobs[app.packageName]?.isActive == true) return
        if (!installer.canInstall()) {
            _state.update { it.copy(needsInstallPermission = true) }
            return
        }
        jobs[app.packageName] = viewModelScope.launch {
            try {
                setTask(app.packageName, Task.Downloading(0, app.latest.sizeBytes))
                var lastShown = 0L
                val apk = downloader.download(app) { done, total ->
                    // Every 1% is plenty for a progress bar.
                    if (done - lastShown >= total / 100 || done == total) {
                        lastShown = done
                        viewModelScope.launch { setTask(app.packageName, Task.Downloading(done, total)) }
                    }
                }
                setTask(app.packageName, Task.Verifying)
                verifyArchive(app, apk)
                setTask(app.packageName, Task.Installing)
                installer.install(apk, app.packageName)
                // The rest happens in onInstallResult.
            } catch (e: CancellationException) {
                setTask(app.packageName, null)
                throw e
            } catch (e: DownloadException) {
                setTask(app.packageName, Task.Failed(e.message ?: "The download failed."))
            } catch (e: VerificationException) {
                downloader.discard(app)
                setTask(app.packageName, Task.Failed(e.message ?: "The APK failed verification."))
            } catch (e: Exception) {
                setTask(app.packageName, Task.Failed("Couldn't install: ${e.message ?: e.javaClass.simpleName}"))
            }
        }
    }

    /** Stops a download; what's downloaded so far is kept so it can resume. */
    fun cancel(app: ManifestApp) {
        jobs.remove(app.packageName)?.cancel()
        setTask(app.packageName, null)
    }

    fun dismissFailure(app: ManifestApp) = setTask(app.packageName, null)

    private class VerificationException(message: String) : Exception(message)

    /**
     * The checksum matched the manifest; now make sure the APK is what the
     * manifest says it is, as Android reads it: the right package, version
     * and signing key. A mismatch means the manifest and the APK disagree,
     * so nothing is installed.
     */
    private suspend fun verifyArchive(app: ManifestApp, apk: File) = withContext(Dispatchers.IO) {
        val archive = packages.inspectArchive(apk)
            ?: throw VerificationException("Android couldn't read the downloaded APK.")
        if (archive.packageName != app.packageName) {
            throw VerificationException("The APK is ${archive.packageName}, not ${app.packageName}. Not installed.")
        }
        if (archive.versionCode != app.latest.versionCode) {
            throw VerificationException(
                "The APK is version code ${archive.versionCode}, but the list says ${app.latest.versionCode}. " +
                    "Refresh and try again.",
            )
        }
        val expected = app.signingCertSha256.map(::normaliseDigest).toSet()
        if (archive.signingCerts.none { it in expected }) {
            throw VerificationException("The APK isn't signed with ${app.name}'s key. Not installed.")
        }
    }

    private fun onInstallResult(result: InstallResult) {
        val app = _state.value.manifest?.manifest?.apps?.firstOrNull { it.packageName == result.packageName }
        jobs.remove(result.packageName)
        when (result) {
            is InstallResult.Success -> {
                app?.let(downloader::discard)
                setTask(result.packageName, null)
                _state.update { it.copy(message = "${app?.name ?: result.packageName} installed.") }
            }
            is InstallResult.Failure -> {
                // A cancelled install keeps its download, so trying again is instant.
                if (result.cancelled) {
                    setTask(result.packageName, null)
                    _state.update { it.copy(message = result.message) }
                } else {
                    setTask(result.packageName, Task.Failed(result.message))
                }
            }
        }
        refreshInstalled()
    }

    fun permissionPromptHandled() = _state.update { it.copy(needsInstallPermission = false) }
    fun messageShown() = _state.update { it.copy(message = null) }
    fun messageFallback(text: String) = _state.update { it.copy(message = text) }

    val installPermissionIntent get() = installer.permissionSettings()
    fun uninstallIntent(app: ManifestApp) = installer.uninstallIntent(app.packageName)
    fun launchIntent(app: ManifestApp) = packages.launchIntent(app.packageName)
}
