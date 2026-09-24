package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.MaxViewModel
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LogsScreen
import com.example.ui.screens.MemoryScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.YouTubeSandboxScreen
import com.example.ui.theme.MaxTheme
import com.example.ui.theme.PrimaryIndigo
import com.example.ui.theme.SurfaceDark

enum class Screen(val route: String, val title: String, val icon: ImageVector) {
    HOME("home", "Assistant", Icons.Default.Mic),
    YOUTUBE("youtube", "YouTube", Icons.Default.SmartDisplay),
    MEMORY("memory", "Memory", Icons.Default.Bookmark),
    LOGS("logs", "Logs", Icons.Default.History),
    SETTINGS("settings", "Settings", Icons.Default.Settings)
}

class MainActivity : ComponentActivity() {

    private val viewModel: MaxViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MaxTheme {
                val navController = rememberNavController()

                // Multiple permissions launcher (Audio, Phone State, Contacts, Calls, Location, Notifications)
                val permissionsLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) {
                    viewModel.checkServiceStatus()
                }

                LaunchedEffect(Unit) {
                    val permissionsList = mutableListOf(
                        Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.CAMERA,
                        Manifest.permission.READ_PHONE_STATE,
                        Manifest.permission.ANSWER_PHONE_CALLS,
                        Manifest.permission.READ_CONTACTS,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )

                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        permissionsList.add(Manifest.permission.POST_NOTIFICATIONS)
                    }

                    val permissionsNeeded = permissionsList.filter {
                        ContextCompat.checkSelfPermission(this@MainActivity, it) != PackageManager.PERMISSION_GRANTED
                    }.toTypedArray()

                    if (permissionsNeeded.isNotEmpty()) {
                        permissionsLauncher.launch(permissionsNeeded)
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        val navBackStackEntry by navController.currentBackStackEntryAsState()
                        val currentRoute = navBackStackEntry?.destination?.route

                        NavigationBar(
                            containerColor = SurfaceDark,
                            contentColor = Color.White
                        ) {
                            Screen.entries.forEach { screen ->
                                NavigationBarItem(
                                    icon = { Icon(screen.icon, contentDescription = screen.title) },
                                    label = { Text(screen.title, fontSize = 10.sp) },
                                    selected = currentRoute == screen.route,
                                    onClick = {
                                        if (currentRoute != screen.route) {
                                            navController.navigate(screen.route) {
                                                popUpTo(navController.graph.startDestinationId) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Color.White,
                                        selectedTextColor = PrimaryIndigo,
                                        indicatorColor = PrimaryIndigo,
                                        unselectedIconColor = Color.Gray,
                                        unselectedTextColor = Color.Gray
                                    ),
                                    modifier = Modifier.testTag("nav_${screen.route}")
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = Screen.HOME.route,
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable(Screen.HOME.route) {
                            HomeScreen(viewModel = viewModel)
                        }
                        composable(Screen.YOUTUBE.route) {
                            YouTubeSandboxScreen(viewModel = viewModel)
                        }
                        composable(Screen.MEMORY.route) {
                            MemoryScreen(viewModel = viewModel)
                        }
                        composable(Screen.LOGS.route) {
                            LogsScreen(viewModel = viewModel)
                        }
                        composable(Screen.SETTINGS.route) {
                            SettingsScreen(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkServiceStatus()
        if (intent?.getBooleanExtra("EXTRA_AUTO_START_LISTENING", false) == true) {
            intent?.removeExtra("EXTRA_AUTO_START_LISTENING")
            viewModel.startListening()
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("EXTRA_AUTO_START_LISTENING", false)) {
            intent.removeExtra("EXTRA_AUTO_START_LISTENING")
            viewModel.startListening()
        }
    }
}
