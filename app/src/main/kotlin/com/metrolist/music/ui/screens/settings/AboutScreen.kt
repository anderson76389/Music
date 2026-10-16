/* Modified for MAKOFF Music. Original Metrolist contributors, GPL-3.0. */
package fr.makoff.music.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import fr.makoff.music.BuildConfig

@Composable
fun AboutScreen(navController: NavController) {
    val uriHandler = LocalUriHandler.current
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
        .verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TextButton(onClick = { navController.navigateUp() }) { Text("Retour") }
        Text("MAKOFF Music", style = MaterialTheme.typography.headlineLarge)
        Text("Version dérivée : ${BuildConfig.VERSION_NAME}")
        Text("Client YouTube Music libre et indépendant, maintenu dans votre dépôt Music.")
        TextButton(onClick = { uriHandler.openUri("https://github.com/anderson76389/Music") }) { Text("Code source et notices") }
        Text("Crédits", style = MaterialTheme.typography.titleLarge)
        Text("Les licences, droits d’auteur et crédits des composants sont conservés dans les sources et dans les notices distribuées avec l’application.")
        Text("Licence GNU GPL version 3. Le code source et les instructions de construction doivent accompagner chaque distribution.")
        Text("YouTube fournit le catalogue et les flux. Les paroles et les intégrations optionnelles utilisent des services tiers. L’écoute partagée nécessite votre propre serveur. Les versions publiées sont proposées depuis le dépôt Music.")
    }
}
