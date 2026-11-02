package gay.doughmination.plural.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

private class Tab(val screen: Screen, val label: String, val icon: ImageVector)

private val Tabs = listOf(
    Tab(Screen.Members, "Members", Icons.Outlined.Groups),
    Tab(Screen.Relationships, "Relationships", Icons.Outlined.FavoriteBorder),
    Tab(Screen.Metrics, "Metrics", Icons.Outlined.BarChart),
    Tab(Screen.Account, "Account", Icons.Outlined.AccountCircle),
)

@Composable
fun AppRoot(vm: SystemViewModel) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.flash) {
        vm.flash?.let { snackbar.showSnackbar(it.text) }
    }

    // Back pops a page off the current tab, then returns to Members, then leaves.
    BackHandler(enabled = vm.stack.size > 1 || vm.tab != Screen.Members) {
        if (vm.stack.size > 1) vm.back() else vm.selectTab(Screen.Members)
    }

    Box(Modifier.fillMaxSize()) {
        Backdrop()
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = {
                SnackbarHost(snackbar) { data ->
                    val error = vm.flash?.error == true
                    Snackbar(
                        data,
                        containerColor = if (error) Bad else androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = if (error) Color(0xFF1A0611) else TextColour,
                    )
                }
            },
            bottomBar = {
                NavigationBar(containerColor = Bg.copy(alpha = 0.92f)) {
                    Tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = vm.tab == tab.screen,
                            onClick = { vm.selectTab(tab.screen) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = AccentDim, selectedIconColor = Accent, selectedTextColor = Accent),
                        )
                    }
                }
            },
            contentWindowInsets = WindowInsets(0),
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize().padding(top = statusBarPadding())) {
                when (val screen = vm.screen) {
                    Screen.Members -> HomeScreen(vm)
                    Screen.Relationships -> RelationshipsScreen(vm)
                    Screen.Metrics -> MetricsScreen(vm)
                    Screen.Account -> AccountScreen(vm)
                    is Screen.MemberPage -> MemberScreen(vm, screen.id)
                    Screen.EditProfile -> EditProfileScreen(vm)
                    Screen.Switch -> SwitchScreen(vm)
                    Screen.Mental -> MentalScreen(vm)
                    Screen.Status -> StatusScreen(vm)
                    Screen.Tags -> TagsScreen(vm)
                    Screen.Pride -> PrideScreen(vm)
                    Screen.ManageRelationships -> ManageRelationshipsScreen(vm)
                    Screen.Users -> UsersScreen(vm)
                }
            }
        }
    }
}

/** Content starts below the status bar; the app draws edge to edge. */
@Composable
private fun statusBarPadding() = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
