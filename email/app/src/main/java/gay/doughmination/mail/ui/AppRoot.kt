package gay.doughmination.mail.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import gay.doughmination.mail.Push
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** One snackbar host for every screen, so messages survive moving between them. */
val LocalSnackbar = staticCompositionLocalOf { SnackbarHostState() }

@Composable
fun AppRoot(vm: MailViewModel) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        vm.snackbar.collect { s ->
            val result = snackbar.showSnackbar(
                s.text,
                actionLabel = s.action,
                withDismissAction = s.action == null,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) s.run?.invoke()
        }
    }

    CompositionLocalProvider(LocalSnackbar provides snackbar) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (!vm.signedIn) {
                LoginScreen(vm)
                return@Surface
            }
            AskForNotifications()
            BackHandler(enabled = vm.stack.size > 1) { vm.back() }
            AnimatedContent(
                targetState = vm.screen,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "screen",
            ) { screen ->
                when (screen) {
                    Screen.List -> ThreadListScreen(vm)
                    is Screen.Thread -> ThreadScreen(vm, screen)
                    Screen.Compose -> ComposeScreen(vm)
                    Screen.Settings -> SettingsScreen(vm)
                }
            }
        }
    }
}

/** Android 13+ needs permission before any notification shows; asked once signed in. */
@Composable
private fun AskForNotifications() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    if (!Push.available(context)) return
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) ask.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

@Composable
private fun LoginScreen(vm: MailViewModel) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.Email, null, Modifier.size(72.dp), tint = Accent)
        Spacer(Modifier.height(16.dp))
        Text("Inbox", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Sign in with your Doughmination account.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        if (vm.signingIn) {
            CircularProgressIndicator()
        } else {
            Button(onClick = { vm.startLogin(context) }) { Text("Sign in") }
        }
        vm.loginError?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        }
    }
    SnackbarHost(LocalSnackbar.current)
}
