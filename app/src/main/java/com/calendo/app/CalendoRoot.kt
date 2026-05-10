package com.calendo.app

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.calendo.app.ui.CalendoViewModel
import com.calendo.app.ui.screens.DayRoute
import com.calendo.app.ui.screens.MonthPlaceholderScreen
import com.calendo.app.ui.screens.WeekPlaceholderScreen

private enum class MainTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    Day("day", "日", Icons.Outlined.CalendarToday),
    Week("week", "周", Icons.Outlined.DateRange),
    Month("month", "月", Icons.Outlined.CalendarMonth),
}

@Composable
fun CalendoRoot() {
    val navController = rememberNavController()
    val vm: CalendoViewModel = viewModel()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
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
        NavHost(
            navController = navController,
            startDestination = MainTab.Day.route,
            modifier = Modifier.padding(paddingValues),
        ) {
            composable(MainTab.Day.route) {
                DayRoute(vm = vm)
            }
            composable(MainTab.Week.route) {
                WeekPlaceholderScreen()
            }
            composable(MainTab.Month.route) {
                MonthPlaceholderScreen()
            }
        }
    }
}
