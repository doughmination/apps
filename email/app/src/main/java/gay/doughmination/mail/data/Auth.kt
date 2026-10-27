package gay.doughmination.mail.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.browser.customtabs.CustomTabsIntent
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Sign-in goes through the server's own SSO flow in a browser tab:
 *
 *  1. open /auth/login?app=<challenge>, keeping the PKCE verifier here;
 *  2. the server, once the SSO is happy, redirects to
 *     gay.doughmination.mail://auth?code=<one-time code>;
 *  3. MainActivity gets that and [finish] swaps code + verifier for a token.
 *
 * Only this app holds the verifier, so another app that grabs the redirect
 * can't use the code.
 */
class Auth(private val api: Api, private val tokens: TokenStore) {
    fun start(context: Context) {
        val verifier = b64url(ByteArray(32).also { SecureRandom().nextBytes(it) })
        val challenge = b64url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
        tokens.pendingVerifier = verifier
        CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
            .launchUrl(context, Uri.parse("${api.baseUrl}/auth/login?app=$challenge"))
    }

    suspend fun finish(code: String) {
        val verifier = tokens.pendingVerifier ?: throw ApiException("That sign-in has expired. Try again.", 400)
        tokens.pendingVerifier = null
        tokens.token = api.exchangeCode(code, verifier)
    }

    private fun b64url(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
}
