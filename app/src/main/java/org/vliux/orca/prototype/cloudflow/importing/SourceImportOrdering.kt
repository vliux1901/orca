package org.vliux.orca.prototype.cloudflow.importing

object SourceImportOrdering {
    fun order(imports: List<SourceImportRequest>): List<SourceImportRequest> {
        return sortIndexed(imports) { it.displayName }
            .map { it.value }
    }

    internal fun orderDisplayNames(displayNames: List<String?>): List<String?> {
        return sortIndexed(displayNames) { it }
            .map { it.value }
    }

    private fun <T> sortIndexed(
        values: List<T>,
        displayNameOf: (T) -> String?,
    ): List<IndexedValue<T>> {
        return values.withIndex()
            .sortedWith { left, right ->
                compareDisplayNames(displayNameOf(left.value), displayNameOf(right.value))
                    .takeIf { it != 0 }
                    ?: left.index.compareTo(right.index)
            }
    }

    private fun compareDisplayNames(left: String?, right: String?): Int {
        val leftValue = left?.trim().orEmpty()
        val rightValue = right?.trim().orEmpty()
        if (leftValue.isEmpty() || rightValue.isEmpty()) {
            return leftValue.compareTo(rightValue, ignoreCase = true)
        }

        val leftTokens = tokenize(leftValue)
        val rightTokens = tokenize(rightValue)
        val sharedSize = minOf(leftTokens.size, rightTokens.size)
        for (index in 0 until sharedSize) {
            val comparison = leftTokens[index].compareTo(rightTokens[index])
            if (comparison != 0) {
                return comparison
            }
        }
        return leftTokens.size.compareTo(rightTokens.size)
    }

    private fun tokenize(value: String): List<SortToken> {
        val tokens = mutableListOf<SortToken>()
        var cursor = 0
        while (cursor < value.length) {
            val start = cursor
            val digitRun = value[cursor].isDigit()
            while (cursor < value.length && value[cursor].isDigit() == digitRun) {
                cursor += 1
            }
            val token = value.substring(start, cursor)
            tokens += if (digitRun) {
                SortToken.Number(token.toBigIntegerOrNull(), token.length)
            } else {
                SortToken.Text(token.lowercase())
            }
        }
        return tokens
    }

    private sealed interface SortToken : Comparable<SortToken> {
        data class Text(val value: String) : SortToken {
            override fun compareTo(other: SortToken): Int = when (other) {
                is Number -> 1
                is Text -> value.compareTo(other.value)
            }
        }

        data class Number(
            val value: java.math.BigInteger?,
            val width: Int,
        ) : SortToken {
            override fun compareTo(other: SortToken): Int = when (other) {
                is Text -> -1
                is Number -> {
                    val valueComparison = when {
                        value == null && other.value == null -> 0
                        value == null -> 1
                        other.value == null -> -1
                        else -> value.compareTo(other.value)
                    }
                    if (valueComparison != 0) {
                        valueComparison
                    } else {
                        width.compareTo(other.width)
                    }
                }
            }
        }
    }
}
