package org.vliux.orca.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.vliux.orca.normalization.NormalizationRules
import org.vliux.orca.search.SearchAnswer
import org.vliux.orca.search.SearchAnswerEngine
import org.vliux.orca.search.SearchResultItem

class SearchRepository(
    private val dao: DocumentDao,
    private val answerEngine: SearchAnswerEngine,
) {
    fun observeSearchResults(
        query: String,
        limit: Int = DEFAULT_LIMIT,
    ): Flow<List<SearchResultItem>> {
        val normalizedQuery = NormalizationRules.normalizeWhitespace(query).orEmpty()
        return dao.observeSearchEntries(normalizedQuery, limit).map { rows ->
            rows.map { row ->
                SearchResultItem(
                    entryId = row.entryId,
                    vehicleId = row.vehicleId,
                    documentId = row.documentId,
                    documentTitle = row.documentTitle?.takeIf { it.isNotBlank() } ?: "Maintenance document",
                    entryType = row.entryType,
                    title = row.title,
                    body = row.body,
                    sourceId = row.sourceId,
                    sourcePage = row.sourcePage,
                    occurredOn = row.occurredOn,
                    createdAt = row.createdAt,
                )
            }
        }
    }

    suspend fun answerQuestion(question: String): SearchAnswer = answerEngine.answer(
        question = question,
        events = dao.getQaEvents(),
        services = dao.getQaServices(),
        facts = dao.getQaFacts(),
    )

    private companion object {
        const val DEFAULT_LIMIT = 40
    }
}
