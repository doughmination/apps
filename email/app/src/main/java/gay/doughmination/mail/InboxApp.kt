package gay.doughmination.mail

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import gay.doughmination.mail.data.Api
import gay.doughmination.mail.data.Auth
import gay.doughmination.mail.data.Live
import gay.doughmination.mail.data.TokenStore
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Holds the app-wide pieces; there's one of each per process. */
class InboxApp : Application() {
    lateinit var tokens: TokenStore
        private set
    lateinit var api: Api
        private set
    lateinit var auth: Auth
        private set
    lateinit var live: Live
        private set

    private val _signedIn = MutableStateFlow(false)
    val signedIn: StateFlow<Boolean> = _signedIn

    override fun onCreate() {
        super.onCreate()
        tokens = TokenStore(this)
        api = Api(BuildConfig.INBOX_URL, tokens) { _signedIn.value = false }
        auth = Auth(api, tokens)
        live = Live(api, MainScope())
        _signedIn.value = tokens.token != null

        // The live socket only runs while the app is on screen and signed in.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                if (_signedIn.value) live.start()
            }

            override fun onStop(owner: LifecycleOwner) = live.stop()
        })
    }

    fun setSignedIn(value: Boolean) {
        _signedIn.value = value
        if (value) live.start() else live.stop()
    }
}
