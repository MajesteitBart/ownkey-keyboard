package dev.patrickgold.florisboard.app.settings.voxtral

import android.content.Context
import com.google.android.gms.wearable.Wearable
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val PATH_VOXTRAL_CONFIG_SYNC = "/ownkey/voxtral-config"

@Serializable
data class WearVoxtralConfig(
    val apiKey: String,
    val endpointUrl: String,
    val model: String,
    val languageHint: String,
)

object WearVoxtralSync {
    fun pushConfig(
        context: Context,
        config: WearVoxtralConfig,
        onResult: (Result<Int>) -> Unit,
    ) {
        val payload = Json.encodeToString(config).encodeToByteArray()
        val nodeClient = Wearable.getNodeClient(context)
        val messageClient = Wearable.getMessageClient(context)

        nodeClient.connectedNodes
            .addOnSuccessListener { nodes ->
                if (nodes.isEmpty()) {
                    onResult(Result.failure(IllegalStateException("Geen gekoppeld Wear OS device gevonden")))
                    return@addOnSuccessListener
                }

                var completed = 0
                var sent = 0
                var lastError: Throwable? = null

                nodes.forEach { node ->
                    messageClient.sendMessage(node.id, PATH_VOXTRAL_CONFIG_SYNC, payload)
                        .addOnSuccessListener {
                            sent += 1
                            completed += 1
                            if (completed == nodes.size) {
                                if (sent > 0) {
                                    onResult(Result.success(sent))
                                } else {
                                    onResult(Result.failure(lastError ?: IllegalStateException("Sync mislukt")))
                                }
                            }
                        }
                        .addOnFailureListener { error ->
                            lastError = error
                            completed += 1
                            if (completed == nodes.size) {
                                if (sent > 0) {
                                    onResult(Result.success(sent))
                                } else {
                                    onResult(Result.failure(lastError ?: IllegalStateException("Sync mislukt")))
                                }
                            }
                        }
                }
            }
            .addOnFailureListener { error ->
                onResult(Result.failure(error))
            }
    }
}
