package gay.doughmination.mail

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import gay.doughmination.mail.ui.AppRoot
import gay.doughmination.mail.ui.InboxTheme
import gay.doughmination.mail.ui.MailViewModel

class MainActivity : ComponentActivity() {
    private val vm: MailViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { InboxTheme { AppRoot(vm) } }
        // A recreated activity (rotation) has already handled its launch intent.
        if (savedInstanceState == null) handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        val data = intent?.data ?: return
        when {
            // The end of sign-in: gay.doughmination.mail://auth?code=...
            data.scheme == "gay.doughmination.mail" && data.host == "auth" ->
                data.getQueryParameter("code")?.let(vm::finishLogin)
            // A link to the site, like /inbox/<thread id>.
            data.scheme == "https" && vm.signedIn -> data.path?.let(vm::openPath)
        }
    }
}
