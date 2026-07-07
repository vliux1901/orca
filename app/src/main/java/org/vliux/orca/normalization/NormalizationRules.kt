package org.vliux.orca.normalization

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

    private fun String.containsAny(vararg terms: String): Boolean = terms.any { contains(it) }
}
