package it.citiverse.app.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import it.citiverse.app.CitiverseApp
import it.citiverse.app.data.NotifMode
import it.citiverse.app.push.PushController
import it.citiverse.app.ui.category.CategoryScreen
import it.citiverse.app.ui.communities.CommunitiesScreen
import it.citiverse.app.ui.feed.FeedScreen
import it.citiverse.app.ui.login.LoginScreen
import it.citiverse.app.ui.notifications.NotificationsScreen
import it.citiverse.app.ui.settings.BatteryHelpScreen
import it.citiverse.app.ui.settings.SettingsScreen
import it.citiverse.app.ui.settings.requestIgnoreBattery
import it.citiverse.app.ui.theme.Cv
import it.citiverse.app.ui.theme.CvIcons
import it.citiverse.app.ui.topic.NewTopicScreen
import it.citiverse.app.ui.topic.TopicScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab(Routes.HOME, "Home", CvIcons.Home),
    Tab(Routes.COMMUNITIES, "Comunità", CvIcons.Compass),
    Tab(Routes.NOTIFICATIONS, "Notifiche", CvIcons.Bell),
    Tab(Routes.SETTINGS, "Profilo", CvIcons.User),
)

@Composable
fun AppRoot(pendingRoute: String?, onPendingConsumed: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as CitiverseApp
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route
    val unread by app.unreadCount.collectAsState()
    val me by app.prefs.me.collectAsState()
    val mode by app.prefs.notifMode.collectAsState()

    val navigate: (String) -> Unit = { route ->
        if (route in Routes.TABS) {
            nav.navigate(route) {
                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        } else {
            nav.navigate(route)
        }
    }
    val back: () -> Unit = { if (!nav.popBackStack()) navigate(Routes.HOME) }

    LaunchedEffect(pendingRoute) {
        if (pendingRoute != null) {
            navigate(pendingRoute)
            onPendingConsumed()
        }
    }

    // Una volta sola, dopo il login: spiega perché serve escludere l'app dall'ottimizzazione batteria.
    var batteryDialog by remember { mutableStateOf(false) }
    LaunchedEffect(me?.uid, mode) {
        batteryDialog = me != null && mode == NotifMode.REALTIME && !app.prefs.askedBatteryOnce &&
            !PushController.isIgnoringBatteryOptimizations(ctx)
    }

    Scaffold(
        containerColor = Cv.colors.bg,
        bottomBar = {
            if (current != null && current in Routes.TABS) {
                NavigationBar(containerColor = Cv.colors.card, tonalElevation = 0.dp) {
                    TABS.forEach { tab ->
                        NavigationBarItem(
                            selected = current == tab.route,
                            onClick = {
                                if (current == tab.route && tab.route == Routes.HOME) app.homeReselect.value++
                                else navigate(tab.route)
                            },
                            icon = {
                                if (tab.route == Routes.NOTIFICATIONS && unread > 0) {
                                    BadgedBox(badge = { Badge { Text(if (unread > 99) "99+" else unread.toString()) } }) {
                                        Icon(tab.icon, null, modifier = Modifier.size(24.dp))
                                    }
                                } else {
                                    Icon(tab.icon, null, modifier = Modifier.size(24.dp))
                                }
                            },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = Cv.colors.pill),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(navController = nav, startDestination = Routes.HOME) {
            composable(Routes.HOME) { FeedScreen(navigate, padding) }
            composable(Routes.COMMUNITIES) { CommunitiesScreen(navigate, padding) }
            composable(Routes.NOTIFICATIONS) { NotificationsScreen(navigate, padding) }
            composable(Routes.SETTINGS) { SettingsScreen(navigate, padding) }
            composable(Routes.LOGIN) { LoginScreen(done = { nav.popBackStack() }, back = back) }
            composable(Routes.BATTERY) { BatteryHelpScreen(back) }
            composable(
                Routes.CATEGORY,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { e ->
                CategoryScreen(e.arguments?.getString("id") ?: "", navigate, back)
            }
            composable(
                Routes.TOPIC,
                arguments = listOf(
                    navArgument("tid") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("pid") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) { e ->
                TopicScreen(e.arguments?.getString("tid"), e.arguments?.getString("pid"), navigate, back)
            }
            composable(
                Routes.NEW_TOPIC,
                arguments = listOf(navArgument("cid") { type = NavType.StringType }),
            ) { e ->
                NewTopicScreen(
                    cid = e.arguments?.getString("cid") ?: "",
                    openTopic = { tid ->
                        nav.popBackStack()
                        navigate(Routes.topic(tid))
                    },
                    back = back,
                )
            }
        }
    }

    if (batteryDialog) {
        AlertDialog(
            onDismissRequest = {
                app.prefs.askedBatteryOnce = true
                batteryDialog = false
            },
            title = { Text("Notifiche senza Google") },
            text = {
                Text(
                    "Per ricevere le notifiche in tempo reale l'app tiene aperta una connessione con Citiverse. " +
                        "Escludila dall'ottimizzazione della batteria, altrimenti Android potrebbe chiuderla. " +
                        "Puoi cambiare idea nelle impostazioni (c'è anche il controllo ogni 15 minuti)."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    app.prefs.askedBatteryOnce = true
                    batteryDialog = false
                    requestIgnoreBattery(ctx)
                }) { Text("Escludi") }
            },
            dismissButton = {
                TextButton(onClick = {
                    app.prefs.askedBatteryOnce = true
                    batteryDialog = false
                }) { Text("Non ora") }
            },
        )
    }
}
