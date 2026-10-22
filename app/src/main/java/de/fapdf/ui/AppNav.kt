package de.fapdf.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import de.fapdf.ui.ai.AiScreen
import de.fapdf.ui.components.BusyDialog
import de.fapdf.ui.components.MessageDialog
import de.fapdf.ui.home.HomeNav
import de.fapdf.ui.home.HomeScreen
import de.fapdf.ui.profiles.ProfileEditScreen
import de.fapdf.ui.profiles.ProfilesScreen
import de.fapdf.ui.settings.SettingsScreen
import de.fapdf.ui.signatures.SignaturePadScreen
import de.fapdf.ui.signatures.SignaturesScreen
import de.fapdf.ui.tools.ToolScreen
import de.fapdf.ui.workspace.WorkspaceScreen

private val aiTools = setOf(Tool.AI_CHAT, Tool.AI_SUMMARY, Tool.AI_QUESTIONS, Tool.TRANSLATE)

@Composable
fun AppNav(appVm: AppViewModel) {
    val nav = rememberNavController()
    val homeNav = object : HomeNav {
        override fun workspace(docId: Long, mode: WorkspaceMode) = nav.navigate("workspace/$docId/${mode.name}")
        override fun tool(tool: Tool, docId: Long?) = nav.navigate("tool/${tool.name}?docId=${docId ?: -1}")
        override fun profiles() = nav.navigate("profiles")
        override fun signatures() = nav.navigate("signatures")
        override fun settings() = nav.navigate("settings")
    }
    NavHost(
        navController = nav, startDestination = "home",
        enterTransition = { slideInHorizontally { it / 3 } + fadeIn() },
        exitTransition = { fadeOut() },
        popEnterTransition = { fadeIn() },
        popExitTransition = { slideOutHorizontally { it / 3 } + fadeOut() },
    ) {
        composable("home") { HomeScreen(appVm, homeNav) }
        composable(
            "workspace/{docId}/{mode}",
            arguments = listOf(navArgument("docId") { type = NavType.LongType }, navArgument("mode") { type = NavType.StringType }),
        ) { entry ->
            val docId = entry.arguments?.getLong("docId") ?: return@composable
            val mode = runCatching { WorkspaceMode.valueOf(entry.arguments?.getString("mode") ?: "") }.getOrDefault(WorkspaceMode.READ)
            WorkspaceScreen(docId, mode, onBack = { nav.popBackStack() }, onNewSignature = { nav.navigate("signaturePad") }, onManageProfiles = { nav.navigate("profiles") })
        }
        composable(
            "tool/{tool}?docId={docId}",
            arguments = listOf(navArgument("tool") { type = NavType.StringType }, navArgument("docId") { type = NavType.LongType; defaultValue = -1L }),
        ) { entry ->
            val tool = runCatching { Tool.valueOf(entry.arguments?.getString("tool") ?: "") }.getOrNull() ?: return@composable
            val docId = entry.arguments?.getLong("docId")?.takeIf { it >= 0 }
            val openDoc: (Long) -> Unit = { id -> nav.navigate("workspace/$id/${WorkspaceMode.READ.name}") }
            if (tool in aiTools) AiScreen(tool, docId, appVm, onBack = { nav.popBackStack() }, onSettings = { nav.navigate("settings") }, onOpenDoc = openDoc)
            else ToolScreen(tool, docId, appVm, onBack = { nav.popBackStack() }, onOpenDoc = openDoc)
        }
        composable("profiles") { ProfilesScreen(appVm, onBack = { nav.popBackStack() }) { id -> nav.navigate("profile/$id") } }
        composable("profile/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            ProfileEditScreen(appVm, entry.arguments?.getLong("id") ?: 0L) { nav.popBackStack() }
        }
        composable("signatures") { SignaturesScreen(appVm, onBack = { nav.popBackStack() }) { nav.navigate("signaturePad") } }
        composable("signaturePad") { SignaturePadScreen(appVm) { nav.popBackStack() } }
        composable("settings") { SettingsScreen(appVm, onBack = { nav.popBackStack() }, onProfiles = { nav.navigate("profiles") }, onSignatures = { nav.navigate("signatures") }) }
    }
    // Neue Datei aus einer anderen App → zurück zur Startseite, dort erscheint das Aktionsmenü
    val pending = appVm.actionDocId
    LaunchedEffect(pending) { if (pending != null) nav.popBackStack("home", inclusive = false) }
    appVm.busy?.let { BusyDialog(it) }
    appVm.error?.let { MessageDialog("Datei kann nicht geöffnet werden", it) { appVm.error = null } }
}
