package com.uiery.keep.domain.websiteblocking

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class DnsMessageCodecTest {
    @Test
    fun parsesAQuery() {
        val packet = dnsQuery(
            transactionId = 0x1234,
            flags = 0x0120,
            name = "Example.COM",
            qtype = 1,
            qclass = 1,
        )

        val result = DnsMessageCodec.parseQuery(packet)

        val parsed = result.requireParsed()
        assertEquals(0x1234, parsed.transactionId)
        assertEquals(0x0120, parsed.flags)
        assertEquals("example.com", parsed.queryName)
        assertEquals(1, parsed.qtype)
        assertEquals(1, parsed.qclass)
    }

    @Test
    fun appliesSameDomainDecisionToCommonAndEmergingRecordTypes() {
        val blocked = setOf(DomainName("example.com"))

        listOf(
            1 to "A",
            28 to "AAAA",
            65 to "HTTPS",
            64 to "SVCB",
            16 to "TXT",
        ).forEach { (qtype, label) ->
            val parsed = DnsMessageCodec.parseQuery(dnsQuery(name = "example.com", qtype = qtype)).requireParsed()

            assertEquals(label, DnsBlockDecision.Block, DnsMessageCodec.decide(parsed, blocked))
        }
    }

    @Test
    fun blocksExactDomainAndSubdomainOnly() {
        val blocked = setOf(DomainName("example.com"))

        assertEquals(
            DnsBlockDecision.Block,
            DnsMessageCodec.decide(DnsMessageCodec.parseQuery(dnsQuery(name = "example.com")).requireParsed(), blocked),
        )
        assertEquals(
            DnsBlockDecision.Block,
            DnsMessageCodec.decide(DnsMessageCodec.parseQuery(dnsQuery(name = "login.example.com")).requireParsed(), blocked),
        )
        assertEquals(
            DnsBlockDecision.Allow,
            DnsMessageCodec.decide(DnsMessageCodec.parseQuery(dnsQuery(name = "badexample.com")).requireParsed(), blocked),
        )
        assertEquals(
            DnsBlockDecision.Allow,
            DnsMessageCodec.decide(DnsMessageCodec.parseQuery(dnsQuery(name = "example.com.evil.test")).requireParsed(), blocked),
        )
    }

    @Test
    fun buildsValidNxDomainResponseWithoutMutatingQuery() {
        val query = dnsQuery(
            transactionId = 0xBEEF,
            flags = 0x0110,
            name = "Mixed.Example.COM",
            qtype = 65,
            qclass = 1,
        )
        val original = query.copyOf()
        val parsed = DnsMessageCodec.parseQuery(query).requireParsed()

        val response = DnsMessageCodec.buildNxDomainResponse(parsed)

        assertArrayEquals(original, query)
        assertEquals(0xBE, response[0].unsigned())
        assertEquals(0xEF, response[1].unsigned())
        assertEquals(0x81, response[2].unsigned())
        assertEquals(0x93, response[3].unsigned())
        assertEquals(1, response.readUnsignedShort(4))
        assertEquals(0, response.readUnsignedShort(6))
        assertEquals(0, response.readUnsignedShort(8))
        assertEquals(0, response.readUnsignedShort(10))
        assertEquals(
            dnsQuery(
                transactionId = 0xBEEF,
                flags = 0x8193,
                name = "mixed.example.com",
                qtype = 65,
                qclass = 1,
            ).toList(),
            response.toList(),
        )
    }

    @Test
    fun parsesCompressedQuestionName() {
        val packet = byteArrayOf(
            0x12, 0x34,
            0x01, 0x00,
            0x00, 0x01,
            0x00, 0x00,
            0x00, 0x00,
            0x00, 0x00,
            0xC0.toByte(), 0x12,
            0x00, 0x1C,
            0x00, 0x01,
            0x03, 'w'.code.toByte(), 'w'.code.toByte(), 'w'.code.toByte(),
            0x07, 'e'.code.toByte(), 'x'.code.toByte(), 'a'.code.toByte(), 'm'.code.toByte(),
            'p'.code.toByte(), 'l'.code.toByte(), 'e'.code.toByte(),
            0x03, 'c'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte(),
            0x00,
        )

        val parsed = DnsMessageCodec.parseQuery(packet).requireParsed()

        assertEquals("www.example.com", parsed.queryName)
        assertEquals(28, parsed.qtype)
        assertEquals(1, parsed.qclass)
    }

    @Test
    fun rejectsMalformedQueriesWithTypedReasons() {
        assertFailure(DnsQueryFailureReason.PacketTooShort, ByteArray(11))
        assertFailure(DnsQueryFailureReason.ResponsePacket, dnsQuery(flags = 0x8000))
        assertFailure(DnsQueryFailureReason.QuestionCountNotOne, dnsQuery(qdcount = 0))
        assertFailure(DnsQueryFailureReason.QuestionCountNotOne, dnsQuery(qdcount = 2))
        assertFailure(DnsQueryFailureReason.TruncatedQuestion, dnsQuery().copyOf(14))
        assertFailure(DnsQueryFailureReason.ReservedLabelEncoding, dnsQuery().also { it[12] = 0x40 })
        assertFailure(DnsQueryFailureReason.PointerOutOfBounds, pointerQuery(0xC0, 0x7F))
        assertFailure(DnsQueryFailureReason.PointerLoop, pointerQuery(0xC0, 0x0C))
        assertFailure(
            DnsQueryFailureReason.NameTooLong,
            dnsQuery(name = List(5) { "a".repeat(50) }.joinToString(".")),
        )
        assertFailure(
            DnsQueryFailureReason.InvalidEmptyLabel,
            dnsQuery(rawQuestionName = byteArrayOf(0)),
        )
        assertFailure(
            DnsQueryFailureReason.NonAsciiLabel,
            dnsQuery(rawQuestionName = byteArrayOf(1, 0xFF.toByte(), 0)),
        )
    }

    private fun assertFailure(expected: DnsQueryFailureReason, packet: ByteArray) {
        val failure = DnsMessageCodec.parseQuery(packet) as? DnsQueryParseResult.Failure
        requireNotNull(failure) { "Expected failure $expected" }
        assertEquals(expected, failure.reason)
    }

    private fun DnsQueryParseResult.requireParsed(): DnsQuery {
        val parsed = this as? DnsQueryParseResult.Parsed
        requireNotNull(parsed) { "Expected parsed query but was $this" }
        return parsed.query
    }

    private fun dnsQuery(
        transactionId: Int = 0xCAFE,
        flags: Int = 0x0100,
        qdcount: Int = 1,
        name: String = "example.com",
        rawQuestionName: ByteArray = encodeName(name),
        qtype: Int = 1,
        qclass: Int = 1,
    ): ByteArray =
        byteArrayOf(
            (transactionId ushr 8).toByte(), transactionId.toByte(),
            (flags ushr 8).toByte(), flags.toByte(),
            (qdcount ushr 8).toByte(), qdcount.toByte(),
            0x00, 0x00,
            0x00, 0x00,
            0x00, 0x00,
        ) + rawQuestionName + byteArrayOf(
            (qtype ushr 8).toByte(), qtype.toByte(),
            (qclass ushr 8).toByte(), qclass.toByte(),
        )

    private fun pointerQuery(firstPointerByte: Int, secondPointerByte: Int): ByteArray =
        dnsQuery(rawQuestionName = byteArrayOf(firstPointerByte.toByte(), secondPointerByte.toByte()))

    private fun encodeName(name: String): ByteArray =
        name.split(".").flatMap { label ->
            listOf(label.length.toByte()) + label.map { it.code.toByte() }
        }.plus(0.toByte()).toByteArray()

    private fun ByteArray.readUnsignedShort(offset: Int): Int =
        (this[offset].unsigned() shl 8) or this[offset + 1].unsigned()

    private fun Byte.unsigned(): Int = toInt() and 0xFF
}
