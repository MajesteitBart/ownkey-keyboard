/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.ime.clipboard

import android.content.ClipData
import android.content.Context
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.appContext
import dev.patrickgold.florisboard.editorInstance
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardHistoryDao
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardHistoryDatabase
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardItem
import dev.patrickgold.florisboard.ime.clipboard.provider.ItemType
import java.io.Closeable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.florisboard.lib.android.AndroidClipboardManager
import org.florisboard.lib.android.AndroidClipboardManager_OnPrimaryClipChangedListener
import org.florisboard.lib.android.clearPrimaryClipAnyApi
import org.florisboard.lib.android.setOrClearPrimaryClip
import org.florisboard.lib.android.showShortToastSync
import org.florisboard.lib.android.systemService
import org.florisboard.lib.kotlin.tryOrNull

/**
 * [ClipboardManager] manages the clipboard and clipboard history.
 *
 * Also just going to document how all the classes here work.
 *
 * [ClipboardManager] handles storage and retrieval of clipboard items. All manipulation of the
 * clipboard goes through here.
 */
class ClipboardManager(
    context: Context,
) : AndroidClipboardManager_OnPrimaryClipChangedListener, Closeable {
    companion object {
        /**
         * Taken from ClipboardDescription.java from the AOSP
         *
         * Helper to compare two MIME types, where one may be a pattern.
         * @param concreteType A fully-specified MIME type.
         * @param desiredType A desired MIME type that may be a pattern such as * / *.
         * @return Returns true if the two MIME types match.
         */
        fun compareMimeTypes(concreteType: String, desiredType: String): Boolean {
            val typeLength = desiredType.length
            if (typeLength == 3 && desiredType == "*/*") {
                return true
            }
            val slashpos = desiredType.indexOf('/')
            if (slashpos > 0) {
                if (typeLength == slashpos + 2 && desiredType[slashpos + 1] == '*') {
                    if (desiredType.regionMatches(0, concreteType, 0, slashpos + 1)) {
                        return true
                    }
                } else if (desiredType == concreteType) {
                    return true
                }
            }
            return false
        }
    }

    private val prefs by FlorisPreferenceStore
    private val appContext by context.appContext()
    private val editorInstance by context.editorInstance()
    private val systemClipboardManager = context.systemService(AndroidClipboardManager::class)

    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val cleanUpJob: Job
    private var clipHistoryDb: ClipboardHistoryDatabase? = null
    private val clipHistoryDao: ClipboardHistoryDao? get() = clipHistoryDb?.clipboardItemDao()

    val historyFlow: StateFlow<ClipboardHistory>
        field = MutableStateFlow(ClipboardHistory.EMPTY)
    val currentHistory: ClipboardHistory
        get() = historyFlow.value

    private val primaryClipLastFromCallbackGuard = Mutex(locked = false)
    private var primaryClipLastFromCallback: ClipData? = null
    val primaryClipFlow: StateFlow<ClipboardItem?>
        field = MutableStateFlow(null)
    inline var primaryClip
        get() = primaryClipFlow.value
        private set(v) {
            primaryClipFlow.value = v
        }

    init {
        systemClipboardManager.addPrimaryClipChangedListener(this)
        cleanUpJob = ioScope.launch {
            combine(
                historyFlow,
                prefs.clipboard.historyAutoCleanOldEnabled.asFlow(),
                prefs.clipboard.historyAutoCleanOldAfter.asFlow(),
                prefs.clipboard.historyAutoCleanSensitiveEnabled.asFlow(),
                prefs.clipboard.historyAutoCleanSensitiveAfter.asFlow(),
            ) { history, oldEnabled, oldAfterMinutes, sensitiveEnabled, sensitiveAfterSeconds ->
                ClipboardCleanupPlan(
                    history = history,
                    policy = ClipboardCleanupPolicy(
                        oldItemsEnabled = oldEnabled,
                        oldItemsAfterMinutes = oldAfterMinutes,
                        sensitiveItemsEnabled = sensitiveEnabled,
                        sensitiveItemsAfterSeconds = sensitiveAfterSeconds,
                    ),
                )
            }.collectLatest { plan ->
                while (true) {
                    val delayMs = ClipboardCleanupScheduler.nextDelayMs(
                        items = plan.history.all.map { it.toCleanupItem() },
                        policy = plan.policy,
                        nowMs = System.currentTimeMillis(),
                    ) ?: return@collectLatest
                    delay(delayMs)
                    val removedItems = try {
                        enforceExpiryDate(plan)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        delay(ClipboardCleanupScheduler.RetryDelayMs)
                        continue
                    }
                    if (removedItems) return@collectLatest
                    if (delayMs == 0L) delay(ClipboardCleanupScheduler.RetryDelayMs)
                }
            }
        }
    }

    fun initializeForContext(context: Context) {
        ioScope.launch {
            if (clipHistoryDb == null) {
                clipHistoryDb = ClipboardHistoryDatabase.new(context.applicationContext)
                withContext(Dispatchers.Main) {
                    clipHistoryDao?.getAllAsFlow()?.collect { items ->
                        updateHistory(items)
                    }
                }
            }
        }
    }

    private fun updateHistory(items: List<ClipboardItem>) {
        val itemsSorted = items.sortedByDescending { it.creationTimestampMs }
        val clipHistory = ClipboardHistory(itemsSorted)
        enforceHistoryLimit(clipHistory)
        historyFlow.value = clipHistory
    }

    /**
     * Sets the current primary clip without updating the internal clipboard history.
     */
    fun updatePrimaryClip(item: ClipboardItem?) {
        primaryClip = item
        if (prefs.clipboard.useInternalClipboard.get()) {
            val syncBehavior = prefs.clipboard.syncToSystem.get()
            val clipData = item?.toClipData(appContext)
            if (clipData != null && syncBehavior.shouldSyncSet) {
                systemClipboardManager.setPrimaryClip(clipData)
            } else if (clipData == null && syncBehavior.shouldSyncClear) {
                systemClipboardManager.clearPrimaryClipAnyApi()
            }
        } else {
            systemClipboardManager.setOrClearPrimaryClip(item?.toClipData(appContext))
        }
    }

    /**
     * Called by system clipboard when the system primary clip has changed.
     */
    override fun onPrimaryClipChanged() {
        val syncBehavior = prefs.clipboard.syncToFloris.get()
        if (!prefs.clipboard.useInternalClipboard.get() || syncBehavior != ClipboardSyncBehavior.NO_EVENTS) {
            val systemPrimaryClip = systemClipboardManager.primaryClip
            ioScope.launch {
                val isDuplicate: Boolean
                primaryClipLastFromCallbackGuard.withLock {
                    val a = primaryClipLastFromCallback?.getItemAt(0)
                    val b = systemPrimaryClip?.getItemAt(0)
                    isDuplicate = when {
                        a === b -> true
                        a == null || b == null -> false
                        else -> a.text == b.text && a.uri == b.uri
                    }
                    primaryClipLastFromCallback = systemPrimaryClip
                }
                if (isDuplicate) return@launch

                val internalPrimaryClip = primaryClip

                if (systemPrimaryClip == null) {
                    if (syncBehavior.shouldSyncClear) {
                        primaryClip = null
                    }
                    return@launch
                }

                if (systemPrimaryClip.getItemAt(0).let { it.text == null && it.uri == null }) {
                    if (syncBehavior.shouldSyncClear) {
                        primaryClip = null
                    }
                    return@launch
                }

                if (!syncBehavior.shouldSyncSet) {
                    return@launch
                }

                val isEqual = internalPrimaryClip?.isEqualTo(systemPrimaryClip) == true
                if (!isEqual) {
                    val item = ClipboardItem.fromClipData(appContext, systemPrimaryClip, cloneUri = true)
                    primaryClip = item
                    insertOrMoveBeginning(item)
                }
            }
        }
    }

    /**
     * Change the current text on clipboard, update history (if enabled).
     */
    private fun addNewClip(item: ClipboardItem) {
        insertOrMoveBeginning(item)
        updatePrimaryClip(item)
    }

    /**
     * Wraps some plaintext in a ClipData and calls [addNewClip]
     */
    fun addNewPlaintext(newText: String) {
        val newData = ClipboardItem.text(newText)
        addNewClip(newData)
    }

    /**
     * Adds a new item to the clipboard history (if enabled).
     */
    private fun insertOrMoveBeginning(newItem: ClipboardItem) {
        if (prefs.clipboard.historyEnabled.get()) {
            val historyElement = currentHistory.all.firstOrNull { item ->
                item.type == ItemType.TEXT && item.text == newItem.text && item.isSensitive == newItem.isSensitive
            }
            if (historyElement != null) {
                moveToTheBeginning(
                    oldItem = historyElement,
                    newItem = if (historyElement.isPinned) {
                        newItem.copy(isPinned = true)
                    } else {
                        newItem
                    }
                )
            } else {
                insertClip(newItem)
            }
        }
    }

    private fun enforceHistoryLimit(clipHistory: ClipboardHistory) {
        if (prefs.clipboard.historySizeLimitEnabled.get()) {
            val nonPinnedItems = clipHistory.recent + clipHistory.other
            val nToRemove = nonPinnedItems.size - prefs.clipboard.historySizeLimit.get()
            if (nToRemove > 0) {
                val itemsToRemove = nonPinnedItems.asReversed().filterIndexed { n, _ -> n < nToRemove }
                ioScope.launch {
                    clipHistoryDao?.delete(itemsToRemove)
                }
            }
        }
    }

    private suspend fun enforceExpiryDate(plan: ClipboardCleanupPlan): Boolean {
        val nowMs = System.currentTimeMillis()
        val itemsToRemove = plan.history.all.filter { item ->
            ClipboardCleanupScheduler.isExpired(
                item = item.toCleanupItem(),
                policy = plan.policy,
                nowMs = nowMs,
            )
        }
        if (itemsToRemove.isEmpty()) return false
        val dao = clipHistoryDao ?: return false
        dao.delete(itemsToRemove)
        return true
    }

    private fun ClipboardItem.toCleanupItem() = ClipboardCleanupItem(
        creationTimestampMs = creationTimestampMs,
        isPinned = isPinned,
        isSensitive = isSensitive,
    )

    private fun moveToTheBeginning(oldItem: ClipboardItem, newItem: ClipboardItem) {
        ioScope.launch {
            clipHistoryDao?.delete(oldItem.id)
            clipHistoryDao?.insert(newItem)
        }
    }

    fun insertClip(item: ClipboardItem) {
        ioScope.launch {
            val id = clipHistoryDao?.insert(item)
            item.id = id ?: 0
        }
    }

    fun clearExactHistory(items: List<ClipboardItem>) {
        ioScope.launch {
            for (item in items) {
                item.close(appContext)
            }
            clipHistoryDao?.delete(items)
        }
    }

    /**
     * Clears all unpinned items from the clipboard history
     */
    fun clearHistory() {
        ioScope.launch {
            for (item in currentHistory.all) {
                item.close(appContext)
            }
            clipHistoryDao?.deleteAllUnpinned()
        }
    }

    /**
     * Clears the full clipboard history
     */
    fun clearFullHistory() {
        ioScope.launch {
            for (item in currentHistory.all) {
                item.close(appContext)
            }
            clipHistoryDao?.deleteAll()
        }
    }


    /**
     * Restore the clipboard history from a [List]
     *
     * @param items the [ClipboardItem] list with the new items
     */
    fun restoreHistory(items: List<ClipboardItem>) {
        ioScope.launch {
            val currentHistory = currentHistory.all
            for (item in items) {
                if (!currentHistory.map { it.copy(id = 0) }.contains(item.copy(id = 0))) {
                    insertClip(item.copy(id = 0))
                }
            }
        }
    }

    fun deleteClip(item: ClipboardItem, onlyIfUnpinned: Boolean) {
        ioScope.launch {
            if (onlyIfUnpinned) {
                clipHistoryDao?.deleteIfUnpinned(item.id)
            } else {
                clipHistoryDao?.delete(item.id)
            }
            tryOrNull {
                val uri = item.uri
                if (uri != null) {
                    appContext.contentResolver.delete(uri, null, null)
                }
            }
        }
    }

    fun pinClip(item: ClipboardItem) {
        ioScope.launch {
            clipHistoryDao?.update(item.copy(isPinned = true))
        }
    }

    fun unpinClip(item: ClipboardItem) {
        ioScope.launch {
            clipHistoryDao?.update(item.copy(isPinned = false))
        }
    }

    fun pasteItem(item: ClipboardItem) {
        val editorInstance by appContext.editorInstance()
        editorInstance.commitClipboardItem(item).also { result ->
            if (!result) {
                appContext.showShortToastSync("Failed to paste item.")
            }
        }
    }

    /**
     * Returns true if the editor can accept the clip item, else false.
     */
    fun canBePasted(clipItem: ClipboardItem?): Boolean {
        if (clipItem == null) return false

        return clipItem.mimeTypes.contains("text/plain") || editorInstance.activeInfo.contentMimeTypes.any { editorType ->
            clipItem.mimeTypes.any { clipType ->
                compareMimeTypes(clipType, editorType)
            }
        }
    }

    /**
     * Cleans up.
     *
     * Unregisters the system clipboard listener, cancels clipboard clean ups.
     */
    override fun close() {
        systemClipboardManager.removePrimaryClipChangedListener(this)
        cleanUpJob.cancel()
    }
}
