package fr.makoff.music.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import fr.makoff.music.LocalPlayerAwareWindowInsets
import fr.makoff.music.R
import fr.makoff.music.downloads.FolderAudioHistory
import fr.makoff.music.ui.component.IconButton
import fr.makoff.music.ui.utils.backToMain

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsSettings(navController: NavController) {
    Column(Modifier.windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(
        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)).verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp)) {
        Spacer(Modifier.windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)))
        FolderAudioHistory()
    }
    TopAppBar(title = { Text(stringResource(R.string.download_settings)) }, navigationIcon = {
        IconButton(onClick = navController::navigateUp, onLongClick = navController::backToMain) {
            Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
        }
    })
}
