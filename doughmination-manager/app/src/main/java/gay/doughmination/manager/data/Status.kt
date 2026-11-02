package gay.doughmination.manager.data

/** What Android says is installed for a package. */
data class InstalledApp(
    val packageName: String,
    val versionCode: Long,
    val versionName: String?,
    /** SHA-256 of each signing certificate, normalised (see [normaliseDigest]). */
    val signingCerts: Set<String>,
)

/** Where an app stands, comparing what's installed with the manifest. */
enum class AppStatus {
    /** Not installed, and the phone can run it. */
    NotInstalled,
    /** Installed at the published version. */
    UpToDate,
    /** The published build is newer than the installed one. */
    UpdateAvailable,
    /** The installed build is newer than the published one (a local or test build). */
    InstalledNewer,
    /**
     * Installed, but signed with a different key (e.g. a copy from before the
     * apps got new keys). Android won't update it in place; it must be
     * uninstalled first.
     */
    SignatureMismatch,
    /** The published build needs a newer Android than this phone has. */
    Incompatible,
}

object Status {
    fun of(app: ManifestApp, installed: InstalledApp?, deviceSdk: Int): AppStatus {
        val expected = app.signingCertSha256.map(::normaliseDigest).toSet()
        if (installed != null && expected.isNotEmpty() && installed.signingCerts.none { it in expected }) {
            return AppStatus.SignatureMismatch
        }
        val runsHere = (app.latest.minSdk ?: 0) <= deviceSdk
        return when {
            installed == null -> if (runsHere) AppStatus.NotInstalled else AppStatus.Incompatible
            installed.versionCode == app.latest.versionCode -> AppStatus.UpToDate
            installed.versionCode > app.latest.versionCode -> AppStatus.InstalledNewer
            !runsHere -> AppStatus.Incompatible
            else -> AppStatus.UpdateAvailable
        }
    }

    /** True when the Manager should offer to download and install the published build. */
    fun canInstall(status: AppStatus): Boolean =
        status == AppStatus.NotInstalled || status == AppStatus.UpdateAvailable
}
