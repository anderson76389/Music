package fr.makoff.music.innertubex.models.body

import fr.makoff.music.innertubex.models.Context
import kotlinx.serialization.Serializable

@Serializable
internal data class AccountsListBody(
    val context: Context,
    val requestType: String = "ACCOUNTS_LIST_REQUEST_TYPE_CHANNEL_SWITCHER",
    val callCircumstance: String = "SWITCHING_USERS_FULL",
)
