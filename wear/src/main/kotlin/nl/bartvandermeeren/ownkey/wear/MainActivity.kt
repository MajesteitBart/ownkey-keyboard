package nl.bartvandermeeren.ownkey.wear

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settingsStore = WearSettingsStore(this)
        val client = VoxtralWearClient()

        setContent {
            MaterialTheme {
                val context = LocalContext.current
                val scope = rememberCoroutineScope()

                var apiKey by remember { mutableStateOf(settingsStore.getApiKey()) }
                var endpointUrl by remember {
                    mutableStateOf(
                        settingsStore.getEndpointUrl().ifBlank { VoxtralWearClient.DefaultEndpointUrl },
                    )
                }
                var model by remember {
                    mutableStateOf(
                        settingsStore.getModel().ifBlank { VoxtralWearClient.DefaultModel },
                    )
                }
                var languageHint by remember { mutableStateOf(settingsStore.getLanguageHint()) }

                var transcript by remember { mutableStateOf("") }
                var statusText by remember { mutableStateOf("Klaar") }
                var isListening by remember { mutableStateOf(false) }
                var isTranscribing by remember { mutableStateOf(false) }
                var activeSession by remember { mutableStateOf<RecordingSession?>(null) }

                var hasMicPermission by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                            PackageManager.PERMISSION_GRANTED,
                    )
                }

                val requestMicPermission = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { granted ->
                    hasMicPermission = granted
                    if (!granted) {
                        statusText = "Microfoon-permissie ontbreekt"
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Ownkey Wear Transcribe", style = MaterialTheme.typography.titleMedium)
                    Text(statusText, style = MaterialTheme.typography.bodySmall)

                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = {
                            apiKey = it
                            settingsStore.setApiKey(it.trim())
                        },
                        label = { Text(context.getString(R.string.api_key_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    OutlinedTextField(
                        value = endpointUrl,
                        onValueChange = {
                            endpointUrl = it
                            settingsStore.setEndpointUrl(it.trim())
                        },
                        label = { Text(context.getString(R.string.endpoint_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    OutlinedTextField(
                        value = model,
                        onValueChange = {
                            model = it
                            settingsStore.setModel(it.trim())
                        },
                        label = { Text(context.getString(R.string.model_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    OutlinedTextField(
                        value = languageHint,
                        onValueChange = {
                            languageHint = it
                            settingsStore.setLanguageHint(it.trim())
                        },
                        label = { Text(context.getString(R.string.language_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Button(
                        onClick = {
                            if (isTranscribing) return@Button

                            if (!isListening) {
                                if (!hasMicPermission) {
                                    requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
                                    return@Button
                                }
                                if (apiKey.isBlank()) {
                                    statusText = "Vul eerst je API key in"
                                    return@Button
                                }

                                val started = startRecording(context)
                                if (started == null) {
                                    statusText = "Opname starten mislukt"
                                } else {
                                    activeSession = started
                                    isListening = true
                                    statusText = "Luistert... tik opnieuw om te transcriberen"
                                }
                                return@Button
                            }

                            val session = activeSession
                            if (session == null) {
                                isListening = false
                                statusText = "Geen actieve opname"
                                return@Button
                            }

                            isListening = false
                            isTranscribing = true
                            statusText = "Audio verwerken..."

                            scope.launch {
                                val audio = stopRecording(session).getOrElse { error ->
                                    activeSession = null
                                    statusText = error.message ?: "Opname stoppen mislukt"
                                    isTranscribing = false
                                    return@launch
                                }
                                activeSession = null

                                val transcriptionResult = withContext(Dispatchers.IO) {
                                    client.transcribe(
                                        apiKey = apiKey.trim(),
                                        endpointUrl = endpointUrl.trim().ifBlank { VoxtralWearClient.DefaultEndpointUrl },
                                        model = model.trim().ifBlank { VoxtralWearClient.DefaultModel },
                                        languageHint = languageHint.trim(),
                                        recording = audio,
                                    )
                                }

                                transcriptionResult
                                    .onSuccess { text ->
                                        transcript = text
                                        statusText = "Klaar"
                                    }
                                    .onFailure { error ->
                                        statusText = error.message ?: "Transcriptie mislukt"
                                    }

                                isTranscribing = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (isListening) context.getString(R.string.stop_and_transcribe) else context.getString(R.string.start_recording))
                    }

                    if (transcript.isNotBlank()) {
                        Text("Transcript:", style = MaterialTheme.typography.titleSmall)
                        Text(transcript, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
