package com.uiery.keep.domain.websiteblocking

import java.net.IDN
import java.util.Locale

private const val MAX_LABEL_LENGTH = 63
private const val MAX_HOST_LENGTH = 253

@JvmInline
value class DomainName(val value: String)

sealed interface DomainNameNormalizationResult {
    data class Valid(val domain: DomainName) : DomainNameNormalizationResult
    data class Invalid(val reason: DomainNameInvalidReason) : DomainNameNormalizationResult
}

enum class DomainNameInvalidReason {
    Empty,
    Wildcard,
    LocalhostOrSingleLabel,
    IpLiteral,
    MalformedLabel,
    LabelTooLong,
    HostTooLong,
}

object DomainNamePolicy {
    fun normalize(input: String): DomainNameNormalizationResult {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return DomainNameNormalizationResult.Invalid(DomainNameInvalidReason.Empty)
        }
        if ("*" in trimmed) {
            return DomainNameNormalizationResult.Invalid(DomainNameInvalidReason.Wildcard)
        }

        val rawHost = trimmed.toRawHost()
        if (rawHost.isEmpty()) {
            return DomainNameNormalizationResult.Invalid(DomainNameInvalidReason.Empty)
        }
        if (rawHost.startsWith("[") || rawHost.endsWith("]") || ":" in rawHost) {
            return DomainNameNormalizationResult.Invalid(DomainNameInvalidReason.IpLiteral)
        }

        val host = rawHost
            .removeSuffix(".")
            .lowercase(Locale.US)
        if (host.isEmpty()) {
            return DomainNameNormalizationResult.Invalid(DomainNameInvalidReason.Empty)
        }
        if (host.isIpv4Literal()) {
            return DomainNameNormalizationResult.Invalid(DomainNameInvalidReason.IpLiteral)
        }

        val asciiLabels = host.split(".").map { label ->
            if (label.isEmpty()) {
                return DomainNameNormalizationResult.Invalid(DomainNameInvalidReason.MalformedLabel)
            }
            if (label.length > MAX_LABEL_LENGTH) {
                return DomainNameNormalizationResult.Invalid(DomainNameInvalidReason.LabelTooLong)
            }

            val asciiLabel = try {
                IDN.toASCII(label).lowercase(Locale.US)
            } catch (_: IllegalArgumentException) {
                return DomainNameNormalizationResult.Invalid(DomainNameInvalidReason.MalformedLabel)
            }

            when {
                asciiLabel.isEmpty() -> {
                    return DomainNameNormalizationResult.Invalid(DomainNameInvalidReason.MalformedLabel)
                }
                asciiLabel.length > MAX_LABEL_LENGTH -> {
                    return DomainNameNormalizationResult.Invalid(DomainNameInvalidReason.LabelTooLong)
                }
                !asciiLabel.all { it.isLetterOrDigit() || it == '-' } -> {
                    return DomainNameNormalizationResult.Invalid(DomainNameInvalidReason.MalformedLabel)
                }
                asciiLabel.startsWith("-") || asciiLabel.endsWith("-") -> {
                    return DomainNameNormalizationResult.Invalid(DomainNameInvalidReason.MalformedLabel)
                }
            }
            asciiLabel
        }

        if (asciiLabels.size < 2) {
            return DomainNameNormalizationResult.Invalid(DomainNameInvalidReason.LocalhostOrSingleLabel)
        }

        val asciiHost = asciiLabels.joinToString(".")
        if (asciiHost.length > MAX_HOST_LENGTH) {
            return DomainNameNormalizationResult.Invalid(DomainNameInvalidReason.HostTooLong)
        }

        val canonicalHost = asciiHost.removePrefix("www.")
        return DomainNameNormalizationResult.Valid(DomainName(canonicalHost))
    }

    fun matches(blockedDomain: DomainName, candidateHostOrUrl: String): Boolean {
        val candidate = normalize(candidateHostOrUrl) as? DomainNameNormalizationResult.Valid ?: return false
        val candidateValue = candidate.domain.value
        val blockedValue = blockedDomain.value

        return candidateValue == blockedValue || candidateValue.endsWith(".$blockedValue")
    }

    private fun String.toRawHost(): String {
        val withoutScheme = substringAfterSchemeOrNetworkPrefix()
        val authority = withoutScheme.substringBeforeAny('/', '?', '#')
        val withoutUserInfo = authority.substringAfterLast('@')

        if (withoutUserInfo.startsWith("[")) {
            return withoutUserInfo
        }

        val colonIndex = withoutUserInfo.lastIndexOf(':')
        if (colonIndex == -1) {
            return withoutUserInfo
        }

        val port = withoutUserInfo.substring(colonIndex + 1)
        return if (port.isNotEmpty() && port.all { it.isDigit() }) {
            withoutUserInfo.substring(0, colonIndex)
        } else {
            withoutUserInfo
        }
    }

    private fun String.substringAfterSchemeOrNetworkPrefix(): String {
        if (startsWith("//")) {
            return drop(2)
        }

        val schemeSeparator = indexOf("://")
        return if (schemeSeparator >= 0) {
            drop(schemeSeparator + 3)
        } else {
            this
        }
    }

    private fun String.substringBeforeAny(vararg delimiters: Char): String {
        val index = indexOfFirst { char -> char in delimiters }
        return if (index == -1) this else substring(0, index)
    }

    private fun String.isIpv4Literal(): Boolean {
        val parts = split(".")
        return parts.size == 4 &&
            parts.all { part ->
                part.isNotEmpty() &&
                    part.all { it.isDigit() } &&
                    part.toIntOrNull()?.let { it in 0..255 } == true
            }
    }
}
