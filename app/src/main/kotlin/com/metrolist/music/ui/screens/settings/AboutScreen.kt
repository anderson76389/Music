/* Modified for MAKOFF Music. Original Metrolist contributors, GPL-3.0. */
package fr.makoff.music.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import fr.makoff.music.BuildConfig

@Composable
fun AboutScreen(navController: NavController) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
        .verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TextButton(onClick = { navController.navigateUp() }) { Text("Retour") }
        Text("MAKOFF Music", style = MaterialTheme.typography.headlineLarge)
        Text("Version dérivée : ${BuildConfig.VERSION_NAME}")
        Text("Lecteur Android autonome basé sur Metrolist. Ce projet n’est pas affilié à Metrolist.")
        Text("Crédits", style = MaterialTheme.typography.titleLarge)
        Text("Metrolist et ses contributeurs ; InnerTune (Zion Huang, Malopieds) ; OuterTune (Davide Garberi, Michael Zh) ; InnerTubeX ; Better Lyrics ; SimpMusic Lyrics ; MusicRecognizer. Les notices originales sont conservées dans les sources.")
        Text("Licence GNU GPL version 3. Le code source et les instructions de construction doivent accompagner chaque distribution.")
        Text("YouTube fournit le catalogue et les flux. Les paroles et les intégrations optionnelles utilisent des services tiers. L’écoute partagée nécessite votre propre serveur. Les mises à jour automatiques ne sont pas encore configurées.")
    }
}
