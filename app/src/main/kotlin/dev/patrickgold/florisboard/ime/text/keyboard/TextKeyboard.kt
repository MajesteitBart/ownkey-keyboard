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

package dev.patrickgold.florisboard.ime.text.keyboard

import dev.patrickgold.florisboard.ime.keyboard.Key
import dev.patrickgold.florisboard.ime.keyboard.KeyData
import dev.patrickgold.florisboard.ime.keyboard.Keyboard
import dev.patrickgold.florisboard.ime.keyboard.KeyboardMode
import dev.patrickgold.florisboard.ime.keyboard.SplitLayoutSpec
import dev.patrickgold.florisboard.ime.popup.PopupMapping
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import kotlin.math.abs

class TextKeyboard(
    val arrangement: Array<Array<TextKey>>,
    override val mode: KeyboardMode,
    val extendedPopupMapping: PopupMapping?,
    val extendedPopupMappingDefault: PopupMapping?,
) : Keyboard() {
    val rowCount: Int
        get() = arrangement.size

    val keyCount: Int
        get() = arrangement.sumOf { it.size }

    override fun getKeyForPos(pointerX: Float, pointerY: Float): TextKey? {
        for (key in keys()) {
            if (key.touchBounds.contains(pointerX, pointerY)) {
                return key
            }
        }
        return null
    }

    override fun layout(
        keyboardWidth: Float,
        keyboardHeight: Float,
        desiredKey: Key,
        extendTouchBoundariesDownwards: Boolean,
        splitSpec: SplitLayoutSpec?,
    ) {
        if (arrangement.isEmpty()) return

        val desiredTouchBounds = desiredKey.touchBounds
        val desiredVisibleBounds = desiredKey.visibleBounds
        if (desiredTouchBounds.isEmpty() || desiredVisibleBounds.isEmpty()) return
        if (keyboardWidth.isNaN() || keyboardHeight.isNaN()) return
        val rowMarginV = (keyboardHeight - desiredTouchBounds.height * rowCount.toFloat()) / (rowCount - 1).coerceAtLeast(1).toFloat()

        val isSplitLayout = splitSpec != null && splitSpec.gapWidth > 0.0f
        if (!isSplitLayout) {
            // The arrangement may contain a duplicated space key (inserted for the split layout) while this
            // layout pass is not split, e.g. in floating/compact window modes. Collapse the duplicate so the
            // row renders with a single space bar.
            collapseDuplicateSpaceKeys()
        }

        for ((r, row) in rows().withIndex()) {
            val posY = (desiredTouchBounds.height + rowMarginV) * r
            val isLastRow = r + 1 == arrangement.size
            val extendBottom = extendTouchBoundariesDownwards && isLastRow
            val splitIndex = if (isSplitLayout) determineSplitIndex(row) else -1
            if (splitIndex <= 0 || splitIndex >= row.size) {
                layoutRowSegment(
                    row, 0, row.size, posY, 0.0f, keyboardWidth, desiredKey, extendBottom,
                )
            } else {
                val segmentWidth = (keyboardWidth - splitSpec!!.gapWidth) / 2.0f
                layoutRowSegment(
                    row, 0, splitIndex, posY, 0.0f, segmentWidth, desiredKey, extendBottom,
                )
                layoutRowSegment(
                    row, splitIndex, row.size, posY, segmentWidth + splitSpec.gapWidth, segmentWidth, desiredKey, extendBottom,
                )
            }
        }
    }

    /**
     * Determines the key index at which a row should be divided for the split layout. The chosen boundary is
     * preferably between two adjacent space keys (inserted by the layout computation when split is active),
     * otherwise the boundary whose cumulative key width is closest to half of the row's total width.
     *
     * @return The index of the first key belonging to the right half, or `-1` if the row cannot be split.
     */
    private fun determineSplitIndex(row: Array<TextKey>): Int {
        if (row.size < 2) return -1

        for (k in 0 until row.size - 1) {
            val code = keyCodeForSplit(row[k])
            val nextCode = keyCodeForSplit(row[k + 1])
            if ((code == KeyCode.SPACE || code == KeyCode.CJK_SPACE) && code == nextCode) {
                return k + 1
            }
        }

        val totalWidth = row.sumOf { it.flayWidthFactor.toDouble() }.toFloat()
        if (totalWidth <= 0.0f) return -1
        var bestIndex = -1
        var bestDelta = Float.MAX_VALUE
        var cumulativeWidth = 0.0f
        for (k in 0 until row.size - 1) {
            cumulativeWidth += row[k].flayWidthFactor
            val delta = abs(cumulativeWidth - totalWidth / 2.0f)
            if (delta < bestDelta) {
                bestDelta = delta
                bestIndex = k + 1
            }
        }
        return bestIndex
    }

    private fun collapseDuplicateSpaceKeys() {
        for (row in arrangement) {
            for (k in 0 until row.size - 1) {
                val code = keyCodeForSplit(row[k])
                if ((code == KeyCode.SPACE || code == KeyCode.CJK_SPACE) && code == keyCodeForSplit(row[k + 1])) {
                    row[k + 1].apply {
                        flayWidthFactor = 0.0f
                        flayGrow = 0.0f
                        flayShrink = 0.0f
                    }
                }
            }
        }
    }

    private fun keyCodeForSplit(key: TextKey): Int {
        val computedCode = key.computedData.code
        if (computedCode != 0) return computedCode
        return (key.data as? KeyData)?.code ?: 0
    }

    private fun layoutRowSegment(
        row: Array<TextKey>,
        fromIndex: Int,
        toIndex: Int,
        posY: Float,
        segmentStartX: Float,
        segmentWidth: Float,
        desiredKey: Key,
        extendTouchBoundariesDownwards: Boolean,
    ) {
        val desiredTouchBounds = desiredKey.touchBounds
        val desiredVisibleBounds = desiredKey.visibleBounds
        val rowMarginH = abs(desiredTouchBounds.width - desiredVisibleBounds.width)
        val segmentEndX = segmentStartX + segmentWidth
        val segmentSize = toIndex - fromIndex
        val availableWidth = (segmentWidth - rowMarginH) / desiredTouchBounds.width
        var requestedWidth = 0.0f
        var shrinkSum = 0.0f
        var growSum = 0.0f
        for (i in fromIndex until toIndex) {
            val key = row[i]
            requestedWidth += key.flayWidthFactor
            shrinkSum += key.flayShrink
            growSum += key.flayGrow
        }
        if (requestedWidth <= availableWidth) {
            // Requested with is smaller or equal to the available with, so we can grow
            val additionalWidth = availableWidth - requestedWidth
            var posX = segmentStartX + rowMarginH / 2.0f
            for (i in fromIndex until toIndex) {
                val key = row[i]
                val k = i - fromIndex
                val keyWidth = desiredTouchBounds.width * when (growSum) {
                    0.0f -> when (k) {
                        0, segmentSize - 1 -> key.flayWidthFactor + additionalWidth / 2.0f
                        else -> key.flayWidthFactor
                    }
                    else -> key.flayWidthFactor + additionalWidth * (key.flayGrow / growSum)
                }
                key.touchBounds.apply {
                    left = posX
                    top = posY
                    right = posX + keyWidth
                    bottom = posY + desiredTouchBounds.height
                }
                key.visibleBounds.apply {
                    left = key.touchBounds.left + abs(desiredTouchBounds.left - desiredVisibleBounds.left) + when {
                        growSum == 0.0f && k == 0 -> ((additionalWidth / 2.0f) * desiredTouchBounds.width)
                        else -> 0.0f
                    }
                    top = key.touchBounds.top + abs(desiredTouchBounds.top - desiredVisibleBounds.top)
                    right = key.touchBounds.right - abs(desiredTouchBounds.right - desiredVisibleBounds.right) - when {
                        growSum == 0.0f && k == segmentSize - 1 -> ((additionalWidth / 2.0f) * desiredTouchBounds.width)
                        else -> 0.0f
                    }
                    bottom = key.touchBounds.bottom - abs(desiredTouchBounds.bottom - desiredVisibleBounds.bottom)
                }
                posX += keyWidth
                // After-adjust touch bounds for the row margin
                key.touchBounds.apply {
                    if (k == 0) {
                        left = segmentStartX
                    } else if (k == segmentSize - 1) {
                        right = segmentEndX
                    }
                    if (extendTouchBoundariesDownwards) {
                        bottom += height
                    }
                }
            }
        } else {
            // Requested size too big, must shrink.
            val clippingWidth = requestedWidth - availableWidth
            var posX = segmentStartX + rowMarginH / 2.0f
            for (i in fromIndex until toIndex) {
                val key = row[i]
                val k = i - fromIndex
                val keyWidth = desiredTouchBounds.width * if (key.flayShrink == 0.0f) {
                    key.flayWidthFactor
                } else {
                    key.flayWidthFactor - clippingWidth * (key.flayShrink / shrinkSum)
                }
                key.touchBounds.apply {
                    left = posX
                    top = posY
                    right = posX + keyWidth
                    bottom = posY + desiredTouchBounds.height
                }
                key.visibleBounds.apply {
                    left = key.touchBounds.left + abs(desiredTouchBounds.left - desiredVisibleBounds.left)
                    top = key.touchBounds.top + abs(desiredTouchBounds.top - desiredVisibleBounds.top)
                    right = key.touchBounds.right - abs(desiredTouchBounds.right - desiredVisibleBounds.right)
                    bottom = key.touchBounds.bottom - abs(desiredTouchBounds.bottom - desiredVisibleBounds.bottom)
                }
                posX += keyWidth
                // After-adjust touch bounds for the row margin
                key.touchBounds.apply {
                    if (k == 0) {
                        left = segmentStartX
                    } else if (k == segmentSize - 1) {
                        right = segmentEndX
                    }
                    if (extendTouchBoundariesDownwards) {
                        bottom += height
                    }
                }
            }
        }
    }

    override fun keys(): Iterator<TextKey> {
        return TextKeyboardIterator(arrangement)
    }

    fun rows(): Iterator<Array<TextKey>> {
        return arrangement.iterator()
    }

    class TextKeyboardIterator internal constructor(
        private val arrangement: Array<Array<TextKey>>
    ) : Iterator<TextKey> {
        private var rowIndex: Int = 0
        private var keyIndex: Int = 0

        override fun hasNext(): Boolean {
            return rowIndex < arrangement.size && keyIndex < arrangement[rowIndex].size
        }

        override fun next(): TextKey {
            val next = arrangement[rowIndex][keyIndex]
            if (keyIndex + 1 == arrangement[rowIndex].size) {
                rowIndex++
                keyIndex = 0
            } else {
                keyIndex++
            }
            return next
        }
    }
}
