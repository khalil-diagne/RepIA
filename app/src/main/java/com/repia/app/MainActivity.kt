package com.repia.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.repia.app.ui.components.RepIABottomBar
import com.repia.app.ui.history.HistoryScreen
import com.repia.app.ui.home.HomeScreen
import com.repia.app.ui.onboarding.OnboardingScreen
import com.repia.app.ui.profile.ProfileScreen
import com.repia.app.ui.theme.RepIATheme
import com.repia.app.ui.workout.WorkoutScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var isDarkMode by remember { mutableStateOf(true) }
            var showOnboarding by remember { mutableStateOf(false) }

            RepIATheme(darkTheme = isDarkMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (showOnboarding) {
                        OnboardingScreen(
                            onFinishOnboarding = { showOnboarding = false }
                        )
                    } else {
                        val navController = rememberNavController()
                        val navBackStackEntry by navController.currentBackStackEntryAsState()
                        val currentRoute = navBackStackEntry?.destination?.route ?: "home"

                        val showBottomBar = currentRoute in listOf("home", "history", "profile")

                        Scaffold(
                            bottomBar = {
                                if (showBottomBar) {
                                    RepIABottomBar(
                                        currentRoute = currentRoute,
                                        onNavigate = { route ->
                                            if (route != currentRoute) {
                                                navController.navigate(route) {
                                                    popUpTo("home") { saveState = true }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            }
                                        },
                                        onQuickStart = {
                                            navController.navigate("workout/pompes")
                                        }
                                    )
                                }
                            }
                        ) { innerPadding ->
                            Box(modifier = Modifier.padding(innerPadding)) {
                                NavHost(navController = navController, startDestination = "home") {
                                    composable("home") {
                                        HomeScreen(
                                            isDarkMode = isDarkMode,
                                            onToggleTheme = { isDarkMode = !isDarkMode },
                                            onSelectExercise = { exerciseId ->
                                                navController.navigate("workout/$exerciseId")
                                            }
                                        )
                                    }
                                    composable("history") {
                                        HistoryScreen(
                                            onBack = { navController.popBackStack() }
                                        )
                                    }
                                    composable("profile") {
                                        ProfileScreen(
                                            onBack = { navController.popBackStack() }
                                        )
                                    }
                                    composable(
                                        route = "workout/{exerciseId}",
                                        arguments = listOf(navArgument("exerciseId") { type = NavType.StringType })
                                    ) { backStackEntry ->
                                        val exerciseId = backStackEntry.arguments?.getString("exerciseId") ?: "pompes"
                                        WorkoutScreen(
                                            exerciseId = exerciseId,
                                            onBack = {
                                                navController.popBackStack()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
