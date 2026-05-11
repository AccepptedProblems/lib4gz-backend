package com.example.lib4gz.common.utils

/**
 * Parses and queries the `?expand=` query parameter used by composite read endpoints.
 *
 * Supports comma-separated, case-insensitive tokens. Unknown tokens are silently ignored;
 * controllers and services consult `has(...)` to decide which optional joins to perform.
 *
 * Designed for the Open/Closed Principle: adding a new expand value extends an endpoint
 * without changing its signature — controllers, services, and clients opt in by token name.
 */
class ExpandTokens private constructor(private val tokens: Set<String>) {

    fun has(token: String): Boolean = tokens.contains(token.lowercase())

    fun isEmpty(): Boolean = tokens.isEmpty()

    companion object {
        val EMPTY: ExpandTokens = ExpandTokens(emptySet())

        fun parse(raw: String?): ExpandTokens {
            if (raw.isNullOrBlank()) return EMPTY
            val parsed = raw.split(',')
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }
                .toSet()
            return if (parsed.isEmpty()) EMPTY else ExpandTokens(parsed)
        }
    }
}
