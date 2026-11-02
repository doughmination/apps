package gay.doughmination.mail.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import gay.doughmination.mail.InboxApp
import gay.doughmination.mail.data.Me
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

/**
 * The signed-in account's picture: the SSO's, when there is one and Settings
 * says to use it, or the default, their initial on the accent colour. Also the
 * default while the picture loads, or if it can't be loaded. Same as the web.
 */
@Composable
fun AccountPicture(me: Me?, size: Dp) {
    val url = me?.shownPicture
    val http = (LocalContext.current.applicationContext as InboxApp).api.http
    val picture by produceState(url?.let { loaded[it] }, url) {
        value = url?.let { loadPicture(http, it) }
    }
    val shape = Modifier.size(size).clip(CircleShape)
    picture?.let {
        Image(it, null, shape, contentScale = ContentScale.Crop)
        return
    }
    val initial = (me?.name?.ifBlank { null } ?: me?.username ?: "?").firstOrNull { it.isLetterOrDigit() }?.uppercaseChar() ?: '?'
    Box(shape.background(Accent), contentAlignment = Alignment.Center) {
        Text(initial.toString(), color = Color.White, fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Pictures already fetched this run, so the drawer doesn't flash its initial each time. */
private val loaded = ConcurrentHashMap<String, ImageBitmap>()

/** Fetches a picture without the session token (it's the SSO's, not ours), scaled down to avatar size. */
private suspend fun loadPicture(http: OkHttpClient, url: String): ImageBitmap? = loaded[url] ?: withContext(Dispatchers.IO) {
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
    }.getOrNull()?.also { loaded[url] = it }
}
