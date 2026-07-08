package org.vliux.orca.search

import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.vliux.orca.data.QaEventRecord
import org.vliux.orca.data.QaFactRecord
import org.vliux.orca.data.QaServiceRecord
import org.vliux.orca.normalization.NormalizationRules

data class SearchResultItem(
    val entryId: String,
    val vehicleId: String,
    val documentId: String,
    val documentTitle: String,
    val entryType: String,
    val title: String,
    val body: String,
    val sourceId: String?,
    val sourcePage: Int?,
    val occurredOn: LocalDate?,
    val createdAt: Instant,
)

data class SearchCitation(
    val vehicleId: String,
    val documentId: String,
    val documentTitle: String,
    val sourceId: String?,
    val sourcePage: Int?,
)

data class SearchAnswer(
    val question: String,
    val answer: String,
    val citations: List<SearchCitation>,
    val relatedDate: LocalDate? = null,
    val relatedMileage: Int? = null,
    val uncertaintyNote: String? = null,
    val isSupported: Boolean = true,
)

class SearchAnswerEngine {
    fun answer(
        question: String,
        events: List<QaEventRecord>,
        services: List<QaServiceRecord>,
        facts: List<QaFactRecord>,
    ): SearchAnswer {
        val normalizedQuestion = NormalizationRules.normalizeWhitespace(question)
            ?: return SearchAnswer(
                question = question,
                answer = "Enter a search term or a maintenance question.",
                citations = emptyList(),
                isSupported = false,
            )
        val intent = parseIntent(normalizedQuestion)
        return when (intent) {
            QuestionIntent.Unsupported -> unsupportedAnswer(normalizedQuestion)
            QuestionIntent.LatestServiceOverall -> answerLatestServiceOverall(normalizedQuestion, events, services, facts)
            is QuestionIntent.LatestComponentWork -> answerLatestComponentWork(normalizedQuestion, intent, services, facts)
        }
    }

    private fun answerLatestServiceOverall(
        question: String,
        events: List<QaEventRecord>,
        services: List<QaServiceRecord>,
        facts: List<QaFactRecord>,
    ): SearchAnswer {
        if (events.isEmpty()) {
            return SearchAnswer(
                question = question,
                answer = "No maintenance events are recorded yet.",
                citations = emptyList(),
                uncertaintyNote = "Import and process at least one maintenance document before asking service-history questions.",
            )
        }
        val sortedEvents = events.sortedWith(
            compareByDescending<QaEventRecord> { it.eventDate != null }
                .thenByDescending { it.eventDate }
                .thenByDescending { it.createdAt },
        )
        val topEvent = sortedEvents.first()
        val topEvents = sortedEvents.takeWhile {
            comparableDate(it.eventDate) == comparableDate(topEvent.eventDate)
        }
        val factsByDocument = facts.groupBy(QaFactRecord::documentId)
        val summaries = topEvents.map { event ->
            val relatedServices = services
                .filter { it.eventId == event.eventId }
                .map(QaServiceRecord::label)
                .distinct()
            EventSummary(
                record = event,
                serviceSummary = when {
                    relatedServices.isEmpty() -> "service details unavailable"
                    relatedServices.size == 1 -> relatedServices.first()
                    else -> relatedServices.joinToString(", ")
                },
                mileage = event.mileage ?: factsByDocument[event.documentId].orEmpty().firstNormalizedInt("ODOMETER"),
                shopName = event.shopName ?: factsByDocument[event.documentId].orEmpty().firstPreferredText("SHOP_NAME"),
            )
        }

        val primary = summaries.first()
        val answerText = if (summaries.size == 1) {
            buildString {
                append("Latest recorded service")
                primary.record.eventDate?.let { append(" was on ${it.format(answerDateFormatter)}") }
                primary.shopName?.let { append(" at $it") }
                append(" for ${primary.serviceSummary}.")
            }
        } else {
            val grouped = summaries.joinToString("; ") { summary ->
                buildString {
                    append(summary.record.documentTitle.orFallbackDocumentTitle())
                    append(": ")
                    append(summary.serviceSummary)
                }
            }
            buildString {
                append("Latest recorded service")
                primary.record.eventDate?.let { append(" date is ${it.format(answerDateFormatter)}") }
                append(". Records on that date show $grouped.")
            }
        }

        return SearchAnswer(
            question = question,
            answer = answerText,
            citations = summaries.map(::citationForEventSummary).distinct(),
            relatedDate = primary.record.eventDate,
            relatedMileage = primary.mileage,
            uncertaintyNote = when {
                summaries.size > 1 -> "Multiple documents share the latest recorded service date."
                primary.record.eventDate == null -> "The latest matching record does not include a service date."
                else -> null
            },
        )
    }

    private fun answerLatestComponentWork(
        question: String,
        intent: QuestionIntent.LatestComponentWork,
        services: List<QaServiceRecord>,
        facts: List<QaFactRecord>,
    ): SearchAnswer {
        val matching = services
            .filter { it.category.equals(intent.category, ignoreCase = true) }
            .sortedWith(
                compareByDescending<QaServiceRecord> { it.eventDate != null }
                    .thenByDescending { it.eventDate }
                    .thenByDescending { it.createdAt },
            )
        if (matching.isEmpty()) {
            return SearchAnswer(
                question = question,
                answer = "I could not find a recorded ${intent.displayLabel.lowercase(Locale.US)} maintenance item.",
                citations = emptyList(),
                uncertaintyNote = "Try a keyword search if the document uses unusual wording or shorthand.",
            )
        }
        val top = matching.first()
        val topMatches = matching.takeWhile {
            comparableDate(it.eventDate) == comparableDate(top.eventDate)
        }
        val factsByDocument = facts.groupBy(QaFactRecord::documentId)
        val enriched = topMatches.map { service ->
            ComponentSummary(
                record = service,
                mileage = service.mileage ?: factsByDocument[service.documentId].orEmpty().firstNormalizedInt("ODOMETER"),
                shopName = service.shopName ?: factsByDocument[service.documentId].orEmpty().firstPreferredText("SHOP_NAME"),
            )
        }
        val primary = enriched.first()
        val answerText = if (enriched.size == 1) {
            buildString {
                append("Latest recorded ${intent.displayLabel.lowercase(Locale.US)} work is ${quote(primary.record.label)}")
                primary.record.eventDate?.let { append(" on ${it.format(answerDateFormatter)}") }
                primary.shopName?.let { append(" at $it") }
                append(".")
            }
        } else {
            val labels = enriched.joinToString("; ") { quote(it.record.label) }
            buildString {
                append("Latest recorded ${intent.displayLabel.lowercase(Locale.US)} work")
                primary.record.eventDate?.let { append(" on ${it.format(answerDateFormatter)}") }
                append(" includes $labels.")
            }
        }
        return SearchAnswer(
            question = question,
            answer = answerText,
            citations = enriched.map(::citationForComponentSummary).distinct(),
            relatedDate = primary.record.eventDate,
            relatedMileage = primary.mileage,
            uncertaintyNote = when {
                enriched.size > 1 -> "More than one matching maintenance item shares the latest recorded date."
                primary.record.eventDate == null -> "The latest matching record does not include a service date."
                else -> null
            },
        )
    }

    private fun unsupportedAnswer(question: String): SearchAnswer = SearchAnswer(
        question = question,
        answer = "This build supports maintenance-history questions like \"When was the last service?\" or \"What was last done to the brakes?\"",
        citations = emptyList(),
        isSupported = false,
        uncertaintyNote = "V1 QA is deterministic and limited to grounded maintenance-history questions.",
    )

    private fun parseIntent(question: String): QuestionIntent {
        val normalized = question.lowercase(Locale.US)
        val component = detectComponentCategory(normalized)
        val asksLatest = normalized.containsAny("last", "latest", "most recent")
        val asksService = normalized.containsAny("service", "serviced", "done", "performed", "changed", "replaced", "work")
        val asksWhen = normalized.containsAny("when", "date")
        if (component != null && (asksLatest || asksService || asksWhen)) {
            return QuestionIntent.LatestComponentWork(
                category = component.category,
                displayLabel = component.displayLabel,
            )
        }
        if (normalized.contains("last service") ||
            normalized.contains("latest service") ||
            normalized.contains("most recent service") ||
            (asksWhen && normalized.contains("servic"))
        ) {
            return QuestionIntent.LatestServiceOverall
        }
        return QuestionIntent.Unsupported
    }

    private fun detectComponentCategory(question: String): ComponentMatch? {
        val mappings = listOf(
            ComponentMatch("OIL_CHANGE", "Oil") to listOf("oil", "lube", "lubrication"),
            ComponentMatch("BRAKES", "Brakes") to listOf("brake", "brakes", "pad", "pads", "rotor", "caliper"),
            ComponentMatch("TIRES", "Tires") to listOf("tire", "tires", "tyre", "wheel", "alignment", "rotation"),
            ComponentMatch("BATTERY", "Battery") to listOf("battery", "alternator", "starter"),
            ComponentMatch("ENGINE", "Engine") to listOf("engine", "spark plug", "ignition", "timing"),
            ComponentMatch("FILTERS", "Filters") to listOf("filter", "filters", "air filter", "cabin filter", "fuel filter"),
            ComponentMatch("COOLING", "Cooling") to listOf("coolant", "radiator", "thermostat", "cooling"),
            ComponentMatch("TRANSMISSION", "Transmission") to listOf("transmission", "gearbox", "atf", "cvt"),
            ComponentMatch("FLUIDS", "Fluids") to listOf("fluid", "fluids", "flush", "power steering", "differential"),
            ComponentMatch("SUSPENSION", "Suspension") to listOf("suspension", "shock", "strut", "control arm"),
            ComponentMatch("DIAGNOSTIC", "Diagnostic") to listOf("diagnostic", "diagnostics", "scan", "troubleshoot"),
            ComponentMatch("WIPERS", "Wipers") to listOf("wiper", "wipers", "blade", "blades"),
            ComponentMatch("DETAILING", "Detailing") to listOf("detail", "detailing", "wash", "cleaning"),
            ComponentMatch("INSPECTION", "Inspection") to listOf("inspection", "checkup", "check-up", "multipoint"),
        )
        return mappings.firstOrNull { (_, terms) -> terms.any(question::contains) }?.first
    }

    private fun comparableDate(value: LocalDate?): LocalDate = value ?: LocalDate.MIN

    private fun List<QaFactRecord>.firstPreferredText(factType: String): String? = this
        .filter { it.factType.equals(factType, ignoreCase = true) }
        .sortedByDescending(QaFactRecord::isUserEdited)
        .firstOrNull()
        ?.let { it.normalizedValue ?: it.valueText }
        ?.takeIf { it.isNotBlank() }

    private fun List<QaFactRecord>.firstNormalizedInt(factType: String): Int? = this
        .filter { it.factType.equals(factType, ignoreCase = true) }
        .sortedByDescending(QaFactRecord::isUserEdited)
        .firstNotNullOfOrNull { record ->
            NormalizationRules.normalizeMileage(record.normalizedValue ?: record.valueText)
        }

    private fun citationForEventSummary(summary: EventSummary): SearchCitation {
        return SearchCitation(
            vehicleId = summary.record.vehicleId,
            documentId = summary.record.documentId,
            documentTitle = summary.record.documentTitle.orFallbackDocumentTitle(),
            sourceId = null,
            sourcePage = null,
        )
    }

    private fun citationForComponentSummary(summary: ComponentSummary): SearchCitation = SearchCitation(
        vehicleId = summary.record.vehicleId,
        documentId = summary.record.documentId,
        documentTitle = summary.record.documentTitle.orFallbackDocumentTitle(),
        sourceId = summary.record.sourceId,
        sourcePage = summary.record.sourcePage,
    )

    private fun String?.orFallbackDocumentTitle(): String = this?.takeIf { it.isNotBlank() } ?: "Maintenance document"

    private fun String.containsAny(vararg values: String): Boolean = values.any(::contains)

    private fun quote(value: String): String = "\"$value\""

    private data class EventSummary(
        val record: QaEventRecord,
        val serviceSummary: String,
        val mileage: Int?,
        val shopName: String?,
    )

    private data class ComponentSummary(
        val record: QaServiceRecord,
        val mileage: Int?,
        val shopName: String?,
    )

    private data class ComponentMatch(
        val category: String,
        val displayLabel: String,
    )

    private sealed interface QuestionIntent {
        data object Unsupported : QuestionIntent
        data object LatestServiceOverall : QuestionIntent
        data class LatestComponentWork(
            val category: String,
            val displayLabel: String,
        ) : QuestionIntent
    }

    private companion object {
        val answerDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
    }
}
