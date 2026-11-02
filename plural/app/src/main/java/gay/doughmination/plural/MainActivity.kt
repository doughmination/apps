package gay.doughmination.plural

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import gay.doughmination.plural.ui.AppRoot
import gay.doughmination.plural.ui.SystemTheme
import gay.doughmination.plural.ui.SystemViewModel

class MainActivity : ComponentActivity() {
    private val vm: SystemViewModel by viewModels()

    /** A sign-in tab is open; coming back without a code means it was closed. */
    private var awaitingLogin = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { SystemTheme { AppRoot(vm) } }
        // A recreated activity (rotation) has already handled its launch intent.
        if (savedInstanceState == null) handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onPause() {
        super.onPause()
        awaitingLogin = vm.signingIn
    }

    override fun onResume() {
        super.onResume()
        // Back from the browser tab without a redirect: the tab was closed.
        if (awaitingLogin && vm.signingIn && intent?.data == null) vm.loginAbandoned()
        awaitingLogin = false
    }

    private fun handle(intent: Intent?) {
        val data = intent?.data ?: return
        // The end of sign-in: gay.doughmination.plural://auth?code=… (or ?error=…)
        if (data.scheme == "gay.doughmination.plural" && data.host == "auth") {
            awaitingLogin = false
            val code = data.getQueryParameter("code")
            val error = data.getQueryParameter("error")
            when {
                code != null -> vm.finishLogin(code)
                error != null -> vm.loginFailed(error)
            }
        }
        // Handled once: a rotation or a later resume mustn't replay it.
        this.intent = Intent(this.intent).setData(null)
    }
}
