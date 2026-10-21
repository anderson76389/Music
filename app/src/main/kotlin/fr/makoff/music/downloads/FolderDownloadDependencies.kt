package fr.makoff.music.downloads

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.makoff.music.playback.DownloadUtil

@EntryPoint
@InstallIn(SingletonComponent::class)
interface FolderDownloadDependencies {
    fun downloadUtil(): DownloadUtil
}
