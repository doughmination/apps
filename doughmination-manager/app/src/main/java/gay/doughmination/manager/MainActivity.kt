package gay.doughmination.manager

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import gay.doughmination.manager.ui.ManagerApp
import gay.doughmination.manager.ui.ManagerTheme
import gay.doughmination.manager.ui.ManagerViewModel

class MainActivity : ComponentActivity() {
    private val vm: ManagerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // The app is always dark, so the system bars get light icons.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent { ManagerTheme { ManagerApp(vm) } }
    }

    override fun onResume() {
        super.onResume()
        // Something may have been installed, updated or removed meanwhile.
        vm.refreshInstalled()
    }
}
