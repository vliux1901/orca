package org.vliux.orca.normalization

import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.ResolverStyle
import java.time.temporal.ChronoField
import java.util.Locale

internal object NormalizationRules {
    private val uppercaseTokens = setOf(
        "BMW", "GM", "GMC", "KIA", "MINI", "SAAB", "SUV", "USA", "VW",
        "LLC", "INC", "CO", "LP", "LTD",
    )

    fun normalizeShopName(raw: String?): String? {
        val cleaned = normalizeWhitespace(raw).orEmpty()
            .replace(Regex("\\s+([&/,-])\\s+"), " $1 ")
            .replace(Regex("\\s+"), " ")
            .trim(' ', ',', ';', ':')
        if (cleaned.isEmpty()) return null
        return cleaned
            .split(' ')
            .joinToString(" ") { token ->
                if (token.isBlank()) {
                    token
                } else {
                    val uppercase = token.uppercase(Locale.US)
                    when {
                        uppercaseTokens.contains(uppercase) -> uppercase
                        token.any(Char::isDigit) -> token.uppercase(Locale.US)
                        else -> token.lowercase(Locale.US).replaceFirstChar(Char::uppercaseChar)
                    }
                }
            }
    }

    fun normalizeServiceCategory(
        rawCategory: String?,
        label: String?,
        notes: String?,
    ): String? {
        val category = normalizeWhitespace(rawCategory)?.lowercase(Locale.US)
        val context = listOfNotNull(category, normalizeWhitespace(label), normalizeWhitespace(notes))
            .joinToString(" ")
            .lowercase(Locale.US)
        if (context.isBlank()) return null

        return when {
            context.containsAny("oil", "lub", "lube") -> "OIL_CHANGE"
            context.containsAny("brake", "pads", "rotor", "caliper") -> "BRAKES"
            context.containsAny("tire", "tyre", "wheel", "rotation", "alignment", "balance") -> "TIRES"
            context.containsAny("inspection", "multipoint", "checkup", "check-up") -> "INSPECTION"
            context.containsAny("battery", "charging", "alternator", "starter") -> "BATTERY"
            context.containsAny("coolant", "radiator", "thermostat") -> "COOLING"
            context.containsAny("transmission", "gearbox", "atf", "cvt") -> "TRANSMISSION"
            context.containsAny("engine", "spark plug", "ignition", "timing") -> "ENGINE"
            context.containsAny("filter", "air filter", "cabin filter", "fuel filter") -> "FILTERS"
            context.containsAny("fluid", "flush", "power steering", "differential") -> "FLUIDS"
            context.containsAny("suspension", "shock", "strut", "control arm") -> "SUSPENSION"
            context.containsAny("diagnostic", "scan", "troubleshoot", "inspection code") -> "DIAGNOSTIC"
            context.containsAny("wiper", "blade") -> "WIPERS"
            context.containsAny("detail", "wash", "cleaning") -> "DETAILING"
            category != null -> category.replace('-', '_').replace(' ', '_').uppercase(Locale.US)
            else -> "OTHER"
        }
    }

    fun normalizeWhitespace(raw: String?): String? =
        raw?.trim()?.replace(Regex("\\s+"), " ")?.takeIf { it.isNotEmpty() }

    fun normalizeDate(raw: String?): LocalDate? {
        val value = normalizeWhitespace(raw) ?: return null
        val candidates = listOf(
            value,
            value.uppercase(Locale.US),
            value.replace(" ", "").uppercase(Locale.US),
            value.replace("-", "/"),
            value.replace(".", "/"),
        ).distinct()
        candidates.forEach { candidate ->
            dateFormatters.forEach { formatter ->
                try {
                    return LocalDate.parse(candidate, formatter)
                } catch (_: DateTimeParseException) {
                    Unit
                }
            }
        }
        return null
    }

    fun normalizeMileage(raw: String?): Int? {
        val digits = raw.orEmpty().filter(Char::isDigit)
        return digits.toIntOrNull()?.takeIf { it in 0..2_000_000 }
    }

    fun normalizeCostMinor(raw: String?): Long? {
        val digits = raw.orEmpty().filter(Char::isDigit)
        return digits.toLongOrNull()?.takeIf { it >= 0 }
    }

    fun shouldExcludeServiceItem(
        category: String?,
        label: String?,
        notes: String?,
    ): Boolean {
        val combined = listOfNotNull(category, label, notes)
            .joinToString(" ")
            .lowercase(Locale.US)
        if (combined.isBlank()) return false
        if (nonServiceChargePatterns.any { it.containsMatchIn(combined) }) return true
        return combined.contains('%') &&
            (combined.contains("off") || combined.contains("discount") || combined.contains("coupon"))
    }

    private fun String.containsAny(vararg terms: String): Boolean = terms.any { contains(it) }

    private val dateFormatters = listOf(
        DateTimeFormatter.ISO_LOCAL_DATE,
        DateTimeFormatter.ofPattern("M/d/uuuu", Locale.US).withResolverStyle(ResolverStyle.SMART),
        DateTimeFormatter.ofPattern("M/d/uu", Locale.US).withResolverStyle(ResolverStyle.SMART),
        DateTimeFormatter.ofPattern("MM/dd/uuuu", Locale.US).withResolverStyle(ResolverStyle.SMART),
        DateTimeFormatter.ofPattern("MM/dd/uu", Locale.US).withResolverStyle(ResolverStyle.SMART),
        DateTimeFormatter.ofPattern("d MMM uuuu", Locale.US).withResolverStyle(ResolverStyle.SMART),
        DateTimeFormatter.ofPattern("d MMM uu", Locale.US).withResolverStyle(ResolverStyle.SMART),
        DateTimeFormatter.ofPattern("ddMMMyyyy", Locale.US).withResolverStyle(ResolverStyle.SMART),
        DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("ddMMM")
            .appendValueReduced(ChronoField.YEAR, 2, 2, 2000)
            .toFormatter(Locale.US)
            .withResolverStyle(ResolverStyle.SMART),
    )

    private val nonServiceChargePatterns = listOf(
        Regex("\\bdiscount\\b"),
        Regex("\\bcoupon\\b"),
        Regex("\\bfee(s)?\\b"),
        Regex("\\btax(es)?\\b"),
        Regex("\\bdisposal\\b"),
        Regex("\\bwaste\\s+disp(osal)?\\b"),
        Regex("\\bshop\\s+suppl(y|ies)\\b"),
        Regex("\\bhazmat\\b"),
        Regex("\\benvironmental\\b"),
    )
}
