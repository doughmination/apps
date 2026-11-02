package gay.doughmination.manager.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import java.io.File
import java.security.MessageDigest

/** Reads installed packages and downloaded APKs through PackageManager. */
class Packages(private val context: Context) {
    private val pm: PackageManager get() = context.packageManager

    @Suppress("DEPRECATION")
    private val signingFlags: Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES
        else PackageManager.GET_SIGNATURES

    /** The installed copy of [packageName], or null. Needs the package in the manifest's <queries>. */
    fun installed(packageName: String): InstalledApp? {
        val info = try {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(packageName, signingFlags)
        } catch (e: PackageManager.NameNotFoundException) {
            return null
        }
        return info.toInstalled()
    }

    /**
     * What a downloaded APK really is: its package, version and signer, read
     * from the file itself. Null when Android can't parse it.
     */
    fun inspectArchive(apk: File): InstalledApp? {
        @Suppress("DEPRECATION")
        val info = pm.getPackageArchiveInfo(apk.absolutePath, signingFlags) ?: return null
        return info.toInstalled()
    }

    fun launchIntent(packageName: String): Intent? = pm.getLaunchIntentForPackage(packageName)

    private fun PackageInfo.toInstalled() = InstalledApp(
        packageName = packageName,
        versionCode = PackageInfoCompat.getLongVersionCode(this),
        versionName = versionName,
        signingCerts = signers().map(::digest).toSet(),
    )

    @Suppress("DEPRECATION")
    private fun PackageInfo.signers(): List<Signature> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = signingInfo ?: return emptyList()
            // With key rotation, any certificate in the lineage is the same app.
            if (info.hasMultipleSigners()) info.apkContentsSigners.toList()
            else info.signingCertificateHistory.toList()
        } else {
            signatures?.toList().orEmpty()
        }

    private fun digest(signature: Signature): String =
        MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
