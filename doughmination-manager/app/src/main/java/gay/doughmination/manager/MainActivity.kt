package gay.doughmination.manager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import gay.doughmination.design.Backdrop
import gay.doughmination.design.DoughminationTheme
import gay.doughmination.design.enableDoughminationEdgeToEdge
import gay.doughmination.manager.ui.ManagerApp
import gay.doughmination.manager.ui.ManagerViewModel

class MainActivity : ComponentActivity() {
    private val vm: ManagerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableDoughminationEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            DoughminationTheme {
                Box(Modifier.fillMaxSize()) {
                    Backdrop()
                    ManagerApp(vm)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Something may have been installed, updated or removed meanwhile.
        vm.refreshInstalled()
    }
}
