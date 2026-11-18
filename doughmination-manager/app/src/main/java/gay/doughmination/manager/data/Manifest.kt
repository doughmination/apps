package gay.doughmination.manager.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The document CI publishes beside the APKs (docs/manifest.md). Every version,
 * hash and size in it was read back from the built APK, so it describes
 * exactly what the download will be.
 */
@Serializable
data class Manifest(
    val schemaVersion: Int,
    val generatedAt: String? = null,
    val commit: String? = null,
    val apps: List<ManifestApp> = emptyList(),
)

@Serializable
data class ManifestApp(
    val id: String,
    val packageName: String,
    val name: String,
    val summary: String? = null,
    val iconUrl: String? = null,
    /** As apksigner prints them; colons and case don't matter. */
    val signingCertSha256: List<String> = emptyList(),
    val latest: Release,
)

@Serializable
data class Release(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val sizeBytes: Long,
    val minSdk: Int? = null,
    val releasedAt: String? = null,
    val releaseNotes: String? = null,
    val releaseUrl: String? = null,
)

class ManifestException(message: String) : Exception(message)

object ManifestParser {
    const val SCHEMA_VERSION = 1

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val hex64 = Regex("^[0-9a-fA-F]{64}$")

    /**
     * Parses and checks a manifest. Apps with unusable entries (no HTTPS URL,
     * a malformed hash, no certificate) are dropped rather than offered.
     */
    fun parse(text: String): Manifest {
        val manifest = try {
            json.decodeFromString<Manifest>(text)
        } catch (e: Exception) {
            throw ManifestException("The app list is damaged or in an unknown format.")
        }
        if (manifest.schemaVersion != SCHEMA_VERSION) {
            throw ManifestException(
                "The app list uses format ${manifest.schemaVersion}; this Manager reads format $SCHEMA_VERSION. " +
                    "Update the Manager from the website.",
            )
        }
        return manifest.copy(apps = manifest.apps.filter(::usable).distinctBy { it.packageName })
    }

    fun encode(manifest: Manifest): String = json.encodeToString(Manifest.serializer(), manifest)

    private fun usable(app: ManifestApp): Boolean = with(app.latest) {
        apkUrl.startsWith("https://") &&
            hex64.matches(sha256) &&
            sizeBytes > 0 &&
            versionCode > 0 &&
            app.signingCertSha256.any { normaliseDigest(it).length == 64 }
    }
}

/** "AB:cd:…" → "abcd…", so digests from apksigner and from Android compare equal. */
fun normaliseDigest(digest: String): String = digest.replace(":", "").trim().lowercase()
