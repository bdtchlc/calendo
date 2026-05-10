package com.calendo.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.calendo.app.ui.CalendoViewModel
import com.calendo.app.ui.components.EventEditorBottomSheet
import com.calendo.app.ui.screens.CalendarRoute
import com.calendo.app.ui.screens.DayRoute
import com.calendo.app.ui.screens.ProfileRoute
import com.calendo.app.ui.screens.TasksRoute

private enum class MainTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    Home("home", "今天", Icons.Outlined.Home),
    Calendar("calendar", "日历", Icons.Outlined.CalendarMonth),
    Tasks("tasks", "任务", Icons.Outlined.TaskAlt),
    Profile("profile", "我的", Icons.Outlined.Person),
}

@Composable
fun CalendoRoot() {
    val navController = rememberNavController()
    val vm: CalendoViewModel = viewModel()
    val uiState by vm.state.collectAsStateWithLifecycle()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                tonalElevation = 0.dp,
                windowInsets = NavigationBarDefaults.windowInsets,
            ) {
                MainTab.entries.forEach { tab ->
                    val selected = currentRoute == tab.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth()
                    .widthIn(max = 500.dp)
                    .background(Color.Transparent),
            ) {
                NavHost(
                    navController = navController,
                    startDestination = MainTab.Home.route,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    composable(MainTab.Home.route) {
                        DayRoute(vm = vm)
                    }
                    composable(MainTab.Calendar.route) {
                        CalendarRoute(vm = vm)
                    }
                    composable(MainTab.Tasks.route) {
                        TasksRoute(vm = vm)
                    }
                    composable(MainTab.Profile.route) {
                        ProfileRoute(vm = vm)
                    }
                }
            }

            EventEditorBottomSheet(
                state = uiState.eventEditor,
                onDismiss = vm::dismissEventEditor,
                onSave = vm::addOrUpdateItem,
                onDelete = vm::deleteItem,
            )
        }
    }
}
