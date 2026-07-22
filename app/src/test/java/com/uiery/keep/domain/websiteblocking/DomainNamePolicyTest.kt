package com.uiery.keep.domain.websiteblocking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainNamePolicyTest {
    @Test
    fun pastedHttpsUrlNormalizesToCanonicalDomain() {
        assertValidDomain("example.com", DomainNamePolicy.normalize(" HTTPS://WWW.Example.COM/path?x=1 "))
    }

    @Test
    fun trailingDotAndUserInfoPortAreRemovedBeforeStorage() {
        assertValidDomain("example.com", DomainNamePolicy.normalize("example.com."))
        assertValidDomain("example.com", DomainNamePolicy.normalize("https://user:pass@www.example.com:8443/path"))
    }

    @Test
    fun unicodeLabelsNormalizeToAsciiPunycode() {
        assertValidDomain("xn--e1afmkfd.xn--p1ai", DomainNamePolicy.normalize("https://www.пример.рф/path"))
        assertValidDomain("xn--e1afmkfd.xn--p1ai", DomainNamePolicy.normalize("xn--e1afmkfd.xn--p1ai"))
    }

    @Test
    fun invalidUserInputsReturnTypedReasons() {
        assertInvalidReason(DomainNameInvalidReason.Empty, DomainNamePolicy.normalize(" "))
        assertInvalidReason(DomainNameInvalidReason.Wildcard, DomainNamePolicy.normalize("*.example.com"))
        assertInvalidReason(DomainNameInvalidReason.LocalhostOrSingleLabel, DomainNamePolicy.normalize("localhost"))
        assertInvalidReason(DomainNameInvalidReason.IpLiteral, DomainNamePolicy.normalize("192.168.0.1"))
        assertInvalidReason(DomainNameInvalidReason.IpLiteral, DomainNamePolicy.normalize("[2001:db8::1]"))
        assertInvalidReason(DomainNameInvalidReason.IpLiteral, DomainNamePolicy.normalize("2001:db8::1"))
        assertInvalidReason(DomainNameInvalidReason.MalformedLabel, DomainNamePolicy.normalize("bad..example.com"))
        assertInvalidReason(DomainNameInvalidReason.MalformedLabel, DomainNamePolicy.normalize("-bad.example.com"))
        assertInvalidReason(DomainNameInvalidReason.LabelTooLong, DomainNamePolicy.normalize("${"a".repeat(64)}.example.com"))
        assertInvalidReason(DomainNameInvalidReason.HostTooLong, DomainNamePolicy.normalize(longHost()))
    }

    @Test
    fun matchesExactDomainAndTrueSubdomainsOnly() {
        val blockedDomain = DomainName("example.com")

        assertTrue(DomainNamePolicy.matches(blockedDomain, "example.com"))
        assertTrue(DomainNamePolicy.matches(blockedDomain, "https://login.example.com/path"))
        assertTrue(DomainNamePolicy.matches(blockedDomain, "deep.login.example.com"))
        assertFalse(DomainNamePolicy.matches(blockedDomain, "badexample.com"))
        assertFalse(DomainNamePolicy.matches(blockedDomain, "example.com.evil.test"))
    }

    private fun assertValidDomain(expected: String, result: DomainNameNormalizationResult) {
        val valid = result as? DomainNameNormalizationResult.Valid
        requireNotNull(valid) { "Expected valid domain $expected but was $result" }
        assertEquals(expected, valid.domain.value)
    }

    private fun assertInvalidReason(
        expected: DomainNameInvalidReason,
        result: DomainNameNormalizationResult,
    ) {
        val invalid = result as? DomainNameNormalizationResult.Invalid
        requireNotNull(invalid) { "Expected invalid reason $expected but was $result" }
        assertEquals(expected, invalid.reason)
    }

    private fun longHost(): String =
        List(5) { "a".repeat(51) }.joinToString(".")
}
