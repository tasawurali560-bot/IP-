package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.camera.CameraProScreen
import com.example.files.FilesManagerScreen
import com.example.home.HomeScreen
import com.example.media.GalleryScreen
import com.example.media.ImageEditorScreen
import com.example.media.MusicPlayerScreen
import com.example.media.MusicPlayerViewModel
import com.example.navigation.SimRoute
import com.example.settings.DeviceSettingsScreen
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.MyApplicationTheme
import com.example.web.WebAppsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = ObsidianBg
                ) {
                    SimulatorNavHost()
                }
            }
        }
    }
}

@Composable
fun SimulatorNavHost(
    musicViewModel: MusicPlayerViewModel = viewModel()
) {
    val context = LocalContext.current
    val navController = rememberNavController()

    LaunchedEffect(Unit) {
        musicViewModel.initialize(context)
    }

    NavHost(
        navController = navController,
        startDestination = SimRoute.Home
    ) {
        composable<SimRoute.Home> {
            HomeScreen(
                musicViewModel = musicViewModel,
                onOpenCamera = { navController.navigate(SimRoute.Camera) },
                onOpenGallery = { navController.navigate(SimRoute.Gallery()) },
                onOpenMusic = { navController.navigate(SimRoute.Music) },
                onOpenFiles = { navController.navigate(SimRoute.Files) },
                onOpenSettings = { navController.navigate(SimRoute.DeviceSettings) },
                onOpenWebApp = { appId, title, url ->
                    navController.navigate(SimRoute.WebApp(appId = appId, title = title, initialUrl = url))
                }
            )
        }

        composable<SimRoute.Camera> {
            CameraProScreen(
                onBack = { navController.popBackStack() },
                onOpenGallery = { navController.navigate(SimRoute.Gallery()) },
                onMediaCaptured = { }
            )
        }

        composable<SimRoute.Gallery> { backStackEntry ->
            val route = backStackEntry.toRoute<SimRoute.Gallery>()
            GalleryScreen(
                initialFilter = route.initialFilter,
                onBack = { navController.popBackStack() },
                onOpenCamera = { navController.navigate(SimRoute.Camera) },
                onEditImage = { uriStr ->
                    navController.navigate(SimRoute.ImageEditor(uriStr))
                }
            )
        }

        composable<SimRoute.ImageEditor> { backStackEntry ->
            val route = backStackEntry.toRoute<SimRoute.ImageEditor>()
            ImageEditorScreen(
                uriString = route.uriString,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }

        composable<SimRoute.Music> {
            MusicPlayerScreen(
                musicViewModel = musicViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable<SimRoute.Files> {
            FilesManagerScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable<SimRoute.WebApp> { backStackEntry ->
            val route = backStackEntry.toRoute<SimRoute.WebApp>()
            WebAppsScreen(
                appId = route.appId,
                title = route.title,
                initialUrl = route.initialUrl,
                onBack = { navController.popBackStack() }
            )
        }

        composable<SimRoute.DeviceSettings> {
            DeviceSettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenStorageDashboard = {
                    navController.navigate(SimRoute.Files)
                }
            )
        }
    }
}
