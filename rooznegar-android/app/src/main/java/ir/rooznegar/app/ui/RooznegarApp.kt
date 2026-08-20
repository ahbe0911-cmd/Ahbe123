package ir.rooznegar.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ir.rooznegar.app.ui.screens.CalendarScreen
import ir.rooznegar.app.ui.screens.NotesScreen
import ir.rooznegar.app.ui.screens.QuickAddSheet
import ir.rooznegar.app.ui.screens.SettingsScreen
import ir.rooznegar.app.ui.screens.TasksScreen
import ir.rooznegar.app.ui.screens.TodayScreen
import ir.rooznegar.app.ui.theme.RooznegarTheme

private data class NavItem(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
private val items = listOf(
    NavItem("today", "امروز", Icons.Default.Today),
    NavItem("calendar", "تقویم", Icons.Default.CalendarMonth),
    NavItem("tasks", "کارها", Icons.Default.CheckCircle),
    NavItem("notes", "یادداشت", Icons.Default.Description)
)

@Composable
fun RooznegarApp(vm: AppViewModel = viewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    RooznegarTheme(settings.themeMode) {
        val nav = rememberNavController()
        var quickAdd by remember { mutableStateOf(false) }
        val backStack by nav.currentBackStackEntryAsState()
        val current = backStack?.destination?.route
        val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 8.dp) {
                    items.forEach { item ->
                        NavigationBarItem(
                            selected = current == item.route,
                            onClick = {
                                nav.navigate(item.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, item.label) },
                            label = { Text(item.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { quickAdd = true },
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) { Icon(Icons.Default.Add, "افزودن سریع") }
            }
        ) { padding ->
            Box(Modifier.padding(padding)) {
                NavHost(navController = nav, startDestination = "today") {
                    composable("today") { TodayScreen(vm, onSettings = { nav.navigate("settings") }) }
                    composable("calendar") { CalendarScreen(vm) }
                    composable("tasks") { TasksScreen(vm) }
                    composable("notes") { NotesScreen(vm) }
                    composable("settings") {
                        SettingsScreen(
                            vm = vm,
                            onRequestNotificationPermission = {
                                if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        )
                    }
                }
            }
        }
        if (quickAdd) QuickAddSheet(vm = vm, onDismiss = { quickAdd = false })
    }
}
