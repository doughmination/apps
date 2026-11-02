package gay.doughmination.manager.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

class DownloadException(message: String, val retryable: Boolean = true) : Exception(message)

/**
 * Downloads an APK into the app's cache, resuming an interrupted download
 * where it stopped, and only returns a file whose size and SHA-256 match the
 * manifest. Nothing unverified is ever handed to the installer.
 */
class Downloader(private val http: OkHttpClient, private val dir: File) {

    /** The file a release downloads to; named by version so a newer release never resumes an older one. */
    fun target(app: ManifestApp): File = File(dir, "${app.packageName}-${app.latest.versionCode}.apk")

    suspend fun download(app: ManifestApp, onProgress: (done: Long, total: Long) -> Unit): File =
        withContext(Dispatchers.IO) {
            dir.mkdirs()
            // Downloads for other versions of this app are stale now.
            dir.listFiles()?.filter {
                it.name.startsWith("${app.packageName}-") && !it.name.startsWith(target(app).name)
            }?.forEach { it.delete() }

            val release = app.latest
            val done = target(app)
            if (done.exists() && done.length() == release.sizeBytes && Hashes.sha256(done) == release.sha256.lowercase()) {
                onProgress(release.sizeBytes, release.sizeBytes)
                return@withContext done
            }
            done.delete()

            val part = File(dir, done.name + ".part")
            if (part.length() > release.sizeBytes) part.delete()
            fetch(release, part, onProgress)

            if (part.length() != release.sizeBytes) {
                part.delete()
                throw DownloadException(
                    "The download was ${part.length()} bytes, not ${release.sizeBytes}. " +
                        "The release may have changed; refresh and try again.",
                )
            }
            onProgress(release.sizeBytes, release.sizeBytes)
            val actual = Hashes.sha256(part)
            if (actual != release.sha256.lowercase()) {
                part.delete()
                throw DownloadException(
                    "The download doesn't match its SHA-256 checksum, so it wasn't installed. " +
                        "The release may have changed while downloading; refresh and try again.",
                )
            }
            if (!part.renameTo(done)) throw DownloadException("Couldn't save the download.")
            done
        }

    private suspend fun fetch(release: Release, part: File, onProgress: (Long, Long) -> Unit) {
        val have = part.length()
        val request = Request.Builder().url(release.apkUrl).apply {
            if (have > 0) header("Range", "bytes=$have-")
        }.build()
        try {
            http.newCall(request).execute().use { res ->
                val append = when (res.code) {
                    206 -> true
                    200 -> false // the server ignored the range: start over
                    416 -> { part.delete(); throw DownloadException("The download couldn't resume; try again.") }
                    404 -> throw DownloadException("The APK isn't on the release any more; refresh the list.")
                    else -> throw DownloadException("The download failed (HTTP ${res.code}).")
                }
                val body = res.body ?: throw DownloadException("The download was empty.")
                var written = if (append) have else 0L
                FileOutputStream(part, append).use { out ->
                    copy(body.byteStream(), out) { n ->
                        written += n
                        if (written > release.sizeBytes) {
                            throw DownloadException("The download is bigger than the release says; refresh and try again.")
                        }
                        onProgress(written, release.sizeBytes)
                    }
                }
            }
        } catch (e: IOException) {
            // The partial file stays, so the next try picks up from here.
            throw DownloadException("The download was interrupted. Try again to resume it.")
        }
    }

    private suspend fun copy(input: InputStream, out: FileOutputStream, onChunk: (Int) -> Unit) {
        val buffer = ByteArray(64 * 1024)
        while (true) {
            kotlin.coroutines.coroutineContext.ensureActive()
            val n = input.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            onChunk(n)
        }
    }

    /** Removes a release's download once it's installed (or abandoned). */
    fun discard(app: ManifestApp) {
        target(app).delete()
        File(dir, target(app).name + ".part").delete()
    }
}

object Hashes {
    fun sha256(file: File): String = file.inputStream().use { sha256(it) }

    fun sha256(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            digest.update(buffer, 0, n)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
