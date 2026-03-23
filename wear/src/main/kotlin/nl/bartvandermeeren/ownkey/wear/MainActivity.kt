package nl.bartvandermeeren.ownkey.wear

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.bartvandermeeren.ownkey.wear.ui.theme.AppPrimary
import nl.bartvandermeeren.ownkey.wear.ui.theme.AppSurfaceStrong
import nl.bartvandermeeren.ownkey.wear.ui.theme.OwnkeyTokens
import nl.bartvandermeeren.ownkey.wear.ui.theme.OwnkeyWearTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settingsStore = WearSettingsStore(this)
        val client = VoxtralWearClient()

        setContent {
            OwnkeyWearTheme {
                WearApp(
                    settingsStore = settingsStore,
                    client = client,
                )
            }
        }
    }
}

private enum class WearRoute {
    Onboarding,
    Home,
    Recording,
    Processing,
    Review,
    Sessions,
}

private data class EditableTranscriptSegment(
    val id: Int,
    val speaker: String,
    val timestampLabel: String,
    val text: String,
    val isEditing: Boolean = false,
)

@Composable
private fun WearApp(
    settingsStore: WearSettingsStore,
    client: VoxtralWearClient,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val spacing = OwnkeyTokens.spacing

    var apiKey by rememberSaveable { mutableStateOf(settingsStore.getApiKey()) }
    var endpointUrl by rememberSaveable {
        mutableStateOf(settingsStore.getEndpointUrl().ifBlank { VoxtralWearClient.DefaultEndpointUrl })
    }
    var model by rememberSaveable {
        mutableStateOf(settingsStore.getModel().ifBlank { VoxtralWearClient.DefaultModel })
    }
    var languageHint by rememberSaveable { mutableStateOf(settingsStore.getLanguageHint()) }

    var sessions by remember { mutableStateOf(settingsStore.getSavedSessions()) }
    var statusText by rememberSaveable { mutableStateOf("Klaar") }

    var route by rememberSaveable {
        mutableStateOf(
            if (apiKey.isBlank()) WearRoute.Onboarding else WearRoute.Home,
        )
    }

    var activeSession by remember { mutableStateOf<RecordingSession?>(null) }
    var preparedRecording by remember { mutableStateOf<AudioRecording?>(null) }
    var transcriptSegments by remember { mutableStateOf<List<EditableTranscriptSegment>>(emptyList()) }
    var activeReviewSessionId by rememberSaveable { mutableStateOf<String?>(null) }
    var reviewDurationMs by rememberSaveable { mutableStateOf(0L) }

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
        statusText = if (granted) "Microfoon-permissie geactiveerd" else "Microfoon-permissie ontbreekt"
    }

    val elapsedRecordingMs by produceState(
        initialValue = 0L,
        key1 = activeSession?.startedAtEpochMs,
    ) {
        while (activeSession != null) {
            val startedAt = activeSession?.startedAtEpochMs ?: break
            value = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
            delay(180L)
        }
        value = preparedRecording?.durationMs ?: 0L
    }

    fun persistProviderSettings() {
        settingsStore.setApiKey(apiKey.trim())
        settingsStore.setEndpointUrl(endpointUrl.trim())
        settingsStore.setModel(model.trim())
        settingsStore.setLanguageHint(languageHint.trim())
    }

    fun resetRecordingState() {
        preparedRecording?.recordingUri?.let { uri ->
            runCatching {
                context.contentResolver.delete(android.net.Uri.parse(uri), null, null)
            }
        }
        preparedRecording = null
        activeSession = null
        reviewDurationMs = 0L
    }

    fun startNewRecording() {
        if (!hasMicPermission) {
            requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        if (apiKey.isBlank()) {
            statusText = "Vul eerst je API key in"
            route = WearRoute.Onboarding
            return
        }

        resetRecordingState()
        val started = startRecording(context)
        if (started == null) {
            statusText = "Opname starten mislukt"
            return
        }

        activeSession = started
        statusText = "Luistert"
    }

    fun stopActiveRecording() {
        val session = activeSession ?: return
        stopRecording(session)
            .onSuccess { recording ->
                preparedRecording = recording
                activeSession = null
                reviewDurationMs = recording.durationMs
                statusText = "Opname klaar, controleer en verstuur"
            }
            .onFailure { error ->
                activeSession = null
                statusText = error.message ?: "Opname stoppen mislukt"
            }
    }

    fun cancelCurrentRecording() {
        val session = activeSession
        if (session != null) {
            cancelRecording(session)
            activeSession = null
        }

        preparedRecording?.recordingUri?.let { uri ->
            runCatching {
                context.contentResolver.delete(android.net.Uri.parse(uri), null, null)
            }
        }
        preparedRecording = null
        statusText = "Opname verwijderd"
    }

    fun saveCurrentReview() {
        val transcript = transcriptSegments.joinToString("\n") { "${it.speaker}: ${it.text}" }
        if (transcript.isBlank()) {
            statusText = "Er is nog geen transcript om op te slaan"
            return
        }

        val sessionId = activeReviewSessionId ?: UUID.randomUUID().toString()
        settingsStore.saveSession(
            SavedTranscriptSession(
                id = sessionId,
                createdAtEpochMs = System.currentTimeMillis(),
                providerLabel = model.ifBlank { VoxtralWearClient.DefaultModel },
                transcript = transcript,
                durationMs = reviewDurationMs,
                recordingUri = preparedRecording?.recordingUri,
            ),
        )
        activeReviewSessionId = sessionId
        sessions = settingsStore.getSavedSessions()
        statusText = "Sessie opgeslagen"
    }

    fun exportCurrentReview() {
        val transcript = transcriptSegments.joinToString("\n") { "${it.speaker}: ${it.text}" }
        if (transcript.isBlank()) {
            statusText = "Geen transcript om te exporteren"
            return
        }

        runCatching {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Transcript", transcript))

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, transcript)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Exporteer transcript"))
        }.onFailure {
            statusText = "Exporteren mislukt"
            return
        }

        statusText = "Transcript gekopieerd en gedeeld"
    }

    val isProcessing = route == WearRoute.Processing
    val showBack = route !in setOf(WearRoute.Onboarding, WearRoute.Home, WearRoute.Processing)
    val providerConnected = apiKey.isNotBlank()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = spacing.md, vertical = spacing.sm),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showBack) {
                        TextButton(
                            onClick = {
                                route = WearRoute.Home
                                statusText = "Terug naar dashboard"
                            },
                            contentPadding = PaddingValues(horizontal = spacing.xs, vertical = spacing.xs),
                        ) {
                            Text("Terug")
                        }
                    }
                    Text(
                        text = when (route) {
                            WearRoute.Onboarding -> "Welkom"
                            WearRoute.Home -> "Ownkey Voice"
                            WearRoute.Recording -> "Nieuwe opname"
                            WearRoute.Processing -> "Verwerken"
                            WearRoute.Review -> "Transcript review"
                            WearRoute.Sessions -> "Sessies"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                        textAlign = if (showBack) TextAlign.End else TextAlign.Start,
                    )
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    ) { innerPadding ->
        when (route) {
            WearRoute.Onboarding -> {
                OnboardingScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    apiKey = apiKey,
                    endpointUrl = endpointUrl,
                    model = model,
                    languageHint = languageHint,
                    onApiKeyChange = { apiKey = it },
                    onEndpointChange = { endpointUrl = it },
                    onModelChange = { model = it },
                    onLanguageHintChange = { languageHint = it },
                    onContinue = {
                        persistProviderSettings()
                        route = WearRoute.Home
                        statusText = "Provider instellingen opgeslagen"
                    },
                )
            }

            WearRoute.Home -> {
                HomeScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    providerConnected = providerConnected,
                    providerLabel = model.ifBlank { VoxtralWearClient.DefaultModel },
                    sessionCount = sessions.size,
                    averageDurationLabel = formatDurationLabel(
                        if (sessions.isEmpty()) 0L else sessions.sumOf { it.durationMs } / sessions.size,
                    ),
                    lastSessionLabel = sessions.firstOrNull()?.createdAtEpochMs?.let(::formatShortDateTime) ?: "Nog geen sessies",
                    onPrimaryAction = {
                        route = WearRoute.Recording
                        startNewRecording()
                    },
                    onSessionsAction = { route = WearRoute.Sessions },
                    onProviderAction = {
                        route = WearRoute.Onboarding
                    },
                )
            }

            WearRoute.Recording -> {
                RecordingScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    isRecording = activeSession != null,
                    hasPreparedRecording = preparedRecording != null,
                    elapsedLabel = formatDurationLabel(elapsedRecordingMs),
                    onStart = { startNewRecording() },
                    onCancel = { cancelCurrentRecording() },
                    onStop = { stopActiveRecording() },
                    onSend = {
                        val recording = preparedRecording
                        if (recording != null) {
                            route = WearRoute.Processing
                            statusText = "Transcriptie bezig"
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    client.transcribe(
                                        apiKey = apiKey.trim(),
                                        endpointUrl = endpointUrl.trim().ifBlank { VoxtralWearClient.DefaultEndpointUrl },
                                        model = model.trim().ifBlank { VoxtralWearClient.DefaultModel },
                                        languageHint = languageHint.trim(),
                                        recording = recording,
                                    )
                                }

                                result
                                    .onSuccess { transcript ->
                                        transcriptSegments = createEditableSegments(transcript)
                                        if (transcriptSegments.isEmpty()) {
                                            transcriptSegments = listOf(
                                                EditableTranscriptSegment(
                                                    id = 0,
                                                    speaker = "Spreker A",
                                                    timestampLabel = "00:00",
                                                    text = transcript,
                                                ),
                                            )
                                        }
                                        reviewDurationMs = recording.durationMs
                                        activeReviewSessionId = null
                                        statusText = "Transcript klaar voor review"
                                        route = WearRoute.Review
                                    }
                                    .onFailure { error ->
                                        statusText = error.message ?: "Transcriptie mislukt"
                                        route = WearRoute.Recording
                                    }
                            }
                        } else {
                            statusText = "Stop eerst de opname"
                        }
                    },
                    onRetake = {
                        cancelCurrentRecording()
                        startNewRecording()
                    },
                )
            }

            WearRoute.Processing -> {
                ProcessingScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    modelLabel = model,
                    isBusy = isProcessing,
                )
            }

            WearRoute.Review -> {
                ReviewScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    segments = transcriptSegments,
                    onEditToggle = { segmentId ->
                        transcriptSegments = transcriptSegments.map {
                            if (it.id == segmentId) it.copy(isEditing = !it.isEditing) else it
                        }
                    },
                    onSegmentTextChange = { segmentId, newText ->
                        transcriptSegments = transcriptSegments.map {
                            if (it.id == segmentId) it.copy(text = newText) else it
                        }
                    },
                    onSave = { saveCurrentReview() },
                    onExport = { exportCurrentReview() },
                )
            }

            WearRoute.Sessions -> {
                SessionsScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    sessions = sessions,
                    onSessionOpen = { session ->
                        transcriptSegments = createEditableSegments(session.transcript)
                        activeReviewSessionId = session.id
                        preparedRecording = null
                        reviewDurationMs = session.durationMs
                        route = WearRoute.Review
                        statusText = "Sessie geladen"
                    },
                    onSessionDelete = { session ->
                        settingsStore.removeSession(session.id)
                        sessions = settingsStore.getSavedSessions()
                        statusText = "Sessie verwijderd"
                    },
                )
            }
        }
    }
}

@Composable
private fun OnboardingScreen(
    modifier: Modifier,
    apiKey: String,
    endpointUrl: String,
    model: String,
    languageHint: String,
    onApiKeyChange: (String) -> Unit,
    onEndpointChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onLanguageHintChange: (String) -> Unit,
    onContinue: () -> Unit,
) {
    val spacing = OwnkeyTokens.spacing

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.md, vertical = spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp),
        ) {
            Column(
                modifier = Modifier.padding(spacing.lg),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                Text(
                    text = "Rustige start",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Verbind je provider en ga daarna direct naar het dashboard.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            label = { Text("Mistral API key") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        OutlinedTextField(
            value = endpointUrl,
            onValueChange = onEndpointChange,
            label = { Text("Endpoint") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        OutlinedTextField(
            value = model,
            onValueChange = onModelChange,
            label = { Text("Model") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        OutlinedTextField(
            value = languageHint,
            onValueChange = onLanguageHintChange,
            label = { Text("Language hint") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            enabled = apiKey.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = AppPrimary),
        ) {
            Text("Opslaan en doorgaan")
        }
    }
}

@Composable
private fun HomeScreen(
    modifier: Modifier,
    providerConnected: Boolean,
    providerLabel: String,
    sessionCount: Int,
    averageDurationLabel: String,
    lastSessionLabel: String,
    onPrimaryAction: () -> Unit,
    onSessionsAction: () -> Unit,
    onProviderAction: () -> Unit,
) {
    val spacing = OwnkeyTokens.spacing

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.md, vertical = spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier.padding(spacing.lg),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                Text(
                    text = "Dashboard",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )

                ProviderStatusChip(
                    isConnected = providerConnected,
                    providerLabel = providerLabel,
                )

                Button(
                    onClick = onPrimaryAction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppPrimary),
                ) {
                    Text("Start nieuwe opname")
                }

                OutlinedButton(
                    onClick = onSessionsAction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                ) {
                    Text("Open sessies")
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            MetricChip(label = "Sessies", value = sessionCount.toString())
            MetricChip(label = "Gem. duur", value = averageDurationLabel)
            MetricChip(label = "Laatste", value = lastSessionLabel)
        }

        TextButton(onClick = onProviderAction, modifier = Modifier.fillMaxWidth()) {
            Text("Provider instellingen aanpassen")
        }
    }
}

@Composable
private fun RecordingScreen(
    modifier: Modifier,
    isRecording: Boolean,
    hasPreparedRecording: Boolean,
    elapsedLabel: String,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onStop: () -> Unit,
    onSend: () -> Unit,
    onRetake: () -> Unit,
) {
    val spacing = OwnkeyTokens.spacing

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.md, vertical = spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                Text(
                    text = if (isRecording) "Microfoon actief" else if (hasPreparedRecording) "Klaar om te versturen" else "Microfoon klaar",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = elapsedLabel,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )

                WaveformBars(
                    isActive = isRecording,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp),
                )

                Box(
                    modifier = Modifier
                        .size(94.dp)
                        .clip(CircleShape)
                        .background(
                            if (isRecording) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.primaryContainer,
                        )
                        .clickable(enabled = !hasPreparedRecording || isRecording) {
                            if (isRecording) onStop() else onStart()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (isRecording) "Stop" else "Start",
                        color = if (isRecording) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }

        if (isRecording) {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                ) {
                    Text("Annuleer")
                }
                Button(
                    onClick = onStop,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                ) {
                    Text("Stop")
                }
            }
        } else if (hasPreparedRecording) {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                OutlinedButton(
                    onClick = onRetake,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                ) {
                    Text("Opnieuw")
                }
                Button(
                    onClick = onSend,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppPrimary),
                ) {
                    Text("Verstuur")
                }
            }
            TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                Text("Verwijder opname")
            }
        } else {
            Button(
                onClick = onStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppPrimary),
            ) {
                Text("Start opname")
            }
        }
    }
}

@Composable
private fun ProcessingScreen(
    modifier: Modifier,
    modelLabel: String,
    isBusy: Boolean,
) {
    val spacing = OwnkeyTokens.spacing

    Column(
        modifier = modifier
            .padding(horizontal = spacing.md, vertical = spacing.sm),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Card(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier.padding(spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 4.dp,
                    )
                }
                Text(
                    text = "Transcript wordt verwerkt",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = modelLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun ReviewScreen(
    modifier: Modifier,
    segments: List<EditableTranscriptSegment>,
    onEditToggle: (Int) -> Unit,
    onSegmentTextChange: (Int, String) -> Unit,
    onSave: () -> Unit,
    onExport: () -> Unit,
) {
    val spacing = OwnkeyTokens.spacing

    Column(
        modifier = modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Button(
                onClick = onSave,
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppPrimary),
            ) {
                Text("Opslaan")
            }
            OutlinedButton(
                onClick = onExport,
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
            ) {
                Text("Export")
            }
        }

        if (segments.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
            ) {
                Text(
                    text = "Nog geen transcript beschikbaar.",
                    modifier = Modifier.padding(spacing.lg),
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
                contentPadding = PaddingValues(bottom = spacing.xl),
            ) {
                items(segments, key = { it.id }) { segment ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (segment.id % 2 == 0) Arrangement.Start else Arrangement.End,
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(0.92f),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (segment.id % 2 == 0) MaterialTheme.colorScheme.surface
                                else AppSurfaceStrong,
                            ),
                        ) {
                            Column(
                                modifier = Modifier.padding(spacing.md),
                                verticalArrangement = Arrangement.spacedBy(spacing.xs),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "${segment.speaker} · ${segment.timestampLabel}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    TextButton(
                                        onClick = { onEditToggle(segment.id) },
                                        contentPadding = PaddingValues(horizontal = spacing.xs),
                                    ) {
                                        Text(if (segment.isEditing) "Klaar" else "Bewerk")
                                    }
                                }

                                if (segment.isEditing) {
                                    OutlinedTextField(
                                        value = segment.text,
                                        onValueChange = { onSegmentTextChange(segment.id, it) },
                                        modifier = Modifier.fillMaxWidth(),
                                        textStyle = MaterialTheme.typography.bodyLarge,
                                    )
                                } else {
                                    Text(
                                        text = segment.text,
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionsScreen(
    modifier: Modifier,
    sessions: List<SavedTranscriptSession>,
    onSessionOpen: (SavedTranscriptSession) -> Unit,
    onSessionDelete: (SavedTranscriptSession) -> Unit,
) {
    val spacing = OwnkeyTokens.spacing

    if (sessions.isEmpty()) {
        Column(
            modifier = modifier
                .padding(horizontal = spacing.md, vertical = spacing.sm)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Nog geen sessies opgeslagen.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
        contentPadding = PaddingValues(bottom = spacing.xl),
    ) {
        items(sessions, key = { it.id }) { session ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSessionOpen(session) },
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    modifier = Modifier.padding(spacing.md),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    Text(
                        text = formatShortDateTime(session.createdAtEpochMs),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = session.transcript.lines().firstOrNull().orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Duur ${formatDurationLabel(session.durationMs)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = { onSessionDelete(session) }) {
                            Text("Verwijder")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderStatusChip(
    isConnected: Boolean,
    providerLabel: String,
) {
    val chipColor = if (isConnected) Color(0xFFDCEEE4) else Color(0xFFF1E3D8)
    val textColor = if (isConnected) Color(0xFF214E37) else Color(0xFF6C4528)

    Surface(
        color = chipColor,
        shape = RoundedCornerShape(999.dp),
    ) {
        Text(
            text = if (isConnected) "Provider actief · $providerLabel" else "Provider niet ingesteld",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
        )
    }
}

@Composable
private fun MetricChip(
    label: String,
    value: String,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun WaveformBars(
    isActive: Boolean,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val animatedScale by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 550),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "wave-scale",
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.Bottom,
    ) {
        repeat(18) { index ->
            val base = 10 + (index % 5) * 4
            val height = if (isActive) (base * animatedScale).dp else 10.dp
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(height)
                    .clip(RoundedCornerShape(12.dp))
                    .alpha(if (isActive) 0.95f else 0.5f)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

private fun createEditableSegments(transcript: String): List<EditableTranscriptSegment> {
    val rawParts = transcript
        .split('\n')
        .flatMap { line ->
            line.split(Regex("(?<=[.!?])\\s+"))
        }
        .map { it.trim() }
        .filter { it.isNotBlank() }

    if (rawParts.isEmpty()) return emptyList()

    return rawParts.mapIndexed { index, part ->
        val parsedSpeaker = part.substringBefore(':', missingDelimiterValue = "")
        val hasSpeaker = parsedSpeaker.isNotBlank() && part.contains(':') && parsedSpeaker.length < 18
        val speaker = if (hasSpeaker) parsedSpeaker else if (index % 2 == 0) "Spreker A" else "Spreker B"
        val text = if (hasSpeaker) part.substringAfter(':').trim() else part

        EditableTranscriptSegment(
            id = index,
            speaker = speaker,
            timestampLabel = formatDurationLabel(index * 12_000L),
            text = text,
        )
    }
}

private fun formatDurationLabel(durationMs: Long): String {
    val safe = durationMs.coerceAtLeast(0L)
    val totalSeconds = safe / 1_000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}

private fun formatShortDateTime(epochMs: Long): String {
    val formatter = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())
    return formatter.format(Date(epochMs))
}
