package org.ownkey.offline

/**
 * Encodes vocabulary terms into the slash-separated hotword string that sherpa-onnx accepts per stream.
 *
 * Terms are treated literally: slashes, colons, line breaks and control characters cannot be
 * expressed in the native syntax, so they become spaces. The result is bounded so one request stays
 * far below the Binder transaction limit; saved entries are never truncated, only the request is.
 */
object HotwordTransport {
    /** UTF-8 budget for one request. This is an IPC bound, not a provider or product limit. */
    const val MAX_BYTES = 64 * 1024
    const val SEPARATOR = '/'

    data class Encoded(val hotwords: String, val included: Int, val dropped: Int) {
        val isEmpty: Boolean get() = hotwords.isEmpty()
    }

    fun sanitize(term: String): String = term
        .map { char -> if (char == SEPARATOR || char == ':' || char.isISOControl() || char.isWhitespace()) ' ' else char }
        .joinToString("")
        .split(' ')
        .filter { it.isNotEmpty() }
        .joinToString(" ")

    /**
     * Keeps terms in saved order and stops at the first term that no longer fits the budget. A term
     * that has nothing left after sanitising, or that collides with an earlier term once sanitised,
     * cannot be transported either and counts as dropped so the reported numbers stay honest.
     */
    fun encode(terms: List<String>, maxBytes: Int = MAX_BYTES): Encoded {
        val builder = StringBuilder()
        val seen = HashSet<String>()
        var bytes = 0
        var included = 0
        var dropped = 0
        var overflowed = false
        for (raw in terms) {
            if (raw.isBlank()) continue
            val term = sanitize(raw)
            if (term.isEmpty() || !seen.add(term.lowercase())) {
                dropped++
                continue
            }
            if (overflowed) {
                dropped++
                continue
            }
            val piece = if (builder.isEmpty()) term else "$SEPARATOR$term"
            val pieceBytes = piece.toByteArray(Charsets.UTF_8).size
            if (bytes + pieceBytes > maxBytes) {
                overflowed = true
                dropped++
                continue
            }
            builder.append(piece)
            bytes += pieceBytes
            included++
        }
        return Encoded(builder.toString(), included, dropped)
    }
}
