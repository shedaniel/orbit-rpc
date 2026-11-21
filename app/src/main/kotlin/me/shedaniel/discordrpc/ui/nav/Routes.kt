package me.shedaniel.discordrpc.ui.nav

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.snapshots.SnapshotStateList

enum class Tab { NOW, RULES, APPS, DATA }

sealed interface Route {
    data object Main : Route
    data object Onboarding : Route
    data object Login : Route
    data object Settings : Route
    data object Log : Route
    /** [id] null = new rule seeded from [preset]. */
    data class RuleEditor(val id: String?, val preset: String? = null) : Route
}

class Navigator(val backStack: SnapshotStateList<Route>) {
    fun push(route: Route) {
        if (backStack.lastOrNull() != route) backStack.add(route)
    }
    fun pop() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }
    fun replaceAll(route: Route) {
        backStack.clear()
        backStack.add(route)
    }
}

val LocalNavigator = compositionLocalOf<Navigator> { error("No navigator") }
