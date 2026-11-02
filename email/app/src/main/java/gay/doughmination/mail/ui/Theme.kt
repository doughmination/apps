package gay.doughmination.mail.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import gay.doughmination.design.DoughminationTheme

// Inbox's look is the shared Doughmination theme (the :design module), which
// started here; only what's Inbox's own stays in this file.

/** A starred thread, like --star on the web. */
val StarYellow = Color(0xFFF7C948)

@Composable
fun InboxTheme(content: @Composable () -> Unit) = DoughminationTheme(content)
