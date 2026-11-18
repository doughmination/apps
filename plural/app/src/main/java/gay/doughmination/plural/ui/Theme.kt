package gay.doughmination.plural.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import gay.doughmination.design.DoughminationTheme

// The System app's look is the shared Doughmination theme (the :design
// module, Inbox's theme); only what's the System app's own stays here.

/** The website's terminal-style text ("status: [ Safe ]"). */
val Mono: FontFamily = FontFamily.Monospace

@Composable
fun SystemTheme(content: @Composable () -> Unit) = DoughminationTheme(content)
