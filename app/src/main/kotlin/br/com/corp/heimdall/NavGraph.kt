package br.com.corp.heimdall

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import br.com.corp.heimdall.presentation.reader.ReaderScreen
import br.com.corp.heimdall.presentation.result.ResultScreen
import br.com.corp.heimdall.presentation.setup.SetupScreen

/** Destinos de navegação do Heimdall. */
object Routes {
    const val SETUP = "setup"
    const val READER = "reader"

    /** Tela de resultado: `result/{isApproved}/{employeeName}/{photoUrl}/{denialReason}` */
    const val RESULT = "result/{isApproved}/{employeeName}/{photoUrl}/{denialReason}"

    fun result(
        isApproved: Boolean,
        employeeName: String,
        photoUrl: String,
        denialReason: String,
    ) = "result/$isApproved/${encode(employeeName)}/${encode(photoUrl)}/${encode(denialReason)}"

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value.ifBlank { "-" }, "UTF-8")
}

/**
 * Grafo de navegação principal do Heimdall.
 *
 * @param navController Controlador de navegação fornecido pela [MainActivity].
 * @param startDestination Rota inicial (setup ou reader, decidida na Activity).
 */
@Composable
fun HeimdallNavGraph(
    navController: NavHostController,
    startDestination: String,
) {
    NavHost(navController = navController, startDestination = startDestination) {

        composable(Routes.SETUP) {
            SetupScreen(
                onNavigateToReader = {
                    navController.navigate(Routes.READER) {
                        popUpTo(Routes.SETUP) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.READER) {
            ReaderScreen(
                onNavigateToResult = { isApproved, name, photo, denial ->
                    navController.navigate(Routes.result(isApproved, name, photo, denial))
                },
            )
        }

        composable(
            route = Routes.RESULT,
            arguments = listOf(
                navArgument("isApproved") { type = NavType.BoolType },
                navArgument("employeeName") { type = NavType.StringType },
                navArgument("photoUrl") { type = NavType.StringType },
                navArgument("denialReason") { type = NavType.StringType },
            ),
        ) { backStack ->
            val isApproved = backStack.arguments?.getBoolean("isApproved") ?: false
            val employeeName = backStack.arguments?.getString("employeeName")
                ?.let { java.net.URLDecoder.decode(it, "UTF-8") } ?: ""
            val photoUrl = backStack.arguments?.getString("photoUrl")
                ?.let { java.net.URLDecoder.decode(it, "UTF-8") } ?: ""
            val denialReason = backStack.arguments?.getString("denialReason")
                ?.let { java.net.URLDecoder.decode(it, "UTF-8") } ?: ""

            ResultScreen(
                isApproved = isApproved,
                employeeName = employeeName,
                photoUrl = photoUrl,
                denialReason = denialReason,
                onDismiss = { navController.popBackStack() },
            )
        }
    }
}
