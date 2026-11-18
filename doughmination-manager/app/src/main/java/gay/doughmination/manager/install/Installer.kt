package gay.doughmination.manager.install

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.provider.Settings
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext
import java.io.File

/** How an install ended, as PackageInstaller reported it. */
sealed interface InstallResult {
    val packageName: String
    data class Success(override val packageName: String) : InstallResult
    data class Failure(override val packageName: String, val message: String, val cancelled: Boolean) : InstallResult
}

/**
 * Installs verified APKs with Android's PackageInstaller. Android shows its
 * own confirmation for every install, and only lets this app install at all
 * once the person has allowed it as a source ("Install unknown apps").
 */
class Installer(private val context: Context) {

    /** Whether the person has allowed this app to install apps. */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** The system screen where the person can allow it. */
    fun permissionSettings(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    /** The system's uninstall prompt for [packageName]. */
    fun uninstallIntent(packageName: String): Intent =
        Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))

    /** Starts installing [apk]; the result arrives on [results]. */
    suspend fun install(apk: File, packageName: String) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                setInstallReason(android.content.pm.PackageManager.INSTALL_REASON_USER)
            }
        }
        val sessionId = installer.createSession(params)
        val session = installer.openSession(sessionId)
        try {
            session.openWrite("base.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val intent = Intent(context, InstallReceiver::class.java)
                .setAction(ACTION)
                .putExtra(EXTRA_PACKAGE, packageName)
            // Mutable: PackageInstaller fills in the status extras.
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            val pending = PendingIntent.getBroadcast(context, sessionId, intent, flags)
            session.commit(pending.intentSender)
        } catch (e: Exception) {
            session.abandon()
            throw e
        } finally {
            session.close()
        }
    }

    companion object {
        const val ACTION = "gay.doughmination.manager.INSTALL_STATUS"
        const val EXTRA_PACKAGE = "package"

        private val _results = MutableSharedFlow<InstallResult>(extraBufferCapacity = 8)
        val results: SharedFlow<InstallResult> = _results

        internal fun report(result: InstallResult) {
            _results.tryEmit(result)
        }
    }
}

/** Receives PackageInstaller's status for each session. */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Installer.ACTION) return
        val packageName = intent.getStringExtra(Installer.EXTRA_PACKAGE) ?: return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // Android's own confirmation screen; the person decides.
                @Suppress("DEPRECATION")
                val confirm = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                if (confirm == null) {
                    Installer.report(InstallResult.Failure(packageName, "Android didn't ask for confirmation.", false))
                    return
                }
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    context.startActivity(confirm)
                } catch (e: Exception) {
                    Installer.report(InstallResult.Failure(packageName, "Couldn't show the install prompt: ${e.message}", false))
                }
            }
            PackageInstaller.STATUS_SUCCESS -> Installer.report(InstallResult.Success(packageName))
            else -> {
                val detail = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                Installer.report(
                    InstallResult.Failure(
                        packageName,
                        describe(status, detail),
                        cancelled = status == PackageInstaller.STATUS_FAILURE_ABORTED,
                    ),
                )
            }
        }
    }

    private fun describe(status: Int, detail: String?): String = when (status) {
        PackageInstaller.STATUS_FAILURE_ABORTED -> "Install cancelled."
        PackageInstaller.STATUS_FAILURE_BLOCKED -> "Android blocked the install."
        PackageInstaller.STATUS_FAILURE_CONFLICT ->
            "It conflicts with the installed copy (usually a different signing key). Uninstall that first."
        PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "This phone can't run that build."
        PackageInstaller.STATUS_FAILURE_INVALID -> "Android says the APK is invalid."
        PackageInstaller.STATUS_FAILURE_STORAGE -> "There isn't enough storage space."
        else -> "The install failed."
    } + (detail?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "")
}
