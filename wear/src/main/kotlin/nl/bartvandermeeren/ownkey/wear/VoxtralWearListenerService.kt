package nl.bartvandermeeren.ownkey.wear

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class VoxtralWearListenerService : WearableListenerService() {
    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)

        if (messageEvent.path != PATH_VOXTRAL_CONFIG_SYNC) {
            return
        }

        val payload = runCatching {
            Json.decodeFromString<WearVoxtralConfig>(String(messageEvent.data, Charsets.UTF_8))
        }.getOrNull() ?: return

        val store = WearSettingsStore(this)
        store.setApiKey(payload.apiKey.trim())
        store.setEndpointUrl(payload.endpointUrl.trim())
        store.setModel(payload.model.trim())
        store.setLanguageHint(payload.languageHint.trim())
    }
}

@Serializable
data class WearVoxtralConfig(
    val apiKey: String,
    val endpointUrl: String,
    val model: String,
    val languageHint: String,
)

const val PATH_VOXTRAL_CONFIG_SYNC = "/ownkey/voxtral-config"
