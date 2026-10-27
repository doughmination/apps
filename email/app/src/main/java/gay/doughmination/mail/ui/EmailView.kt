package gay.doughmination.mail.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import gay.doughmination.mail.data.Api
import gay.doughmination.mail.data.AttachmentInfo
import okhttp3.Request
import java.io.ByteArrayInputStream

private const val BASE_STYLE =
    ":root{color-scheme:light}body{margin:0;padding:12px;background:#fff;color:#111;" +
        "font:15px/1.5 sans-serif;overflow-wrap:anywhere}" +
        "img{max-width:100%;height:auto}pre{white-space:pre-wrap}table{max-width:100%}"

private val REMOTE = Regex("""(?:src|background|href)\s*=\s*["']?\s*https?:|url\(\s*["']?\s*https?:|@import""", RegexOption.IGNORE_CASE)

/** Whether an email pulls anything from the internet (mostly tracking pixels). */
fun hasRemoteContent(html: String): Boolean = REMOTE.containsMatchIn(html)

/**
 * The only place email HTML is ever rendered, like EmailFrame on the web:
 *
 * - no JavaScript, no file or content access;
 * - links never load in here, they open in the browser;
 * - remote images, fonts and stylesheets stay blocked until `showRemote`,
 *   by both a CSP and refusing the requests outright;
 * - cid: images load from /api/attachments with the session token.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun EmailView(api: Api, html: String, attachments: List<AttachmentInfo>, showRemote: Boolean, modifier: Modifier = Modifier) {
    val body = remember(html, attachments) {
        val byCid = attachments.filter { it.contentId != null }.associate { it.contentId!!.lowercase() to it.id }
        Regex("""cid:([^"'\s>)]+)""", RegexOption.IGNORE_CASE).replace(html) { m ->
            val id = byCid[m.groupValues[1].removePrefix("<").removeSuffix(">").lowercase()]
            if (id != null) api.attachmentUrl(id, inline = true) else m.value
        }
    }
    val document = remember(body, showRemote) {
        val remote = if (showRemote) " https:" else ""
        val csp = "default-src 'none'; img-src ${api.baseUrl} data:$remote; style-src 'unsafe-inline'$remote; " +
            "font-src data:$remote; media-src 'none'; form-action 'none'; base-uri 'none'"
        "<!doctype html><html><head><meta charset=\"utf-8\">" +
            "<meta http-equiv=\"Content-Security-Policy\" content=\"$csp\">" +
            "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
            "<meta name=\"referrer\" content=\"no-referrer\"><style>$BASE_STYLE</style></head>" +
            "<body>$body</body></html>"
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setBackgroundColor(Color.WHITE)
                isVerticalScrollBarEnabled = false
                settings.apply {
                    javaScriptEnabled = false
                    allowFileAccess = false
                    allowContentAccess = false
                    domStorageEnabled = false
                    setGeolocationEnabled(false)
                    builtInZoomControls = true
                    displayZoomControls = false
                }
            }
        },
        update = { view ->
            view.webViewClient = MailClient(api, showRemote)
            if (view.tag != document) {
                view.tag = document
                view.loadDataWithBaseURL(api.baseUrl + "/", document, "text/html", "utf-8", null)
            }
        },
        onRelease = { it.destroy() },
    )
}

private class MailClient(private val api: Api, private val showRemote: Boolean) : WebViewClient() {
    private val attachmentPrefix = api.baseUrl + "/api/attachments/"

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val scheme = request.url.scheme?.lowercase()
        if (scheme == "http" || scheme == "https" || scheme == "mailto") {
            runCatching {
                view.context.startActivity(Intent(Intent.ACTION_VIEW, request.url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        // Nothing ever navigates inside the email itself.
        return true
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val url = request.url.toString()
        if (url.startsWith(attachmentPrefix)) {
            return runCatching {
                val res = api.http.newCall(api.authorize(Request.Builder().url(url)).build()).execute()
                val type = res.header("Content-Type")?.substringBefore(";") ?: "application/octet-stream"
                WebResourceResponse(type, null, res.body?.byteStream())
            }.getOrElse { blocked() }
        }
        val scheme = request.url.scheme?.lowercase()
        if (scheme == "data") return null
        if (!showRemote || scheme != "https") return blocked()
        return null
    }

    private fun blocked() = WebResourceResponse("text/plain", "utf-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
}
