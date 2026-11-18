package gay.doughmination.manager.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/** A manifest, and whether it came from the network just now or from the last good copy. */
data class LoadedManifest(val manifest: Manifest, val fetchedAt: Long, val fromCache: Boolean)

/**
 * Fetches the manifest, keeping the last good copy so the list still shows
 * (marked as cached) when the phone is offline.
 */
class ManifestSource(
    private val http: OkHttpClient,
    private val url: String,
    private val cacheFile: File,
) {
    /** The last good manifest, if any; never throws. */
    suspend fun cached(): LoadedManifest? = withContext(Dispatchers.IO) {
        runCatching {
            LoadedManifest(ManifestParser.parse(cacheFile.readText()), cacheFile.lastModified(), fromCache = true)
        }.getOrNull()
    }

    /** Fetches a fresh manifest; throws [ManifestException] with a message fit to show. */
    suspend fun fetch(): LoadedManifest = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).cacheControl(CacheControl.FORCE_NETWORK).build()
        val text = try {
            http.newCall(request).execute().use { res ->
                if (res.code == 404) throw ManifestException("No apps have been published yet.")
                if (!res.isSuccessful) throw ManifestException("The app list couldn't be loaded (HTTP ${res.code}).")
                res.body?.string() ?: throw ManifestException("The app list was empty.")
            }
        } catch (e: IOException) {
            throw ManifestException("Couldn't reach GitHub. Check your connection.")
        }
        val manifest = ManifestParser.parse(text)
        runCatching {
            val tmp = File(cacheFile.parentFile, cacheFile.name + ".tmp")
            tmp.writeText(text)
            tmp.renameTo(cacheFile)
        }
        LoadedManifest(manifest, System.currentTimeMillis(), fromCache = false)
    }
}
