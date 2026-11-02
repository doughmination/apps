package gay.doughmination.plural.ui

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import gay.doughmination.plural.R
import gay.doughmination.plural.SystemApp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

/**
 * A member's (or user's) avatar, round, with a ring in their colour. Shows the
 * System mascot while it loads and when it can't be, as the website falls
 * back to the site icon.
 */
@Composable
fun Avatar(url: String?, size: Dp, modifier: Modifier = Modifier, ring: Color? = null) {
    val picture by rememberPicture(url)
    val shape = modifier
        .size(size)
        .clip(CircleShape)
        .let { if (ring != null) it.border(2.dp, ring, CircleShape) else it }
    Image(
        bitmap = picture ?: ImageBitmap.imageResource(R.drawable.mascot),
        contentDescription = null,
        modifier = shape,
        contentScale = ContentScale.Crop,
    )
}

/** An avatar as a bitmap (null until loaded, or if it can't be), for drawing on a canvas. */
@Composable
fun rememberPicture(url: String?): State<ImageBitmap?> {
    val http = (LocalContext.current.applicationContext as SystemApp).api.http
    return produceState(url?.let { cache.get(it) }, url) {
        if (url != null && value == null) value = load(http, url)
    }
}

/** Avatars fetched this run, scaled down; about 25 MB at most. */
private val cache = object : LruCache<String, ImageBitmap>(24 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
}

/** Requests in flight, so a grid of the same avatar fetches it once. */
private val pending = ConcurrentHashMap<String, CompletableDeferred<ImageBitmap?>>()

private suspend fun load(http: OkHttpClient, url: String): ImageBitmap? {
    cache.get(url)?.let { return it }
    val mine = CompletableDeferred<ImageBitmap?>()
    val existing = pending.putIfAbsent(url, mine)
    if (existing != null) return existing.await()
    val result = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(Request.Builder().url(url).build()).execute().use { res ->
                if (!res.isSuccessful) return@use null
                val bytes = res.body?.bytes() ?: return@use null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sample = 1
                while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 256) sample *= 2
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                    ?.asImageBitmap()
            }
        }.getOrNull()
    }
    result?.let { cache.put(url, it) }
    mine.complete(result)
    pending.remove(url)
    return result
}
