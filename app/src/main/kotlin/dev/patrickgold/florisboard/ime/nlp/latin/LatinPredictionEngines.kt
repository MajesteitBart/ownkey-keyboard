/*
 * Copyright (C) 2026 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.ime.nlp.latin

import android.content.res.AssetManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.florisboard.lib.prediction.EngineConfig
import org.florisboard.lib.prediction.Lexicon
import org.florisboard.lib.prediction.PersonalOverlay
import org.florisboard.lib.prediction.PredictionEngine
import org.florisboard.lib.prediction.SpatialModel
import org.florisboard.lib.prediction.WordLanguageModel
import org.florisboard.lib.prediction.ngram.NgramWordModel
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * Loads the n-gram models and builds one [PredictionEngine] per ordered language list (primary first), off the
 * caller's thread.
 *
 * Every language is loaded once ([loadModel], typically [loadNgramModel] on a memory-mapped APK asset, see
 * [mapAsset]) and sorted into a [Lexicon] once; all engines share them ([PredictionEngine.fromLexicons]), so `nl, en`
 * and `en, nl` cost no extra memory. A mapped model reads the uncompressed asset in place, so its file stays off the
 * Java heap; the lexicons keep their models, and so the mappings, alive as long as this object. Loading runs in
 * [scope], off the main thread and one language at a time in request order (the primary language of the first
 * request first), so a start-up that falls back to reading a model into the heap (only within
 * [heapCopyBudget]) allocates one such copy at a time:
 * [engineIfReady] never waits and returns null until the engine is built, [awaitEngine] suspends. A language whose
 * model fails to load, also with an [Error] such as [OutOfMemoryError], stays failed (a broken asset does not heal);
 * callers then use the fallback, and the failure is never rethrown to them.
 *
 * [derive] adds the per-request keyboard geometry, configuration and personal overlay to a base engine, reusing
 * recent combinations. Thread-safe.
 */
internal class LatinPredictionEngines(
    private val scope: CoroutineScope,
    private val loadModel: (language: String) -> WordLanguageModel,
) {
    private val lock = Any()
    // The most recently requested model load; the next one waits for it (see the class documentation).
    private var lastLoad: Deferred<*>? = null
    private val lexicons = HashMap<String, Deferred<Lexicon>>()
    private val engines = HashMap<List<String>, Deferred<PredictionEngine>>()
    private val derived = object : LinkedHashMap<DerivedKey, PredictionEngine>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<DerivedKey, PredictionEngine>?): Boolean {
            return size > MaxDerivedEngines
        }
    }

    private data class DerivedKey(
        val base: PredictionEngine,
        val spatial: SpatialModel?,
        val config: EngineConfig,
        val overlay: PersonalOverlay?,
    )

    /** Starts loading the engine for [languages] unless it is loaded or loading; returns its deferred result. */
    fun prepare(languages: List<String>): Deferred<PredictionEngine> = synchronized(lock) {
        engines.getOrPut(languages) {
            val parts = languages.map { lexiconLocked(it) }
            scope.async { PredictionEngine.fromLexicons(parts.map { it.await() }) }
        }
    }

    /** The engine for [languages] if it is built, else null (and loading starts). Never waits. */
    fun engineIfReady(languages: List<String>): PredictionEngine? = prepare(languages).completedOrNull()

    /** True when the engine for [languages] can never be built (a model failed to load). */
    fun hasFailed(languages: List<String>): Boolean {
        val deferred = synchronized(lock) { engines[languages] } ?: return false
        return deferred.isCompleted && deferred.failure() != null
    }

    /** The engine for [languages], loading it if needed; null if a model failed to load. */
    suspend fun awaitEngine(languages: List<String>): PredictionEngine? = awaitOrNull(prepare(languages))

    /** The loaded model of [language], or null while it loads, if it failed or if nothing requested it yet. */
    fun modelIfReady(language: String): WordLanguageModel? {
        val deferred = synchronized(lock) { lexicons[language] } ?: return null
        return deferred.completedOrNull()?.model
    }

    /** The model of [language], loading it if needed; null if it failed to load. */
    suspend fun awaitModel(language: String): WordLanguageModel? {
        val deferred = synchronized(lock) { lexiconLocked(language) }
        return awaitOrNull(deferred)?.model
    }

    /** [base] with [spatial] (null = QWERTY), [config] and [overlay]; cheap, and recent combinations are reused. */
    fun derive(base: PredictionEngine, spatial: SpatialModel?, config: EngineConfig, overlay: PersonalOverlay?): PredictionEngine {
        val key = DerivedKey(base, spatial, config, overlay)
        synchronized(lock) { derived[key]?.let { return it } }
        var engine = base
        if (spatial != null) engine = engine.withGeometry(spatial)
        if (config != engine.config) engine = engine.withConfig(config)
        if (overlay != null) engine = engine.withPersonalOverlay(overlay)
        synchronized(lock) { derived[key] = engine }
        return engine
    }

    private fun lexiconLocked(language: String): Deferred<Lexicon> = lexicons.getOrPut(language) {
        val previous = lastLoad
        scope.async {
            // One model at a time, in request order; join() also returns when the previous load failed.
            previous?.join()
            Lexicon.build(loadModel(language))
        }.also { lastLoad = it }
    }

    /**
     * The result of [deferred], or null if it failed. Only the caller's own cancellation is rethrown: a load that
     * failed with an [Error] (out of memory while reading a model) must not crash the keyboard's callers.
     */
    private suspend fun <T> awaitOrNull(deferred: Deferred<T>): T? {
        return try {
            deferred.await()
        } catch (_: CancellationException) {
            // Rethrows if the caller was cancelled; otherwise the load itself was cancelled.
            currentCoroutineContext().ensureActive()
            null
        } catch (_: Throwable) {
            null
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> Deferred<T>.completedOrNull(): T? {
        if (!isCompleted || failure() != null) return null
        return getCompleted()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun Deferred<*>.failure(): Throwable? = if (isCompleted) getCompletionExceptionOrNull() else null

    companion object {
        private const val MaxDerivedEngines = 6

        /**
         * Share of the heap limit ([Runtime.maxMemory]) that must stay free after a model is copied into the heap. On
         * a phone with a 256 MB limit and the keyboard's usual 80 MB, this admits one large model (nl, 38.6 MB) and
         * refuses the second, so a packaging regression costs prediction, not an out-of-memory crash.
         */
        const val HeapCopyReserveFraction = 0.5

        /**
         * Bytes a heap copy of a model may take now: the free heap up to [Runtime.maxMemory] minus
         * [HeapCopyReserveFraction] of that limit (never negative).
         */
        fun heapCopyBudget(runtime: Runtime = Runtime.getRuntime()): Long {
            val max = runtime.maxMemory()
            val used = runtime.totalMemory() - runtime.freeMemory()
            return (max - used - (max * HeapCopyReserveFraction).toLong()).coerceAtLeast(0L)
        }

        /**
         * The n-gram model of [language], read in place from the buffer [map] returns (zero copy: a read-only mapping
         * of an uncompressed asset, see [mapAsset]), or from [open] (one heap copy of the file) when mapping throws,
         * for example because the asset is stored compressed; [onMapFailed] gets that exception. The heap copy is only
         * made when the file fits in [heapBudget] (default [heapCopyBudget]); otherwise the load fails with an
         * [IllegalStateException] after reading the file header, and the caller falls back to the frequency lists. A
         * buffer that maps but holds no valid model fails without the fallback: the stream would hold the same bytes.
         */
        fun loadNgramModel(
            language: String,
            map: () -> ByteBuffer,
            open: () -> InputStream,
            onMapFailed: (Exception) -> Unit = {},
            heapBudget: () -> Long = { heapCopyBudget() },
        ): NgramWordModel {
            val mapped = try {
                map()
            } catch (e: Exception) {
                onMapFailed(e)
                null
            }
            if (mapped != null) return NgramWordModel.load(mapped, language)
            val budget = heapBudget()
            return open().use { NgramWordModel.load(it, language, maxBytes = budget) }
        }

        /**
         * Maps the asset at [path] read-only. Works for assets stored uncompressed in the APK (`androidResources
         * .noCompress` in app/build.gradle.kts); a compressed asset cannot be opened as a file descriptor and throws
         * [java.io.FileNotFoundException]. The mapping stays valid after the descriptor is closed and lives as long as
         * the returned buffer (and every view of it) is reachable.
         */
        fun mapAsset(assets: AssetManager, path: String): ByteBuffer = assets.openFd(path).use { fd ->
            require(fd.declaredLength >= 0) { "asset '$path' has no declared length" }
            FileInputStream(fd.fileDescriptor).channel.use { channel ->
                channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
            }
        }
    }
}
