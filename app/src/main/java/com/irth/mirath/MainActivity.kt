package com.irth.mirath

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.irth.mirath.data.Repo
import com.irth.mirath.store.Prefs
import com.irth.mirath.ui.*
import com.irth.mirath.ui.theme.MirathTheme
import com.irth.mirath.ui.theme.palOf
import kotlinx.serialization.Serializable

@Serializable object Home
@Serializable object Fav
@Serializable object Stats
@Serializable data class ScienceR(val id: String)
@Serializable data class SeriesR(val id: String)
@Serializable data class LessonR(val id: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(applicationContext)
        enableEdgeToEdge()
        setContent { MirathApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MirathApp() {
    val themeName by Prefs.theme
    val p = palOf(themeName)
    val nav = rememberNavController()
    var showTheme by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var booted by remember { mutableStateOf(Repo.sciences != null) }
    val backEntry by nav.currentBackStackEntryAsState()
    val route = backEntry?.destination?.route ?: ""

    LaunchedEffect(Unit) {
        runCatching { Repo.boot() }
        booted = true
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MirathTheme(themeName) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = p.bg,
                topBar = {
                    val title = when {
                        route.contains("LessonR") -> "ميراث"
                        route.contains("SeriesR") -> Repo.seriesOf(backEntry?.arguments?.getString("id") ?: "")?.title ?: "ميراث"
                        route.contains("ScienceR") -> "إرث"
                        route.contains("Fav") -> "المفضلة"
                        route.contains("Stats") -> "إحصائي"
                        else -> "إرث"
                    }
                    TopGlassBar(
                        p, title,
                        onTheme = { showTheme = true },
                        onSearch = { showSearch = true },
                        onBack = if (route.contains("ScienceR") || route.contains("SeriesR") || route.contains("LessonR")) {
                            { nav.popBackStack() }
                        } else null,
                    )
                },
                bottomBar = {
                    NavigationBar(containerColor = p.bg, contentColor = p.muted) {
                        NavItem(p, route.contains("Home") || route.isEmpty(), "الرئيسية", Icons.Filled.Home) {
                            nav.navigate(Home) { popUpTo(Home) { inclusive = true } }
                        }
                        NavItem(p, route.contains("Fav"), "المفضلة", Icons.Filled.Favorite) { nav.navigate(Fav) }
                        NavItem(p, route.contains("Stats"), "إحصائي", Icons.Filled.BarChart) { nav.navigate(Stats) }
                    }
                },
            ) { pad ->
                if (!booted) {
                    LoadingBox(p)
                } else {
                    NavHost(
                        navController = nav,
                        startDestination = Home,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding()),
                    ) {
                        composable<Home> { HomeScreen(p) { id -> nav.navigate(ScienceR(id)) } }
                        composable<Fav> { FavScreen(p) { id -> nav.navigate(LessonR(id)) } }
                        composable<Stats> { StatsScreen(p) }
                        composable<ScienceR> { e ->
                            val r = e.toRoute<ScienceR>()
                            ScienceScreen(p, r.id, onSeries = { nav.navigate(SeriesR(it)) }, onLesson = { nav.navigate(LessonR(it)) })
                        }
                        composable<SeriesR> { e ->
                            val r = e.toRoute<SeriesR>()
                            SeriesScreen(p, r.id) { nav.navigate(LessonR(it)) }
                        }
                        composable<LessonR> { e ->
                            val r = e.toRoute<LessonR>()
                            LessonScreen(p, r.id)
                        }
                    }
                }
            }

            if (showTheme) {
                ModalBottomSheet(onDismissRequest = { showTheme = false }, containerColor = p.card) {
                    ThemeSheet(p, themeName) { Prefs.setTheme(it); showTheme = false }
                }
            }
            if (showSearch) {
                ModalBottomSheet(onDismissRequest = { showSearch = false }, containerColor = p.card) {
                    SearchSheet(p) { id -> showSearch = false; nav.navigate(LessonR(id)) }
                }
            }
        }
    }
}

@Composable
private fun RowScope.NavItem(p: com.irth.mirath.ui.theme.Pal, on: Boolean, label: String, ic: androidx.compose.ui.graphics.vector.ImageVector, click: () -> Unit) {
    NavigationBarItem(
        selected = on, onClick = click,
        icon = { Icon(ic, label) },
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = p.primary, selectedTextColor = p.primary,
            unselectedIconColor = p.muted, unselectedTextColor = p.muted,
            indicatorColor = p.soft,
        ),
    )
}
