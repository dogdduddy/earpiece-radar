package com.dogdduddy.earpieceradar.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dogdduddy.earpieceradar.ui.simple.SimpleScreen
import com.dogdduddy.earpieceradar.ui.simple.SimpleViewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dogdduddy.earpieceradar.AppContainer
import com.dogdduddy.earpieceradar.ui.locate.LocateScreen
import com.dogdduddy.earpieceradar.ui.locate.LocateViewModel
import com.dogdduddy.earpieceradar.ui.monitor.MonitorScreen
import com.dogdduddy.earpieceradar.ui.monitor.MonitorViewModel
import com.dogdduddy.earpieceradar.ui.record.RecordScreen
import com.dogdduddy.earpieceradar.ui.record.RecordViewModel
import com.dogdduddy.earpieceradar.ui.results.ResultsScreen
import com.dogdduddy.earpieceradar.ui.results.ResultsViewModel
import com.dogdduddy.earpieceradar.ui.settings.SettingsScreen
import com.dogdduddy.earpieceradar.ui.settings.SettingsViewModel

enum class Dest(val route: String, val label: String, val icon: ImageVector) {
    Monitor("monitor", "모니터", Icons.Filled.Radar),
    Record("record", "기록", Icons.Filled.FiberManualRecord),
    Results("results", "결과", Icons.Filled.Analytics),
    Locate("locate", "위치", Icons.Filled.TravelExplore),
    Settings("settings", "설정", Icons.Filled.Settings),
}

@Composable
fun RadarApp(container: AppContainer) {
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle()
    if (!settings.developerMode) {
        val vm: SimpleViewModel = viewModel(factory = factory {
            SimpleViewModel(container.signalHub, container.settingsRepository, container.profileRepository)
        })
        SimpleScreen(vm)
        return
    }
    DeveloperApp(container)
}

@Composable
private fun DeveloperApp(container: AppContainer) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                Dest.entries.forEach { d ->
                    NavigationBarItem(
                        selected = currentRoute == d.route,
                        onClick = {
                            nav.navigate(d.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(d.icon, contentDescription = d.label) },
                        label = { Text(d.label) },
                    )
                }
            }
        },
    ) { inner ->
        NavHost(nav, startDestination = Dest.Monitor.route, modifier = Modifier.padding(inner)) {
            composable(Dest.Monitor.route) {
                val vm: MonitorViewModel = viewModel(factory = factory { MonitorViewModel(container.signalHub, container.settingsRepository, container.profileRepository) })
                MonitorScreen(vm)
            }
            composable(Dest.Record.route) {
                val vm: RecordViewModel = viewModel(factory = factory {
                    RecordViewModel(container.signalHub, container.settingsRepository, container.trialRepository, container.profileRepository)
                })
                RecordScreen(vm)
            }
            composable(Dest.Results.route) {
                val vm: ResultsViewModel = viewModel(factory = factory {
                    ResultsViewModel(container.trialRepository, container.csvExporter)
                })
                ResultsScreen(vm)
            }
            composable(Dest.Locate.route) {
                val vm: LocateViewModel = viewModel(factory = factory { LocateViewModel(container.signalHub, container.settingsRepository) })
                LocateScreen(vm)
            }
            composable(Dest.Settings.route) {
                val vm: SettingsViewModel = viewModel(factory = factory { SettingsViewModel(container.settingsRepository, container.profileRepository, container.signalHub) })
                SettingsScreen(vm)
            }
        }
    }
}
