package gay.doughmination.plural

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import gay.doughmination.plural.data.Api
import gay.doughmination.plural.data.Auth
import gay.doughmination.plural.data.Live
import gay.doughmination.plural.data.TokenStore
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Holds the app-wide pieces; there's one of each per process. */
class SystemApp : Application() {
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
        api = Api(BuildConfig.API_URL, tokens) { _signedIn.value = false }
        auth = Auth(api, tokens)
        live = Live(api, MainScope())
        _signedIn.value = tokens.token != null

        // Live updates are public, so the socket runs whenever the app is on
        // screen, signed in or not, as on the website.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = live.start()
            override fun onStop(owner: LifecycleOwner) = live.stop()
        })
    }

    fun setSignedIn(value: Boolean) {
        _signedIn.value = value
    }
}
