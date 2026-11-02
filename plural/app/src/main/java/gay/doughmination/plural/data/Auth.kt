package gay.doughmination.plural.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.browser.customtabs.CustomTabsIntent
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Sign-in goes through the API's own SSO flow in a browser tab (a real one,
 * so passkeys work), the same way Inbox does it:
 *
 *  1. open /v2/plural/auth/sso/login?app=<challenge>, keeping the PKCE verifier here;
 *  2. once the SSO is happy, the API redirects to
 *     gay.doughmination.plural://auth?code=<one-time code> (never the token);
 *  3. MainActivity gets that, and [finish] swaps code + verifier for the token.
 *
 * Only this app holds the verifier, so another app that grabs the redirect
 * can't use the code, and the code dies after one use or a minute.
 */
class Auth(private val api: Api, private val tokens: TokenStore) {
    fun start(context: Context) {
        val verifier = b64url(ByteArray(32).also { SecureRandom().nextBytes(it) })
        val challenge = b64url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
        tokens.pendingVerifier = verifier
        CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
            .launchUrl(context, Uri.parse(api.appLoginUrl(challenge)))
    }

    suspend fun finish(code: String) {
        val verifier = tokens.pendingVerifier ?: throw ApiException("That sign-in has expired. Try again.", 400)
        tokens.pendingVerifier = null
        tokens.token = api.exchangeCode(code, verifier)
    }

    suspend fun signOut() {
        val token = tokens.token
        tokens.token = null
        if (token != null) api.logout(token)
    }

    private fun b64url(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
}
