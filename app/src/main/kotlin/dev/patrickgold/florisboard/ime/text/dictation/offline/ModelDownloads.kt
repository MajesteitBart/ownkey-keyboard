package dev.patrickgold.florisboard.ime.text.dictation.offline

import android.app.*
import android.app.job.*
import android.content.*
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PersistableBundle
import androidx.work.*
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisAppActivity
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import org.ownkey.offline.*

object ModelDownloads {
    const val JOB_ID = 7311
    const val NOTIFICATION_ID = 7312
    private const val WORK = "orukeet-model-download"
    private const val CHANNEL = "orukeet-downloads"
    private fun record(context: Context) = File(context.noBackupFilesDir, "ai-models/transfer-request")

    /** Called from the visible settings Download/Retry action, never from keyboard startup. */
    suspend fun schedule(context: Context, allowMobileData: Boolean) = withContext(Dispatchers.Main.immediate) {
        val app = context.applicationContext
        if (!app.offlineDictation().compatible) throw LocalAsrException(LocalAsrFailure.UNSUPPORTED)
        cancel(app)
        val token = UUID.randomUUID().toString()
        try {
            withContext(Dispatchers.IO) { ModelStore.atomicWrite(record(app), "${ModelCatalog.current.id}:$token") }
            app.offlineDictation().waitingForNetwork(allowMobileData)
            if (Build.VERSION.SDK_INT >= 34) {
                val job = createJob(app, token, allowMobileData)
                if (app.getSystemService(JobScheduler::class.java).schedule(job) != JobScheduler.RESULT_SUCCESS) {
                    throw LocalAsrException(LocalAsrFailure.DOWNLOAD)
                }
            } else {
                val constraints = Constraints.Builder().setRequiredNetworkRequest(
                    ModelDownloadNetwork.request(allowMobileData), NetworkType.CONNECTED,
                ).build()
                val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>().setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
                    .setInputData(workDataOf("token" to token, "wifiOnly" to !allowMobileData)).build()
                WorkManager.getInstance(app).enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE, request)
            }
        } catch (cancelled: CancellationException) {
            cancel(app)
            throw cancelled
        } catch (failure: Exception) {
            cancel(app)
            app.offlineDictation().downloadFailed()
            throw failure
        }
    }

    @androidx.annotation.RequiresApi(34)
    fun createJob(context: Context, token: String, allowMobileData: Boolean): JobInfo =
        JobInfo.Builder(JOB_ID, ComponentName(context, ModelDownloadJob::class.java))
            .setUserInitiated(true).setRequiredNetwork(ModelDownloadNetwork.request(allowMobileData))
            .setEstimatedNetworkBytes(ModelCatalog.current.bytes, 0)
            // Partial files survive interruption; Android need not fit all 672 MB into one run.
            .setMinimumNetworkChunkBytes(ModelDownloadNetwork.RESUMABLE_CHUNK_BYTES)
            .setExtras(PersistableBundle().apply { putString("token", token); putBoolean("allowMobileData", allowMobileData) })
            .build()

    @Synchronized
    fun authorized(context: Context, token: String?): Boolean = token != null &&
        runCatching { record(context).readText() == "${ModelCatalog.current.id}:$token" }.getOrDefault(false)
    @Synchronized
    fun finished(context: Context, token: String?) { if (authorized(context, token)) record(context).delete() }
    @Synchronized
    fun waitingIfAuthorized(context: Context, token: String?, allowMobileData: Boolean): Boolean {
        if (!authorized(context, token)) return false
        context.offlineDictation().waitingForNetwork(allowMobileData)
        return true
    }
    @Synchronized
    fun failedIfAuthorized(context: Context, token: String?) {
        if (!authorized(context, token)) return
        if (context.offlineDictation().state.value.error == null) context.offlineDictation().downloadFailed()
        finished(context, token)
    }
    @Synchronized
    fun cancel(context: Context) {
        record(context).delete()
        context.getSystemService(JobScheduler::class.java).cancel(JOB_ID)
        if (Build.VERSION.SDK_INT < 34) WorkManager.getInstance(context).cancelUniqueWork(WORK)
        context.offlineDictation().cancelDownload()
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    fun notification(context: Context, progress: DownloadProgress? = null): Notification {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.orukeet__download_channel), NotificationManager.IMPORTANCE_LOW))
        val cancel = PendingIntent.getBroadcast(context, 0, Intent(context, CancelModelDownloadReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(context, 0, Intent(Intent.ACTION_VIEW, android.net.Uri.parse("ui://florisboard/settings/voxtral"), context, FlorisAppActivity::class.java).addCategory(Intent.CATEGORY_BROWSABLE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val percent = progress?.let { (it.completed * 100 / it.total.coerceAtLeast(1)).toInt() } ?: 0
        return Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(context.getString(R.string.orukeet__title))
            .setContentText(context.getString(if (progress?.verifying == true) R.string.orukeet__verifying else R.string.orukeet__downloading))
            .setContentIntent(open).setOnlyAlertOnce(true).setOngoing(true)
            .setProgress(100, percent, progress == null)
            .addAction(Notification.Action.Builder(null, context.getString(R.string.action__cancel), cancel).build())
            .build()
    }
}

class ModelDownloadJob : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var runningParams: JobParameters? = null
    override fun onStartJob(params: JobParameters): Boolean {
        if (Build.VERSION.SDK_INT < 34 || !ModelDownloads.authorized(this, params.extras.getString("token"))) return false
        setNotification(params, ModelDownloads.NOTIFICATION_ID, ModelDownloads.notification(this), JOB_END_NOTIFICATION_POLICY_REMOVE)
        runningParams = params
        job = scope.launch {
            try {
                val network = params.network ?: throw LocalAsrException(LocalAsrFailure.DOWNLOAD)
                offlineDictation().download(open = { network.openConnection(it) as java.net.HttpURLConnection }) { progress ->
                    if (Build.VERSION.SDK_INT >= 34) setNotification(params, ModelDownloads.NOTIFICATION_ID,
                        ModelDownloads.notification(this@ModelDownloadJob, progress), JOB_END_NOTIFICATION_POLICY_REMOVE)
                }
                ModelDownloads.finished(this@ModelDownloadJob, params.extras.getString("token"))
            } catch (_: CancellationException) { return@launch
            } catch (_: Exception) {
                ModelDownloads.failedIfAuthorized(this@ModelDownloadJob, params.extras.getString("token"))
            } finally { withContext(NonCancellable + Dispatchers.Main) {
                // onStopJob owns retry after the scheduler revokes this run.
                if (runningParams === params) {
                    runningParams = null; job = null
                    jobFinished(params, false)
                } else if (ModelDownloads.authorized(this@ModelDownloadJob, params.extras.getString("token")) &&
                    offlineDictation().state.value.transferPhase == null) {
                    ModelDownloads.waitingIfAuthorized(this@ModelDownloadJob, params.extras.getString("token"), params.extras.getBoolean("allowMobileData"))
                }
            } }
        }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean {
        runningParams = null
        job?.cancel(); job = null
        return ModelDownloads.authorized(this, params.extras.getString("token"))
    }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}

class ModelDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val token = inputData.getString("token")
        if (!ModelDownloads.authorized(applicationContext, token)) return Result.failure()
        val connectivity = applicationContext.getSystemService(android.net.ConnectivityManager::class.java)
        val network = connectivity.activeNetwork ?: return Result.retry()
        val wifiOnly = inputData.getBoolean("wifiOnly", true)
        fun allowed(): Boolean {
            val caps = connectivity.getNetworkCapabilities(network) ?: return false
            return ModelDownloadNetwork.allows(caps, allowMobileData = !wifiOnly)
        }
        if (!allowed()) return Result.retry()
        return try {
            setForeground(foreground())
            applicationContext.offlineDictation().download(open = {
                if (!allowed()) throw java.io.IOException("Required network unavailable")
                network.openConnection(it) as java.net.HttpURLConnection
            }) { progress ->
                applicationContext.getSystemService(NotificationManager::class.java)
                    .notify(ModelDownloads.NOTIFICATION_ID, ModelDownloads.notification(applicationContext, progress))
            }
            ModelDownloads.finished(applicationContext, token)
            Result.success()
        } catch (cancel: CancellationException) { throw cancel
        } catch (error: Exception) {
            val transient = error is java.io.IOException
            if (transient && runAttemptCount < 3 &&
                ModelDownloads.waitingIfAuthorized(applicationContext, token, allowMobileData = !wifiOnly)) {
                return Result.retry()
            }
            ModelDownloads.failedIfAuthorized(applicationContext, token)
            Result.failure()
        }
    }
    override suspend fun getForegroundInfo() = foreground()
    private fun foreground(): ForegroundInfo {
        val notification = ModelDownloads.notification(applicationContext)
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(ModelDownloads.NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else ForegroundInfo(ModelDownloads.NOTIFICATION_ID, notification)
    }
}

class CancelModelDownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = ModelDownloads.cancel(context)
}
